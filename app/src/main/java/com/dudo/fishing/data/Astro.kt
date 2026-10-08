package com.dudo.fishing.data

import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

/** 달 위상, 물때, 일출·일몰 계산 (근사 계산) */
object Astro {
    const val SYNODIC = 29.530588853
    private val REF_NEW_MOON = ZonedDateTime.of(2000, 1, 6, 18, 14, 0, 0, ZoneOffset.UTC)

    /** 월령(일). 0 = 그믐/삭, 약 14.8 = 보름 */
    fun moonAge(at: ZonedDateTime): Double {
        // 평균 주기로 대략 위치를 잡은 뒤, 실제 합삭 시각(Meeus)으로 보정
        val approxK = floor(Duration.between(REF_NEW_MOON, at).seconds / 86400.0 / SYNODIC).toInt()
        var k = approxK + 1
        while (newMoonKst(k).isAfter(at)) k--
        return Duration.between(newMoonKst(k), at).seconds / 86400.0
    }

    /**
     * 음력 날짜 (1~30). 실제 삭(합삭) 시각을 Meeus 알고리즘(천문 알고리즘 49장)으로 계산해,
     * 한국시간 기준 합삭이 든 날을 음력 1일로 센다. 물때 앱(바다타임 등)과 같은 기준.
     */
    fun lunarDay(date: LocalDate): Int {
        val y = date.year + (date.dayOfYear - 0.5) / 365.25
        var k = floor((y - 2000) * 12.3685).toInt() + 1
        while (newMoonKst(k).toLocalDate().isAfter(date)) k--
        return (java.time.temporal.ChronoUnit.DAYS.between(newMoonKst(k).toLocalDate(), date) + 1).toInt().coerceIn(1, 30)
    }

    /** k번째 삭의 한국시간 (k=0 ≈ 2000년 1월 6일) */
    fun newMoonKst(k: Int): ZonedDateTime {
        val kd = k.toDouble()
        val t = kd / 1236.85
        var jde = 2451550.09766 + 29.530588861 * kd + 0.00015437 * t * t - 0.000000150 * t * t * t + 0.00000000073 * t * t * t * t
        val e = 1 - 0.002516 * t - 0.0000074 * t * t
        fun r(d: Double) = Math.toRadians(d)
        val m = r(2.5534 + 29.10535670 * kd - 0.0000014 * t * t)
        val mp = r(201.5643 + 385.81693528 * kd + 0.0107582 * t * t + 0.00001238 * t * t * t)
        val f = r(160.7108 + 390.67050284 * kd - 0.0016118 * t * t)
        val om = r(124.7746 - 1.56375588 * kd + 0.0020672 * t * t)
        jde += -0.40720 * sin(mp) + 0.17241 * e * sin(m) + 0.01608 * sin(2 * mp) + 0.01039 * sin(2 * f) +
                0.00739 * e * sin(mp - m) - 0.00514 * e * sin(mp + m) + 0.00208 * e * e * sin(2 * m) -
                0.00111 * sin(mp - 2 * f) - 0.00057 * sin(mp + 2 * f) + 0.00056 * e * sin(2 * mp + m) -
                0.00042 * sin(3 * mp) + 0.00042 * e * sin(m + 2 * f) + 0.00038 * e * sin(m - 2 * f) -
                0.00024 * e * sin(2 * mp - m) - 0.00017 * sin(om) - 0.00007 * sin(mp + 2 * m) +
                0.00004 * sin(2 * mp - 2 * f) + 0.00004 * sin(3 * m) + 0.00003 * sin(mp + m - 2 * f) +
                0.00003 * sin(2 * mp + 2 * f) - 0.00003 * sin(mp + m + 2 * f) + 0.00003 * sin(mp - m + 2 * f) -
                0.00002 * sin(mp - m - 2 * f) - 0.00002 * sin(3 * mp + m) + 0.00002 * sin(4 * mp)
        val unixSec = ((jde - 2440587.5) * 86400.0 - 69.0).toLong()   // TT → UT (ΔT 약 69초)
        return java.time.Instant.ofEpochSecond(unixSec).atZone(ZoneId.of("Asia/Seoul"))
    }

    /**
     * 남해안식(8물때식) 물때 이름. 음력 1일 = 8물, 7일 = 조금, 8일 = 무쉬, 9일 = 1물 … 30일 = 7물.
     */
    fun mulName(lunarDay: Int): String {
        val names = (1..13).map { "${it}물" } + listOf("조금", "무쉬")
        return names[(lunarDay - 1 + 7) % 15]
    }

    /**
     * 조차 지수 0(조금)~1(사리). 사리는 보통 삭·망 후 1~2일 늦게 오므로 1.5일 지연을 둔다.
     */
    fun tideRangeFactor(age: Double): Double {
        val a = age - 1.5
        return (1 + cos(4 * PI * a / SYNODIC)) / 2
    }

    /** NOAA 근사식으로 일출·일몰 계산 */
    fun sunTimes(date: LocalDate, lat: Double, lng: Double, zone: ZoneId): Pair<LocalTime, LocalTime> {
        val n = date.dayOfYear
        val g = 2 * PI / 365 * (n - 1)
        val eqTime = 229.18 * (0.000075 + 0.001868 * cos(g) - 0.032077 * sin(g) -
                0.014615 * cos(2 * g) - 0.040849 * sin(2 * g))
        val decl = 0.006918 - 0.399912 * cos(g) + 0.070257 * sin(g) - 0.006758 * cos(2 * g) +
                0.000907 * sin(2 * g) - 0.002697 * cos(3 * g) + 0.00148 * sin(3 * g)
        val latR = Math.toRadians(lat)
        val ha = Math.toDegrees(
            acos(cos(Math.toRadians(90.833)) / (cos(latR) * cos(decl)) - tan(latR) * tan(decl))
        )
        val offsetMin = zone.rules.getOffset(date.atStartOfDay()).totalSeconds / 60.0
        val rise = 720 - 4 * (lng + ha) - eqTime + offsetMin
        val set = 720 - 4 * (lng - ha) - eqTime + offsetMin
        return minutesToTime(rise) to minutesToTime(set)
    }

    private fun minutesToTime(m: Double): LocalTime {
        val total = ((m.toInt() % 1440) + 1440) % 1440
        return LocalTime.of(total / 60, total % 60)
    }
}
