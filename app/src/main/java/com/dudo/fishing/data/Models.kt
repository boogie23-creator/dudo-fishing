package com.dudo.fishing.data

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

data class FishingPoint(
    val id: String,
    val name: String,
    val area: String,
    val lat: Double,
    val lng: Double,
    /** 갯바위가 바다를 바라보는 방향 (0=북, 90=동, 180=남, 270=서) */
    val facingDeg: Int,
    val depth: String,
    val depthMin: Double,
    val depthMax: Double,
    val bottom: String,
    val species: List<String>,
    val note: String,
    val coordVerified: Boolean,
)

/** 1시간 단위 기상 예보 */
data class HourWeather(
    val time: LocalDateTime,
    val windSpeed: Double,   // m/s
    val windDir: Int,        // 바람이 불어오는 방향(도)
    val wave: Double?,       // 파고 m (해상 격자에서만 제공)
    val rainProb: Int,       // 강수확률 %
    val precipType: Int,     // 0 없음, 1 비, 2 비/눈, 3 눈, 4 소나기
    val temp: Double?,
)

data class TideEvent(
    val time: LocalDateTime,
    val isHigh: Boolean,
    val levelCm: Int?,
)

data class DayConditions(
    val date: LocalDate,
    val weather: List<HourWeather>,
    val weatherIsDemo: Boolean,
    val tides: List<TideEvent>,
    val tideIsEstimated: Boolean,
    val waterTemp: Double,
    val waterTempIsEstimated: Boolean,
    val moonAge: Double,
    val mulName: String,
    /** 0.0 = 조금(조차 최소) ~ 1.0 = 사리(조차 최대) */
    val tideRangeFactor: Double,
    val sunrise: LocalTime,
    val sunset: LocalTime,
    val messages: List<String>,
)
