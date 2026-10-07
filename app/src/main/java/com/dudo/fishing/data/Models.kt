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
    /** 지형: 곶부리(돌출부) / 홈통(만입부) / 여밭(얕은 수중여) / 직벽 / 평면 */
    val terrain: String,
    /** 공략 지점 설명 (고기가 붙는 자리) */
    val target: String = "",
    /** 공략 지점까지 대략 거리 (m) */
    val targetDistance: Int = 10,
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

/**
 * 조과 기록 한 건 (공개 조행기 또는 사용자가 앱에서 직접 남긴 기록).
 * 그날의 조건(바람·파고·수온)은 기록 당시 값이거나, 과거 기상자료로 채운 값이다.
 */
data class CatchRecord(
    val id: String,
    val date: LocalDate,
    val startHour: Int,
    val endHour: Int,
    val pointId: String?,
    /** 포인트 번호를 모르고 방향만 알 때 (서편=270 등) */
    val sideFacingDeg: Int?,
    val catches: Map<String, Int>,
    val note: String,
    val source: String,
    val url: String?,
    val byUser: Boolean,
    val windSpeed: Double? = null,
    val windDir: Int? = null,
    val wave: Double? = null,
    val waterTemp: Double? = null,
    /** 조황이 부진했던 어종 (밴드 조황 등급 -1). 비슷한 조건이면 감점 근거로 쓴다 */
    val poorSpecies: Set<String> = emptySet(),
) {
    val hasConditions get() = windSpeed != null || wave != null || waterTemp != null
}

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
