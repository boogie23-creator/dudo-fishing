package com.dudo.fishing.scoring

import com.dudo.fishing.data.Astro
import com.dudo.fishing.data.CatchRecord
import com.dudo.fishing.data.DayConditions
import com.dudo.fishing.data.FishingPoint
import com.dudo.fishing.data.TideEvent
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** 점수 계산에 필요한 부가 정보 (조과 기록, 포인트 목록) */
data class ScoreContext(
    val history: List<CatchRecord> = emptyList(),
    val pointsById: Map<String, FishingPoint> = emptyMap(),
)

/** 특정 시각의 물 흐름 상태 */
data class TideState(
    val incoming: Boolean,     // 들물(밀물)이면 true
    val progress: Double,      // 0 = 직전 만조/간조, 1 = 다음 만조/간조
    val nearSlack: Boolean,    // 정조(물돌이) 40분 이내
    val minutesToNext: Long,
    val prevIsHigh: Boolean,
) {
    val text get() = (if (incoming) "들물" else "날물") + " %d%%".format((progress * 100).roundToInt())
}

object Factors {
    private val ZONE = ZoneId.of("Asia/Seoul")

    /**
     * 부산 연안 조류 방향(흘러가는 쪽, 도).
     * 대한해협 연안은 들물 때 북동쪽, 날물 때 남서쪽으로 흐르는 것이 일반적이라 이를 기본값으로 둔다.
     * 두도 주변 실제 흐름이 다르면 이 두 값만 고치면 된다.
     */
    const val FLOOD_FLOW_DEG = 60
    const val EBB_FLOW_DEG = 240

    fun tideState(at: LocalDateTime, tides: List<TideEvent>): TideState? {
        val prev = tides.lastOrNull { !it.time.isAfter(at) } ?: return null
        val next = tides.firstOrNull { it.time.isAfter(at) } ?: return null
        val total = Duration.between(prev.time, next.time).toMinutes().coerceAtLeast(1)
        val elapsed = Duration.between(prev.time, at).toMinutes()
        return TideState(
            incoming = !prev.isHigh,
            progress = elapsed.toDouble() / total,
            nearSlack = elapsed < 40 || total - elapsed < 40,
            minutesToNext = total - elapsed,
            prevIsHigh = prev.isHigh,
        )
    }

    /** 조류 세기 0~1: 들물·날물 중간에 가장 빠르고, 사리일수록 세다 */
    fun currentStrength(t: TideState, rangeFactor: Double): Double =
        sin(PI * t.progress).coerceIn(0.0, 1.0) * (0.4 + 0.6 * rangeFactor)

    /**
     * 찌낚시 관점의 조류·지형 평가.
     * - 조류가 갯바위 앞에서 바다 쪽으로 뻗어 나가면 밑밥과 채비를 흘리기 좋다.
     * - 옆으로 흐르면 무난하고, 곶부리에서는 조경지대가 생겨 더 좋다.
     * - 갯바위 쪽으로 받히면 채비가 밀려와 불리하다(홈통은 완만해서 덜 불리).
     * - 센 물때엔 홈통의 반탄류·와류에, 약한 물때엔 물이 가는 곶부리에 고기가 모인다.
     */
    fun currentReasons(p: FishingPoint, t: TideState?, rangeFactor: Double): List<Reason> {
        if (t == null) return emptyList()
        val strength = currentStrength(t, rangeFactor)
        if (strength < 0.15) return emptyList()
        val flowDeg = if (t.incoming) FLOOD_FLOW_DEG else EBB_FLOW_DEG
        val a = angleDiff(flowDeg, p.facingDeg)
        val out = mutableListOf<Reason>()
        val tideName = if (t.incoming) "들물" else "날물"
        when {
            a <= 60 -> out += Reason("$tideName 조류가 앞으로 뻗어나감 – 밑밥·찌 흘리기 좋음", 7)
            a < 120 -> {
                out += Reason("$tideName 조류가 옆으로 흐름 – 찌 흘리기 무난", 3)
                if (p.terrain == "곶부리") out += Reason("곶부리 앞 조경지대 형성", 3)
            }
            p.terrain == "홈통" -> out += Reason("$tideName 조류가 받히지만 홈통이라 완만", 0)
            else -> out += Reason("$tideName 조류가 갯바위로 받힘 – 채비가 밀려옴", -6)
        }
        when {
            strength > 0.6 -> when (p.terrain) {
                "곶부리" -> out += Reason("센 조류 – 곶부리는 물살이 너무 빠름", -4)
                "홈통" -> out += Reason("센 조류 – 홈통 반탄류에 고기가 모임", 5)
                "여밭" -> out += Reason("센 조류 – 여밭 뒤 와류 형성", 2)
            }
            strength < 0.3 -> when (p.terrain) {
                "곶부리" -> out += Reason("약한 조류 – 물이 가는 곶부리가 유리", 5)
                "홈통" -> out += Reason("약한 조류 – 홈통은 물이 고임", -3)
            }
        }
        return out
    }

