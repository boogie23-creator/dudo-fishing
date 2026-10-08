package com.dudo.fishing.data

import org.json.JSONObject
import java.time.LocalDateTime
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Open-Meteo 해양 예보 (키 불필요).
 * - 해류(ocean_current_*): Copernicus SMOC = 조류+해류+파랑류 합성. 윈디 해류 레이어와 같은 계열. 방향 = 흘러가는 쪽.
 * - 너울 방향: 너울이 0.3m 이상이면 너울 방향, 아니면 파랑 방향 (오는 쪽).
 * - 해수면(sea_level_height_msl): 만조·간조 높이 차(실제 조차) 계산용.
 */
object MarineApi {
    data class Current(val speedKmh: Double, val dirDeg: Int)

    data class Result(
        val currents: Map<LocalDateTime, Current>,
        val waveDir: Map<LocalDateTime, Int>,
        val seaLevel: List<Pair<LocalDateTime, Double>>,
    )

    // 두도 남쪽 바다 격자
    private const val LAT = 35.035
    private const val LNG = 129.03

    @Volatile private var cache: Pair<LocalDateTime, Result>? = null

    fun fetch(now: LocalDateTime): Result {
        cache?.let { (t, r) -> if (t.isAfter(now.minusMinutes(30))) return r }
        val h = JSONObject(Http.get(
            "https://marine-api.open-meteo.com/v1/marine?latitude=$LAT&longitude=$LNG" +
                    "&hourly=wave_direction,swell_wave_height,swell_wave_direction,sea_level_height_msl," +
                    "ocean_current_velocity,ocean_current_direction&cell_selection=sea" +
                    "&past_days=2&forecast_days=4&timezone=Asia%2FSeoul"
        )).getJSONObject("hourly")
        val times = h.getJSONArray("time")
        fun num(key: String, i: Int): Double? = h.optJSONArray(key)?.let { a ->
            if (a.isNull(i)) null else a.optDouble(i).takeIf { !it.isNaN() }
        }
        val cur = HashMap<LocalDateTime, Current>()
        val wd = HashMap<LocalDateTime, Int>()
        val lv = ArrayList<Pair<LocalDateTime, Double>>()
        for (i in 0 until times.length()) {
            val t = LocalDateTime.parse(times.getString(i))
            val v = num("ocean_current_velocity", i)
            val d = num("ocean_current_direction", i)
            if (v != null && d != null) cur[t] = Current(v, d.toInt())
            val sw = num("swell_wave_height", i)
            val swd = num("swell_wave_direction", i)
            val w = if (sw != null && sw >= 0.3 && swd != null) swd else num("wave_direction", i)
            if (w != null) wd[t] = w.toInt()
            num("sea_level_height_msl", i)?.let { lv += t to it }
        }
        return Result(cur, wd, lv).also { cache = now to it }
    }

    /** 해수면 시계열 → 만조·간조 (포물선 보정, 높이는 cm) */
    fun tidesFromLevel(lv: List<Pair<LocalDateTime, Double>>): List<TideEvent> {
        val out = ArrayList<TideEvent>()
        for (i in 1 until lv.size - 1) {
            val a = lv[i - 1].second; val b = lv[i].second; val c = lv[i + 1].second
            val hi = b >= a && b > c; val lo = b <= a && b < c
            if (!hi && !lo) continue
            val den = a - 2 * b + c
            val off = if (den != 0.0) 0.5 * (a - c) / den else 0.0
            out += TideEvent(lv[i].first.plusMinutes((off * 60).toLong()), hi, (b * 100).toInt())
        }
        return out
    }

    /** 들물·날물 중간(진행 20~80%) 시간의 해류를 벡터 평균 → 그 기간 실제 들물/날물 흐름 방향 */
    data class Flow(val floodDeg: Int, val ebbDeg: Int, val vRef: Double)

    fun flowDirections(currents: Map<LocalDateTime, Current>, tides: List<TideEvent>): Flow? {
        if (currents.isEmpty() || tides.size < 5) return null
        val acc = mapOf(true to DoubleArray(3), false to DoubleArray(3))
        for ((t, c) in currents) {
            val ts = com.dudo.fishing.scoring.Factors.tideState(t, tides) ?: continue
            if (ts.progress < 0.2 || ts.progress > 0.8) continue
            val a = acc.getValue(ts.incoming)
            val r = Math.toRadians(c.dirDeg.toDouble())
            a[0] += c.speedKmh * sin(r); a[1] += c.speedKmh * cos(r); a[2] += 1.0
        }
        fun dir(a: DoubleArray) = if (a[2] == 0.0 || hypot(a[0], a[1]) == 0.0) null
            else ((Math.toDegrees(atan2(a[0], a[1])) + 360) % 360).toInt()
        val f = dir(acc.getValue(true)) ?: return null
        val e = dir(acc.getValue(false)) ?: return null
        val vs = currents.values.map { it.speedKmh }.sorted()
        return Flow(f, e, maxOf(0.2, vs[(vs.size * 0.8).toInt().coerceAtMost(vs.size - 1)]))
    }
}
