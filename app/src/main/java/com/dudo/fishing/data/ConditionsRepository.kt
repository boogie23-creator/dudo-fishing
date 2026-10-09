package com.dudo.fishing.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import com.dudo.fishing.scoring.Factors

/** 날짜별 낚시 조건(날씨·물때·수온·해 뜨고 지는 시각)을 모은다 */
class ConditionsRepository(
    private val settings: Settings,
    private val beaches: List<BeachApi.Beach>,
) {
    private val zone = ZoneId.of("Asia/Seoul")

    // 송도 두도(부산 서구 암남동) 대표 좌표 – 기상 격자·일출 계산용
    private val areaLat = 35.0488
    private val areaLng = 129.0150

    /** 부산 연안 월별 평균 표층 수온(근사값, °C) */
    private val monthlyWaterTemp = doubleArrayOf(
        13.0, 12.0, 12.5, 14.0, 16.5, 19.0, 21.5, 24.0, 23.5, 21.0, 18.0, 15.0
    )

    private var weatherCache: Pair<LocalDateTime, KmaApi.Result>? = null

    suspend fun load(date: LocalDate): DayConditions = withContext(Dispatchers.IO) {
        val msgs = mutableListOf<String>()
        val now = LocalDateTime.now(zone)

        // 1) 날씨
        var weatherDemo = false
        val weather: List<HourWeather> = if (settings.dataGoKrKey.isBlank()) {
            weatherDemo = true
            msgs += "기상청 API 키가 없어 데모 날씨로 계산했어요. 설정에서 키를 넣어주세요."
            KmaApi.demo(date)
        } else try {
            // 같은 예보를 30분 안에 다시 부르지 않는다 (날짜 탭 전환 시 호출 절약)
            val cached = weatherCache
            val res = if (cached != null && cached.first.isAfter(now.minusMinutes(30))) cached.second
            else KmaApi.fetch(settings.dataGoKrKey, areaLat, areaLng, now)
                .also { weatherCache = now to it }
            msgs += "기상청 단기예보 연결됨 (${res.issued}, 격자 ${res.grid})"
            if (!res.hasWave) msgs += "이 지역 예보에 파고 정보가 없어 파고 점수는 빠졌어요."
            res.hours
        } catch (e: Exception) {
            weatherDemo = true
            msgs += "기상청 예보를 못 불러와 데모 날씨로 계산했어요 (${e.message})"
            KmaApi.demo(date)
        }
        val dayWeather = weather.filter {
            !it.time.toLocalDate().isBefore(date) && it.time.toLocalDate().isBefore(date.plusDays(2))
        }.ifEmpty {
            weatherDemo = true
            msgs += "이 날짜의 예보가 아직 없어요(단기예보는 약 3일까지). 데모 날씨로 계산했어요."
            KmaApi.demo(date)
        }

        // 2) 조석: 국립해양조사원 조석예보(부산, 같은 키) > 바다누리 구 API 키 > 기상청 해수욕장 조석정보 > 달 위치 추정
        var tideEstimated = false
        val official = if (settings.dataGoKrKey.isNotBlank())
            runCatching { TideApi.fetchDataGoKr(settings.dataGoKrKey, date) }
                .onFailure { msgs += "국립해양조사원 조석예보를 못 불러왔어요 (${it.message})" }
                .getOrNull()?.takeIf { it.isNotEmpty() }
                ?.also { msgs += "만조·간조 – 국립해양조사원 조석예보 (부산 관측소)" }
        else null
        val khoa = if (official == null && settings.khoaKey.isNotBlank()) runCatching { TideApi.fetchKhoa(settings.khoaKey, date) }
            .onFailure { msgs += "바다누리 조석예보를 못 불러왔어요 (${it.message})" }.getOrNull() else null
        val tides = official
            ?: khoa?.takeIf { it.isNotEmpty() }
            ?: beachTides(date, msgs)
            ?: run {
                tideEstimated = true
                msgs += "만조·간조 시각은 달 위치로 계산한 추정치예요."
                TideApi.estimate(date, zone)
            }

        // 3) 수온: 직접 입력 > 근처 해수욕장 부이 실측 > 월평균 추정
        val manual = settings.manualWaterTemp
        val buoy = if (manual == null) buoyWaterTemp(now, msgs) else null
        val waterTemp = manual ?: buoy ?: monthlyWaterTemp[date.monthValue - 1]
        if (manual == null && buoy == null) msgs += "수온은 부산 연안 월평균으로 추정했어요."

        // 4) 천문
        val age = Astro.moonAge(date.atTime(12, 0).atZone(zone))
        val (rise, set) = Astro.sunTimes(date, areaLat, areaLng, zone)

        // 5) 해양 예보(Open-Meteo, 키 불필요): 해류(조류 포함)·너울 방향·해수면
        val marine = runCatching { MarineApi.fetch(now) }
            .onFailure { msgs += "해류 예보를 못 불러왔어요 (${it.message}) – 조류 방향은 기본값(들물 남서·날물 북동)" }
            .getOrNull()
        val levelTides = marine?.let { MarineApi.tidesFromLevel(it.seaLevel) }.orEmpty()
        val flow = marine?.let { MarineApi.flowDirections(it.currents, levelTides.ifEmpty { tides }) }
        Factors.FLOOD_FLOW_DEG = flow?.floodDeg ?: Factors.DEFAULT_FLOOD_DEG
        Factors.EBB_FLOW_DEG = flow?.ebbDeg ?: Factors.DEFAULT_EBB_DEG
        flow?.let {
            msgs += if (it.fromForecast) "조류 방향(조석 성분): 들물 ${it.floodDeg}° · 날물 ${it.ebbDeg}° – 해류가 ${it.residDeg}° 쪽으로 %.1fkn 더해짐".format(it.residKmh / 1.852)
            else "해류 예보로 들물·날물 방향을 가르지 못해 기본값(들물 250°, 날물 60°)을 썼어요"
        }
        val currents = marine?.currents.orEmpty().filterKeys { it.toLocalDate() == date }.mapKeys { it.key.hour }
        val waveDir = marine?.waveDir.orEmpty().filterKeys { it.toLocalDate() == date }.mapKeys { it.key.hour }

        // 6) 실제 조차: 조석표 높이 > 해수면 예보. 05~13시에 걸친 구간들의 평균 조차
        val astroRf = Astro.tideRangeFactor(age)
        fun rangeIn(ev: List<TideEvent>): Int? {
            val rs = (5 until 13).mapNotNull { h ->
                val at = date.atTime(h, 30)
                val prev = ev.lastOrNull { !it.time.isAfter(at) } ?: return@mapNotNull null
                val next = ev.firstOrNull { it.time.isAfter(at) } ?: return@mapNotNull null
                if (prev.levelCm == null || next.levelCm == null) null else kotlin.math.abs(next.levelCm - prev.levelCm)
            }
            return if (rs.isEmpty()) null else rs.average().toInt()
        }
        // 부산 조차: 조금 ≈ 40cm, 사리 ≈ 120cm (조석표 기준)
        val tableRange = rangeIn(tides)
        val levelRange = if (tableRange == null) rangeIn(levelTides) else null
        val levelMean = levelTides.zipWithNext().filter { (a, b) -> a.isHigh != b.isHigh && a.levelCm != null && b.levelCm != null }.map { (a, b) -> kotlin.math.abs(a.levelCm!! - b.levelCm!!) }.takeIf { it.size >= 6 }?.average()
        val realRf = when {
            tableRange != null -> ((tableRange - 40) / 80.0).coerceIn(0.0, 1.0)
            levelRange != null && levelMean != null -> (0.5 + (levelRange / levelMean - 1)).coerceIn(0.0, 1.0)
            else -> null
        }
        val rangeCm = tableRange ?: levelRange
        rangeCm?.let { msgs += "05~13시 실제 조차 ${it}cm – 조류 세기 ${((realRf ?: astroRf) * 100).toInt()}% (물때 계산 ${(astroRf * 100).toInt()}%)" }

        DayConditions(
            date = date,
            weather = dayWeather,
            weatherIsDemo = weatherDemo,
            tides = tides,
            tideIsEstimated = tideEstimated,
            waterTemp = waterTemp,
            waterTempIsEstimated = manual == null && buoy == null,
            waterTempChange = if (manual == null && buoy != null && date == now.toLocalDate()) buoyChange else null,
            moonAge = age,
            mulName = Astro.mulName(Astro.lunarDay(date)),
            tideRangeFactor = realRf ?: astroRf,
            tideRangeFactorAstro = astroRf,
            tideRangeCm = rangeCm,
            currents = currents,
            flow = flow,
            waveDir = waveDir,
            sunrise = rise,
            sunset = set,
            messages = msgs,
        )
    }

    private var buoyCache: Triple<LocalDateTime, BeachApi.Beach, BeachApi.Reading>? = null
    private var buoyChange: Double? = null

    /** 같은 해수욕장의 24시간 전 수온과 비교 (감성돔은 수온이 0.1도라도 오르면 활성이 산다는 게 정설) */
    private fun fetchChange(key: String, b: BeachApi.Beach, now: LocalDateTime, r: BeachApi.Reading, msgs: MutableList<String>) {
        val prev = runCatching { BeachApi.waterTemp(key, b.num, now.minusHours(24)) }.getOrNull()
        buoyChange = prev?.let { ((r.value - it.value) * 10).let { v -> kotlin.math.round(v) / 10.0 } }
        buoyChange?.let { msgs += "수온 변화 %+.1f°C (24시간 전 대비)".format(it) }
    }

    /** 수온·조석을 조회할 해수욕장 후보: 설정에 번호가 있으면 그것, 없으면 두도에서 가까운 순 (25km 이내) */
    private fun candidateBeaches(): List<BeachApi.Beach> {
        settings.beachNum?.let { n -> beaches.firstOrNull { it.num == n }?.let { return listOf(it) } }
        return beaches.sortedBy { it.distanceKm(areaLat, areaLng) }
            .filter { it.distanceKm(areaLat, areaLng) <= 25.0 }
            .take(4)
    }

    /**
     * 가까운 해수욕장 부이 수온. 가장 가까운 곳(송도해수욕장)에 관측이 없으면 다음 후보로 넘어간다.
     * (수온은 하루 사이 크게 변하지 않으므로 내일·모레도 현재 실측값을 쓴다)
     */
    private fun buoyWaterTemp(now: LocalDateTime, msgs: MutableList<String>): Double? {
        val key = settings.dataGoKrKey
        if (key.isBlank()) return null
        buoyCache?.let { (t, b, r) -> if (t.isAfter(now.minusMinutes(30))) return r.value.also {
            note(msgs, b, r); buoyChange?.let { c -> msgs += "수온 변화 %+.1f°C (24시간 전 대비)".format(c) } } }

        for (b in candidateBeaches()) {
            val r = runCatching { BeachApi.waterTemp(key, b.num, now) }.getOrNull() ?: continue
            buoyCache = Triple(now, b, r)
            note(msgs, b, r)
            fetchChange(key, b, now, r, msgs)
            return r.value
        }
        msgs += "근처 해수욕장 부이 수온이 없어요(해수욕장 운영 기간 외에는 관측이 없을 수 있어요)."
        return null
    }

    private fun note(msgs: MutableList<String>, b: BeachApi.Beach, r: BeachApi.Reading) {
        msgs += "수온 %.1f°C – %s 부이 실측 (%s)".format(r.value, b.name, r.time)
    }

    /** 가까운 해수욕장 기준 만조·간조 (전날~다음날, 자정 근처 물 흐름 계산용) */
    private fun beachTides(date: LocalDate, msgs: MutableList<String>): List<TideEvent>? {
        val key = settings.dataGoKrKey
        if (key.isBlank()) return null
        for (b in candidateBeaches()) {
            val today = runCatching { BeachApi.tides(key, b.num, date) }.getOrDefault(emptyList())
            if (today.isEmpty()) continue
            val around = listOf(date.minusDays(1), date.plusDays(1)).flatMap { d ->
                runCatching { BeachApi.tides(key, b.num, d) }.getOrDefault(emptyList())
            }
            msgs += "만조·간조 – 기상청 조석정보 (${b.name} 기준)"
            return (today + around).sortedBy { it.time }.distinctBy { it.time }
        }
        return null
    }
}
