package com.dudo.fishing.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** 날짜별 낚시 조건(날씨·물때·수온·해 뜨고 지는 시각)을 모은다 */
class ConditionsRepository(private val settings: Settings) {
    private val zone = ZoneId.of("Asia/Seoul")

    // 송도 두도(부산 서구 암남동) 대표 좌표 – 기상 격자·일출 계산용
    private val areaLat = 35.0491
    private val areaLng = 129.0149

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

        // 2) 조석
        var tideEstimated = false
        val tides = if (settings.khoaKey.isBlank()) {
            tideEstimated = true
            TideApi.estimate(date, zone)
        } else try {
            TideApi.fetchKhoa(settings.khoaKey, date)
        } catch (e: Exception) {
            tideEstimated = true
            msgs += "조석예보를 못 불러와 추정 물때로 계산했어요 (${e.message})"
            TideApi.estimate(date, zone)
        }
        if (tideEstimated && settings.khoaKey.isBlank()) {
            msgs += "만조·간조 시각은 달 위치로 계산한 추정치예요. 정확한 값은 조석 API 키가 필요해요."
        }

        // 3) 수온: 직접 입력 > 근처 해수욕장 부이 실측 > 월평균 추정
        val manual = settings.manualWaterTemp
        val buoy = if (manual == null && !weatherDemo) buoyWaterTemp(now, msgs) else null
        val waterTemp = manual ?: buoy ?: monthlyWaterTemp[date.monthValue - 1]
        if (manual == null && buoy == null) msgs += "수온은 부산 연안 월평균으로 추정했어요."

        // 4) 천문
        val age = Astro.moonAge(date.atTime(12, 0).atZone(zone))
        val (rise, set) = Astro.sunTimes(date, areaLat, areaLng, zone)

        DayConditions(
            date = date,
            weather = dayWeather,
            weatherIsDemo = weatherDemo,
            tides = tides,
            tideIsEstimated = tideEstimated,
            waterTemp = waterTemp,
            waterTempIsEstimated = manual == null && buoy == null,
            moonAge = age,
            mulName = Astro.mulName(Astro.lunarDay(age)),
            tideRangeFactor = Astro.tideRangeFactor(age),
            sunrise = rise,
            sunset = set,
            messages = msgs,
        )
    }

    private var buoyCache: Pair<LocalDateTime, BeachApi.Reading>? = null

    /**
     * 두도와 가장 가까운 해수욕장의 부이 수온. 해수욕장 번호가 없으면 처음 한 번 자동으로 찾는다.
     * (수온은 하루 사이 크게 변하지 않으므로 내일·모레도 현재 실측값을 쓴다)
     */
    private suspend fun buoyWaterTemp(now: LocalDateTime, msgs: MutableList<String>): Double? {
        val key = settings.dataGoKrKey
        if (key.isBlank()) return null
        buoyCache?.let { (t, r) -> if (t.isAfter(now.minusMinutes(30))) return r.value.also { note(msgs, r) } }

        var num = settings.beachNum
        if (num == null) {
            val today = now.toLocalDate().toString()
            if (settings.beachProbeDay == today) return null
            settings.beachProbeDay = today
            val (nx, ny) = KmaApi.toGrid(areaLat, areaLng)
            num = runCatching { BeachApi.findNearest(key, nx, ny, now) }.getOrNull()
            if (num == null) {
                msgs += "가까운 해수욕장 관측소를 찾지 못했어요. 설정에서 해수욕장 번호를 직접 넣을 수 있어요."
                return null
            }
            settings.beachNum = num
        }
        val beach: Int = num
        val r = runCatching { BeachApi.waterTemp(key, beach, now) }.getOrNull()
        if (r == null) {
            msgs += "해수욕장 #$beach 부이 수온을 받지 못했어요(해수욕장 운영 기간 외에는 관측이 없을 수 있어요)."
            return null
        }
        buoyCache = now to r
        note(msgs, r)
        return r.value
    }

    private fun note(msgs: MutableList<String>, r: BeachApi.Reading) {
        msgs += "수온 %.1f°C – 인근 해수욕장(#%d) 부이 실측 (%s)".format(r.value, settings.beachNum ?: 0, r.time)
    }
}
