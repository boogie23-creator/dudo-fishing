package com.dudo.fishing.scoring

import com.dudo.fishing.data.DayConditions
import com.dudo.fishing.data.FishingPoint
import com.dudo.fishing.data.HourWeather
import com.dudo.fishing.data.TideEvent
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.math.abs
import kotlin.math.roundToInt

/** 점수에 더해지거나 빠진 이유 하나 */
data class Reason(val text: String, val delta: Int)

data class SlotScore(
    val slot: TimeSlot,
    val score: Int,
    val reasons: List<Reason>,
    val danger: String?,
    val windSpeed: Double,
    val windDir: Int,
    val wave: Double?,
    val tidePhase: String,
)

data class PointResult(
    val point: FishingPoint,
    val species: Species,
    val slots: List<SlotScore>,
) {
    val best: SlotScore get() = slots.maxBy { it.score }
}

/**
 * 규칙 기반 점수 계산 (1단계).
 * 요인별 가감을 합산한 뒤 곡선으로 0~100점 변환. 위험 조건이면 15점 이하로 제한.
 * 모든 가감 내역을 Reason 으로 남겨 "왜 이 점수인지" 앱에서 보여준다.
 */
object ScoreEngine {

    fun evaluate(point: FishingPoint, species: Species, c: DayConditions, ctx: ScoreContext = ScoreContext()): PointResult =
        PointResult(point, species, TimeSlot.entries.map { scoreSlot(point, species, c, it, ctx) })

    /** 해당 포인트 대표 어종 중 가장 점수가 높은 결과 */
    fun bestForPoint(point: FishingPoint, c: DayConditions, ctx: ScoreContext = ScoreContext()): PointResult {
        val candidates = point.species.mapNotNull { Species.byLabel(it) }.ifEmpty { Species.entries }
        return candidates.map { evaluate(point, it, c, ctx) }.maxBy { it.best.score }
    }

    /**
     * 가감 합계를 0~100 점수로 바꾼다. 요인이 많아 단순 합산하면 대부분 상한에 몰리므로
     * 부드러운 곡선(tanh)으로 눌러서 좋은 날·좋은 포인트 사이 차이가 보이게 한다.
     * 합계 +38 ≈ 50점, +60 ≈ 72점, +80 ≈ 86점, +15 ≈ 30점.
     */
    fun toScore(sum: Int): Int = (50 + 48 * kotlin.math.tanh((sum - 38) / 28.0)).roundToInt().coerceIn(0, 100)

