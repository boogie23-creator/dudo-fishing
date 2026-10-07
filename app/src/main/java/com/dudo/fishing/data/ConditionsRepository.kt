package com.dudo.fishing.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** 날짜별 낚시 조건(날씨·물때·수온·해 뜨고 지는 시각)을 모은다 */
class ConditionsRepository(private val settings: Settings) {
    private val zone = ZoneId.of("Asia/Seoul")

    // 두도 일대 대표 좌표 (기상 격자·일출 계산용)
    private val areaLat = 35.033
    private val areaLng = 128.978

    /** 부산 연안 월별 평균 표층 수온(근사값, °C) */
    private val monthlyWaterTemp = doubleArrayOf(
        13.0, 12.0, 12.5, 14.0, 16.5, 19.0, 21.5, 24.0, 23.5, 21.0, 18.0, 15.0
    )

    private var weatherCache: Pair<LocalDate, List<HourWeather>>? = null

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
            val cached = weatherCache
            if (cached != null && cached.first == now.toLocalDate()) cached.second
            else KmaApi.fetch(settings.dataGoKrKey, areaLat, areaLng, now)
                .also { weatherCache = now.toLocalDate() to it }
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

        // 3) 수온
        val manual = settings.manualWaterTemp
        val waterTemp = manual ?: monthlyWaterTemp[date.monthValue - 1]

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
            waterTempIsEstimated = manual == null,
            moonAge = age,
            mulName = Astro.mulName(Astro.lunarDay(age)),
            tideRangeFactor = Astro.tideRangeFactor(age),
            sunrise = rise,
            sunset = set,
            messages = msgs,
        )
    }
}
