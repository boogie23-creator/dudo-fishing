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
        val days = Duration.between(REF_NEW_MOON, at).seconds / 86400.0
        return ((days % SYNODIC) + SYNODIC) % SYNODIC
    }

    /** 대략적인 음력 날짜 (1~30) */
    fun lunarDay(age: Double): Int = (floor(age).toInt() + 1).coerceIn(1, 30)

    /**
     * 남해안식(8물때식) 물때 이름. 음력 1일 = 8물로 계산.
     * 실제 물때표와 하루 정도 차이 날 수 있음.
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