    private fun scoreSlot(p: FishingPoint, s: Species, c: DayConditions, slot: TimeSlot, ctx: ScoreContext): SlotScore {
        val r = mutableListOf<Reason>()
        val start = c.date.atTime(slot.startHour, 0)
        val center = start.plusMinutes(((slot.endHour - slot.startHour) * 30).toLong())
        val hours = c.weather.filter {
            it.time.toLocalDate() == c.date && it.time.hour in slot.startHour until slot.endHour
        }.ifEmpty { listOfNotNull(c.weather.minByOrNull { abs(Duration.between(it.time, center).toMinutes()) }) }

        val wind = hours.map { it.windSpeed }.average().takeIf { !it.isNaN() } ?: 0.0
        val windDir = hours.maxByOrNull { it.windSpeed }?.windDir ?: 0
        val wave = hours.mapNotNull { it.wave }.maxOrNull()
        val rainy = hours.any { it.precipType != 0 }
        var danger: String? = null

        // 1. 시즌
        val season = s.season[c.date.monthValue - 1]
        r += Reason("${s.label} 시즌 적합도 ${"★".repeat(season)}${"☆".repeat(3 - season)}", (season - 1.5).times(8).roundToInt())

        // 2. 수온
        val t = c.waterTemp
        when {
            t in s.idealTemp -> r += Reason("수온 %.1f°C – 적정 범위".format(t), 12)
            t in s.okTemp -> r += Reason("수온 %.1f°C – 활동 가능 범위".format(t), 2)
            else -> r += Reason("수온 %.1f°C – 선호 범위 밖".format(t), -15)
        }

        // 2-1. 수온 변화 (24시간 전 대비) – 오르면 활성↑, 급락하면 입을 닫음
        c.waterTempChange?.let { d ->
            when {
                d >= 0.3 -> r += Reason("수온 상승 %+.1f°C – 활성 오름".format(d), if (s == Species.GAMSEONG || s == Species.BENGAE) 5 else 3)
                d <= -1.5 -> r += Reason("수온 급락 %+.1f°C – 입을 닫기 쉬움".format(d), -8)
                d <= -0.5 -> r += Reason("수온 하락 %+.1f°C".format(d), -4)
            }
        }
        // 2-2. 저수온기 감성돔: 북서풍을 등지고 볕 드는 남향 자리, 깊은 곳이 유리 (영등철 공략)
        if (s == Species.GAMSEONG && t < 13.5) {
            if (p.facingDeg in 120..240) r += Reason("저수온기 – 볕 드는 남향 자리", 4)
            if (p.depthMax >= 10) r += Reason("저수온기 – 수온 안정된 깊은 수심", 3)
        }

        // 3. 대표 어종 여부
        if (s.label !in p.species) r += Reason("이 포인트의 대표 어종 아님", -10)

        // 4. 시간대(피딩타임)
        r += timeOfDay(s, slot, c.sunrise, c.sunset)

        // 5. 조류(물 흐름)
        val (phaseText, tideReasons) = tide(s, center, c.tides)
        r += tideReasons

        // 6. 물때(조차)
        val f = c.tideRangeFactor
        when {
            f < 0.25 -> r += Reason("${c.mulName} – 물 흐름 약함(조금 무렵)", -5)
            f in 0.4..0.8 -> r += Reason("${c.mulName} – 적당한 조류", 8)
            f > 0.9 -> r += Reason("${c.mulName} – 사리 무렵, 조류 강함", if (p.depthMax >= 10) 0 else -3)
            else -> r += Reason("${c.mulName}", 2)
        }

        // 7. 바람 (포인트가 바라보는 방향 기준)
        val onshore = angleDiff(windDir, p.facingDeg) <= 60   // 바다 쪽에서 갯바위로 불어오는 바람
        val dirText = compass(windDir)
        when {
            wind >= 12 -> { danger = "강풍 %.0fm/s".format(wind); r += Reason("강풍 %.1fm/s – 출조 위험".format(wind), -30) }
            wind >= 9 -> r += Reason("$dirText 바람 %.1fm/s – 낚시 어려움".format(wind), -15)
            wind >= 6 && onshore -> {
                r += Reason("$dirText 맞바람 %.1fm/s – 채비 운용 어려움".format(wind), -12)
                if (wind >= 8) danger = "맞바람·파도 주의"
            }
            wind >= 6 -> r += Reason("$dirText 바람 %.1fm/s – 포인트가 등지는 방향".format(wind), -3)
            wind >= 2.5 -> r += Reason("$dirText 바람 %.1fm/s – 적당한 물결".format(wind), 4)
            else -> r += Reason("바람 거의 없음 %.1fm/s".format(wind), if (s.likesSomeWave) -3 else 2)
        }
        // 은파낚시 밴드 조황: 남서풍이 불면 수온이 내려가고 조황이 떨어진 날이 많았음
        if (wind >= 4 && windDir in 200..250) r += Reason("남서풍 – 두도 수온 하강·조황 저하 경향 (밴드 조황)", -3)

        // 8. 파고
        if (wave != null) {
            when {
                wave >= 2.0 -> { danger = "파고 %.1fm – 갯바위 위험".format(wave); r += Reason("파고 %.1fm – 갯바위 위험".format(wave), -30) }
                wave > s.waveLimit -> r += Reason("파고 %.1fm – ${s.label} 낚시에 높음".format(wave), -12)
                s.likesSomeWave && wave in 0.5..1.2 -> r += Reason("파고 %.1fm – 적당한 포말, 경계심 낮춤".format(wave), 6)
                wave < 0.3 && s.likesSomeWave -> r += Reason("파고 %.1fm – 너무 잔잔함".format(wave), -3)
                else -> r += Reason("파고 %.1fm".format(wave), 0)
            }
            if (onshore && wave >= 1.5 && danger == null) danger = "정면 너울 – 갯바위 진입 주의"
        }

        // 9. 비
        if (rainy) r += Reason("강수 예보", -4)

        // 10. 찌낚시 조류·지형 (포인트가 바라보는 방향 vs 들물/날물 흐름)
        r += Factors.currentReasons(p, Factors.tideState(center, c.tides), c.tideRangeFactor)

        // 11. 어종별 수심·지형 적합도
        r += Factors.depthTerrainReasons(p, s)

        // 12-0. 물돌이(만조·간조)가 이 시간대에 들어 있는지 – 두도 대물 감성돔은 물돌이 전후 입질 (밴드 조황 다수)
        if (s == Species.GAMSEONG) {
            val turn = c.tides.firstOrNull { it.time.toLocalDate() == c.date && it.time.hour in slot.startHour until slot.endHour }
            if (turn != null) r += Reason("물돌이(%s %02d:%02d) 포함 – 대물 입질 시간 (밴드 조황)".format(
                if (turn.isHigh) "만조" else "간조", turn.time.hour, turn.time.minute), 7)
        }

        // 12. 과거 조과 기록 – 비슷한 물때·계절·바람·파고·수온일 때 가점
        r += Factors.historyReasons(p, s, c, slot, wind, windDir, wave, ctx)
        // 12-1. 두도 전체 조황 (모든 포인트 공통 – 순위는 바꾸지 않음)
        r += Factors.dayReasons(s, c, slot, wind, windDir, wave, ctx)

        var score = toScore(r.sumOf { it.delta })
        if (danger != null) score = score.coerceAtMost(15)
        return SlotScore(slot, score, r, danger, wind, windDir, wave, phaseText)
    }