    /** 어종별 찌낚시 수심·지형 적합도 (조행기: 감성돔은 4~6m 여밭에서 조과가 좋았음) */
    fun depthTerrainReasons(p: FishingPoint, s: Species): List<Reason> = buildList {
        when (s) {
            Species.GAMSEONG -> when {
                p.depthMin <= 6 && (p.terrain == "여밭" || p.terrain == "평면") ->
                    add(Reason("감성돔 찌낚시 적정 (얕은 여밭·수심 %s)".format(p.depth), 6))
                p.depthMin >= 8 -> add(Reason("감성돔에는 다소 깊은 수심 (%s)".format(p.depth), -3))
            }
            Species.BENGAE -> if (p.depthMax >= 8) add(Reason("벵에돔 – 수심 있고 조류 받는 자리 (%s)".format(p.depth), 4))
            Species.BOLLAK -> if (p.terrain == "여밭" || p.terrain == "홈통") add(Reason("볼락 – 여밭·홈통 은신처", 3))
            Species.MUNUI -> if (p.terrain == "곶부리" || p.terrain == "직벽") add(Reason("무늬오징어 – 돌출부·직벽 에깅 유리", 3))
        }
        Unit
    }

    /**
     * 과거 조과 기록과 지금 조건의 유사도로 가점.
     * 물때·계절이 비슷해야 기본 유사도가 생기고, 바람·파고·수온·시간대가 비슷할수록 커진다.
     * 같은 포인트 기록은 가중치 1, 같은 방향(서편 등)이면 0.6, 근처 다른 포인트는 0.1~0.4.
     */
    fun historyReasons(
        p: FishingPoint, s: Species, c: DayConditions, slot: TimeSlot,
        wind: Double, windDir: Int, wave: Double?, ctx: ScoreContext,
    ): List<Reason> {
        if (ctx.history.isEmpty()) return emptyList()
        val nowMul = mulIndex(c.moonAge)
        var total = 0.0
        var count = 0
        var best: Pair<CatchRecord, Double>? = null
        var pointRecords = 0

        for (rec in ctx.history) {
            val n = rec.catches[s.label] ?: continue
            if (n <= 0) continue
            val pw = pointWeight(p, rec, ctx)
            if (pw <= 0.05) continue
            if (rec.pointId == p.id) pointRecords++

            val recAge = Astro.moonAge(rec.date.atTime(12, 0).atZone(ZONE))
            val mul = when (cyclicDiff(nowMul, mulIndex(recAge), 15)) { 0 -> 1.0; 1 -> 0.85; 2 -> 0.6; 3 -> 0.35; else -> 0.0 }
            val season = when (cyclicDiff(c.date.monthValue, rec.date.monthValue, 12)) { 0 -> 1.0; 1 -> 0.7; 2 -> 0.3; else -> 0.0 }
            if (mul == 0.0 || season == 0.0) continue

            val parts = mutableListOf<Double>()
            val overlap = min(slot.endHour, rec.endHour) - maxOf(slot.startHour, rec.startHour)
            parts += if (overlap > 0) 1.0 else 0.5
            rec.windSpeed?.let { w ->
                var v = when { abs(w - wind) <= 2 -> 1.0; abs(w - wind) <= 4 -> 0.6; else -> 0.2 }
                if (wind > 4 && rec.windDir != null && angleDiff(rec.windDir, windDir) > 90) v *= 0.7
                parts += v
            }
            if (rec.wave != null && wave != null) {
                val d = abs(rec.wave - wave)
                parts += when { d <= 0.3 -> 1.0; d <= 0.6 -> 0.6; else -> 0.2 }
            }
            rec.waterTemp?.let { t ->
                val d = abs(t - c.waterTemp)
                parts += when { d <= 1.5 -> 1.0; d <= 3.0 -> 0.6; else -> 0.2 }
            }
            val sim = mul * season * parts.average()
            val mag = ln(1.0 + n) / ln(16.0)
            val contrib = sim * pw * mag
            if (contrib < 0.05) continue
            total += contrib
            count++
            if (best == null || contrib > best.second) best = rec to contrib
        }

        val out = mutableListOf<Reason>()
        if (best != null) {
            val b = best.first
            val where = b.pointId?.let { ctx.pointsById[it]?.name?.removePrefix("두도 ") } ?: b.sideFacingDeg?.let { ScoreEngine.compass(it) + "편" } ?: "두도"
            out += Reason(
                "비슷한 물때·조건 조과 ${count}건 (예: ${b.date} $where ${s.label} ${b.catches[s.label]}마리)",
                min(18, (total * 14).roundToInt()).coerceAtLeast(2)
            )
        } else if (pointRecords > 0) {
            out += Reason("이 포인트 ${s.label} 조과 기록 ${pointRecords}건", 3)
        }
        return out
    }

    private fun pointWeight(p: FishingPoint, rec: CatchRecord, ctx: ScoreContext): Double {
        rec.pointId?.let { id ->
            if (id == p.id) return 1.0
            val rp = ctx.pointsById[id] ?: return 0.1
            return if (angleDiff(rp.facingDeg, p.facingDeg) <= 40 && rp.terrain == p.terrain) 0.4 else 0.1
        }
        rec.sideFacingDeg?.let { return if (angleDiff(it, p.facingDeg) <= 60) 0.6 else 0.05 }
        return 0.25
    }

    /** 15물때 주기 안의 위치(0~14) */
    fun mulIndex(moonAge: Double): Int = (Astro.lunarDay(moonAge) - 1 + 7) % 15

    private fun cyclicDiff(a: Int, b: Int, n: Int): Int {
        val d = abs(a - b) % n
        return min(d, n - d)
    }

    fun angleDiff(a: Int, b: Int): Int {
        val d = abs(a - b) % 360
        return if (d > 180) 360 - d else d
    }
}
