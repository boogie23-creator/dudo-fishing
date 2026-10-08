package com.dudo.fishing.scoring

import com.dudo.fishing.data.FishingPoint
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * 포인트의 '고정 성격'을 위치·방향·지형으로 계산한다 (계절·날씨와 무관한 부분).
 *
 * - 들물/날물 적합도: 들물(남서류)·날물(북동류)이 이 자리에서 어떻게 흐르는지
 *   · 조류가 공략 방향(앞)으로 뻗어 나감 → 밑밥·채비를 흘리기 좋음
 *   · 옆으로 흐르는데 섬의 '하류 쪽'(조류가 섬을 지나 빠져나가는 쪽) → 곶부리 뒤 조경지대·와류가 생겨 최상
 *   · 옆으로 흐르는데 '상류 쪽' → 물살이 직접 부딪혀 빠름
 *   · 갯바위로 받힘 → 채비가 밀려옴 (홈통은 완만)
 * - 너울 노출도: 두도 남쪽·남동쪽은 외해, 북쪽은 송도·암남 해안과 방파제로 막혀 있음
 */
data class PointProfile(
    val floodSuit: Double,   // -1 ~ 1
    val ebbSuit: Double,     // -1 ~ 1
    val exposure: Double,    // 0.3(막힘) ~ 1.0(외해 정면)
    val bearing: Int,        // 섬 중심에서 본 방위
) {
    val tideType: String get() = when {
        floodSuit - ebbSuit >= 0.35 -> "들물 포인트"
        ebbSuit - floodSuit >= 0.35 -> "날물 포인트"
        floodSuit > 0.3 && ebbSuit > 0.3 -> "양물 포인트"
        else -> "물돌이 포인트"
    }
    val exposureText: String get() = when {
        exposure >= 0.9 -> "외해 정면 (너울 그대로)"
        exposure >= 0.65 -> "반쯤 열림"
        else -> "막힌 쪽 (너울 약함)"
    }
}

object PointModel {
    // 두도 본섬 중심
    private const val C_LAT = 35.0488
    private const val C_LNG = 129.0150

    private val cache = HashMap<String, PointProfile>()

    fun profile(p: FishingPoint): PointProfile = cache.getOrPut("${p.id}:${p.lat}:${p.lng}:${p.facingDeg}:${Factors.FLOOD_FLOW_DEG}:${Factors.EBB_FLOW_DEG}") {
        val dy = (p.lat - C_LAT) * 111_320.0
        val dx = (p.lng - C_LNG) * 111_320.0 * cos(Math.toRadians(C_LAT))
        val bearing = ((Math.toDegrees(atan2(dx, dy)) + 360) % 360).toInt()
        PointProfile(
            floodSuit = suit(p, bearing, Factors.FLOOD_FLOW_DEG),
            ebbSuit = suit(p, bearing, Factors.EBB_FLOW_DEG),
            exposure = exposure(bearing, p.facingDeg),
            bearing = bearing,
        )
    }

    /** 지금 실제로 흐르는 방향(해류 예보)에 대한 이 자리의 적합도 -1~1 */
    fun suitAt(p: FishingPoint, flowDeg: Int): Double = suit(p, profile(p).bearing, flowDeg)

    private fun suit(p: FishingPoint, bearing: Int, flowDeg: Int): Double {
        val a = Factors.angleDiff(flowDeg, p.facingDeg)
        // 섬 중심→포인트 방향과 흐름 방향의 내적: + 면 하류 쪽(조류가 섬을 지나 빠져나가는 쪽)
        val lee = cos(Math.toRadians((bearing - flowDeg).toDouble()))
        var v = when {
            a <= 50 -> 1.0
            a < 130 -> if (lee > 0.3) 0.85 else if (lee > -0.3) 0.6 else 0.4
            p.terrain == "홈통" -> 0.15
            else -> -0.6
        }
        when (p.terrain) {
            "곶부리" -> v += if (lee > 0.3) 0.1 else -0.1     // 곶부리 하류 쪽에 조경지대
            "홈통" -> if (a in 50..129) v += 0.1                 // 홈통 입구로 도는 반탄류
            "직벽" -> v += 0.05
        }
        return v.coerceIn(-1.0, 1.0)
    }

    private fun exposure(bearing: Int, facing: Int): Double {
        fun sector(b: Int) = when (b) {
            in 110..250 -> 1.0     // 남~남동·남서: 외해
            in 250..300 -> 0.75    // 서: 감천 앞바다
            in 60..110 -> 0.7      // 동: 영도 쪽
            else -> 0.35           // 북: 송도·암남 해안, 방파제
        }
        return max(sector(bearing) * 0.6 + sector(facing) * 0.4, 0.3)
    }

    /** 이 자리에서 체감하는 파고 (외해 파고 × 노출도, 바람이 정면이면 조금 더) */
    fun effectiveWave(prof: PointProfile, p: FishingPoint, wave: Double?, wind: Double, windDir: Int): Double? {
        if (wave == null) return null
        var w = wave * (0.35 + 0.65 * prof.exposure)
        if (wind >= 4) w *= if (Factors.angleDiff(windDir, p.facingDeg) <= 70) 1.15 else 0.85
        return (w * 10).toInt() / 10.0
    }

    /** 수온에 따른 어종별 선호 수심 (m) – 수온이 내려갈수록 깊은 곳으로 */
    fun preferredDepth(s: Species, temp: Double): ClosedFloatingPointRange<Double> = when (s) {
        Species.GAMSEONG -> when {
            temp >= 20 -> 3.0..8.0
            temp >= 15 -> 4.0..8.0      // 가을 최성기 – 4~8m 여밭 (B조법)
            temp >= 13.5 -> 6.0..12.0
            else -> 8.0..15.0           // 영등철 – 깊은 곳
        }
        Species.BENGAE -> if (temp >= 18) 3.0..10.0 else 6.0..12.0
        Species.BOLLAK -> 2.0..8.0
        Species.MUNUI -> 3.0..10.0
    }

    /** 포인트 수심 범위와 선호 수심의 겹침 비율 0~1 */
    fun depthFit(p: FishingPoint, pref: ClosedFloatingPointRange<Double>): Double {
        val lo = maxOf(p.depthMin, pref.start)
        val hi = minOf(p.depthMax, pref.endInclusive)
        val span = (p.depthMax - p.depthMin).coerceAtLeast(1.0)
        return ((hi - lo) / span).coerceIn(0.0, 1.0)
    }

    fun unitSin(x: Double) = sin(x)
}
