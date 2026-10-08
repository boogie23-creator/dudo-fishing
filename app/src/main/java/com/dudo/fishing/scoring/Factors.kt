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
    /** 포인트를 특정할 수 있는 기록인지 (번호 또는 방향이 있는 기록) */
    fun isPointRecord(r: CatchRecord) = r.pointId != null || r.sideFacingDeg != null

    /**
     * 두도 전체 조황(밴드 일일 조황 등 포인트 없는 기록)으로 본 '오늘 바다 상태'.
     * 모든 포인트에 똑같이 적용되므로 포인트 간 순위는 바꾸지 않고 전체 수준만 올리거나 내린다.
     */
    fun dayReasons(
        s: Species, c: DayConditions, slot: TimeSlot, wind: Double, windDir: Int, wave: Double?, ctx: ScoreContext,
    ): List<Reason> {
        val nowMul = mulIndex(c.date)
        var good = 0.0; var poor = 0.0; var nGood = 0; var nPoor = 0
        for (rec in ctx.history) {
            if (isPointRecord(rec)) continue
            val isPoor = s.label in rec.poorSpecies
            val n = rec.catches[s.label] ?: continue
            if (n <= 0 && !isPoor) continue
            val sim = similarity(rec, c, slot, nowMul, wind, windDir, wave)
            if (sim < 0.25) continue
            if (isPoor) { poor += sim; nPoor++ } else { good += sim * ln(1.0 + n) / ln(13.0); nGood++ }
        }
        if (nGood + nPoor == 0) return emptyList()
        val net = good - poor * 0.9
        val delta = (net * 4).roundToInt().coerceIn(-8, 10)
        val text = "두도 전체 – 비슷한 물때·날씨 날 조황 (좋음 ${nGood}일 · 부진 ${nPoor}일)"
        return listOf(Reason(text, delta))
    }

    /**
     * 이 포인트(또는 같은 방향·인접 포인트)에서 실제로 잡힌 기록만으로 포인트 간 차등을 준다.
     * 물때·계절이 비슷하고 바람·파고·수온·시간대가 비슷할수록 가점.
     */
    fun historyReasons(
        p: FishingPoint, s: Species, c: DayConditions, slot: TimeSlot,
        wind: Double, windDir: Int, wave: Double?, ctx: ScoreContext,
    ): List<Reason> {
        if (ctx.history.isEmpty()) return emptyList()
        val nowMul = mulIndex(c.date)
        var total = 0.0
        var count = 0
        var best: Pair<CatchRecord, Double>? = null
        var exact = 0
        var poorTotal = 0.0
        var poorCount = 0
        for (rec in ctx.history) {
            if (!isPointRecord(rec)) continue
            val poor = s.label in rec.poorSpecies
            val n = rec.catches[s.label] ?: continue
            if (n <= 0 && !poor) continue
            val pw = pointWeight(p, rec, ctx)
            if (pw <= 0.0) continue
            if (rec.pointId == p.id && !poor) exact++
            val sim = similarity(rec, c, slot, nowMul, wind, windDir, wave)
            if (sim <= 0.0) continue
            if (poor) {
                val neg = sim * pw
                if (neg >= 0.05) { poorTotal += neg; poorCount++ }
                continue
            }
            val contrib = sim * pw * ln(1.0 + n) / ln(16.0)
            if (contrib < 0.05) continue
            total += contrib
            count++
            if (best == null || contrib > best.second) best = rec to contrib
        }

        val out = mutableListOf<Reason>()
        if (best != null) {
            val b = best.first
            val where = when {
                b.pointId == p.id -> "이 자리"
                b.pointId != null -> ctx.pointsById[b.pointId]?.name?.removePrefix("두도 ") ?: "인접 자리"
                else -> ScoreEngine.compass(b.sideFacingDeg ?: 0) + "편"
            }
            out += Reason(
                "비슷한 조건 포인트 조과 ${count}건 (예: ${b.date} $where ${s.label} ${qtyText(b, s.label)})",
                min(18, (total * 14).roundToInt()).coerceAtLeast(2)
            )
        } else if (exact > 0) {
            out += Reason("이 포인트 ${s.label} 조과 기록 ${exact}건 (다른 계절·물때)", 2)
        }
        if (poorCount > 0) {
            out += Reason("비슷한 조건에 이 쪽 ${s.label} 부진 기록 ${poorCount}건", -min(8, (poorTotal * 12).roundToInt()).coerceAtLeast(2))
        }
        return out
    }

    /** 과거 기록 하루와 지금 조건의 유사도 0~1 (물때·계절이 멀면 0) */
    private fun similarity(
        rec: CatchRecord, c: DayConditions, slot: TimeSlot, nowMul: Int,
        wind: Double, windDir: Int, wave: Double?,
    ): Double {
        val mul = when (cyclicDiff(nowMul, mulIndex(rec.date), 15)) { 0 -> 1.0; 1 -> 0.85; 2 -> 0.6; 3 -> 0.35; else -> 0.0 }
        val season = when (cyclicDiff(c.date.monthValue, rec.date.monthValue, 12)) { 0 -> 1.0; 1 -> 0.7; 2 -> 0.3; else -> 0.0 }
        if (mul == 0.0 || season == 0.0) return 0.0

        val parts = mutableListOf<Double>()
        val overlap = min(slot.endHour, rec.endHour) - maxOf(slot.startHour, rec.startHour)
        parts += if (overlap > 0) 1.0 else 0.5
        val rw = rec.windSpeed
        if (rw != null) {
            var v = when { abs(rw - wind) <= 2 -> 1.0; abs(rw - wind) <= 4 -> 0.6; else -> 0.2 }
            val rd = rec.windDir
            if (wind > 4 && rd != null && angleDiff(rd, windDir) > 90) v *= 0.7
            parts += v
        }
        val rwave = rec.wave
        if (rwave != null && wave != null) {
            val d = abs(rwave - wave)
            parts += when { d <= 0.3 -> 1.0; d <= 0.6 -> 0.6; else -> 0.2 }
        }
        val rt = rec.waterTemp
        if (rt != null) {
            val d = abs(rt - c.waterTemp)
            parts += when { d <= 1.5 -> 1.0; d <= 3.0 -> 0.6; else -> 0.2 }
        }
        return mul * season * parts.average()
    }

    /** 밴드 조황 기록은 마릿수가 아니라 등급이므로 글로 표시 */
    fun qtyText(r: CatchRecord, label: String): String {
        val n = r.catches[label] ?: 0
        return if (r.id.startsWith("band_")) when {
            label in r.poorSpecies -> "조황 부진"
            n >= 12 -> "조황 좋음"
            n >= 6 -> "조과 있음"
            else -> "조황 저조"
        } else "${n}마리"
    }

    /**
     * 기록이 이 포인트에 얼마나 해당하는지.
     * 같은 번호 1.0 / 35m 이내 + 공략 방향 비슷 0.45 / 70m 이내 + 같은 지형 0.2 / 같은 방향(서편 등) 기록 0.5
     */
    private fun pointWeight(p: FishingPoint, rec: CatchRecord, ctx: ScoreContext): Double {
        rec.pointId?.let { id ->
            if (id == p.id) return 1.0
            val rp = ctx.pointsById[id] ?: return 0.0
            val d = distanceM(rp.lat, rp.lng, p.lat, p.lng)
            return when {
                d <= 35 && angleDiff(rp.facingDeg, p.facingDeg) <= 50 -> 0.45
                d <= 70 && rp.terrain == p.terrain -> 0.2
                else -> 0.0
            }
        }
        rec.sideFacingDeg?.let { return if (angleDiff(it, p.facingDeg) <= 50) 0.5 else 0.0 }
        return 0.0
    }

    fun distanceM(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double =
        kotlin.math.hypot((lat1 - lat2) * 111_320.0, (lng1 - lng2) * 111_320.0 * kotlin.math.cos(Math.toRadians(lat1)))

    /** 15물때 주기 안의 위치(0~14) */
    fun mulIndex(date: java.time.LocalDate): Int = (Astro.lunarDay(date) - 1 + 7) % 15

    private fun cyclicDiff(a: Int, b: Int, n: Int): Int {
        val d = abs(a - b) % n
        return min(d, n - d)
    }

    fun angleDiff(a: Int, b: Int): Int {
        val d = abs(a - b) % 360
        return if (d > 180) 360 - d else d
    }
}