    private fun timeOfDay(s: Species, slot: TimeSlot, rise: LocalTime, set: LocalTime): Reason {
        val riseIn = rise.hour in (slot.startHour - 1) until slot.endHour
        val setIn = set.hour in (slot.startHour - 1) until slot.endHour
        val twilight = riseIn || setIn
        return when (s.activeAt) {
            Activity.TWILIGHT -> if (twilight) Reason("${if (riseIn) "해 뜰 무렵" else "해 질 무렵"} 피딩타임", 12)
                else if (slot == TimeSlot.MIDDAY) Reason("한낮 – 활성도 낮은 시간", -6) else Reason("일반 시간대", 0)
            Activity.NIGHT -> when (slot) {
                TimeSlot.NIGHT -> Reason("밤 – ${s.label} 활성 시간", 15)
                TimeSlot.DAWN -> Reason("새벽 – 아직 어두운 시간", 8)
                else -> if (setIn) Reason("해 질 무렵 – 입질 시작", 6) else Reason("낮 – ${s.label} 활성도 낮음", -12)
            }
            Activity.DAY -> when (slot) {
                TimeSlot.NIGHT -> Reason("밤 – ${s.label} 활성도 낮음", -12)
                TimeSlot.MORNING, TimeSlot.AFTERNOON -> Reason("낮 활성 시간대", 6)
                else -> Reason("일반 시간대", 0)
            }
        }
    }

    /** 가운데 시각 기준 들물/날물 진행 정도로 점수 계산 */
    private fun tide(s: Species, at: LocalDateTime, tides: List<TideEvent>): Pair<String, List<Reason>> {
        val prev = tides.lastOrNull { !it.time.isAfter(at) }
        val next = tides.firstOrNull { it.time.isAfter(at) }
        if (prev == null || next == null) return "정보 없음" to emptyList()

        val total = Duration.between(prev.time, next.time).toMinutes().coerceAtLeast(1)
        val elapsed = Duration.between(prev.time, at).toMinutes()
        val p = elapsed.toDouble() / total            // 0 = 직전 극치, 1 = 다음 극치
        val incoming = !prev.isHigh                   // 간조 → 만조 사이면 들물
        val nearSlack = elapsed < 40 || total - elapsed < 40
        val phase = (if (incoming) "들물" else "날물") + " %d%%".format((p * 100).roundToInt())
        val reasons = mutableListOf<Reason>()

        if (nearSlack) {
            val which = if ((p < 0.5) == prev.isHigh) "만조" else "간조"
            if (which == "만조" && s.likesIncoming) reasons += Reason("만조 전후 – 감성돔류 입질 기대", 8)
            else if (s == Species.GAMSEONG) reasons += Reason("$which 물돌이 무렵", 0)   // 두도 조황상 감성돔은 물돌이에 입질 → 감점 안 함
            else reasons += Reason("$which 정조 – 물 흐름 멈춤", -6)
        } else if (incoming) {
            reasons += when {
                s.likesIncoming && p >= 0.4 -> Reason("중들물~끝들물 – 좋은 물때", 14)
                s.likesIncoming -> Reason("초들물 – 입질 시작 구간", 6)
                p in 0.3..0.8 -> Reason("들물 중반 – 조류 활발", 8)
                else -> Reason("들물", 3)
            }
        } else {
            reasons += when {
                p in 0.2..0.6 -> Reason("초날물~중날물 – 조류 활발", if (s.likesIncoming) 5 else 9)
                else -> Reason("끝날물", -2)
            }
        }
        return phase to reasons
    }

    private fun angleDiff(a: Int, b: Int): Int {
        val d = abs(a - b) % 360
        return if (d > 180) 360 - d else d
    }

    fun compass(deg: Int): String {
        val names = listOf("북", "북동", "동", "남동", "남", "남서", "서", "북서")
        return names[(((deg % 360) + 360 + 22) % 360) / 45]
    }
}
