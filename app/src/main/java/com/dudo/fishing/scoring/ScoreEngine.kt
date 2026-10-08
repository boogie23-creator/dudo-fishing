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
    /** 05~18시 시간별 점수 (13시 이후는 참고) */
    val slots: List<SlotScore>,
) {
    /** 순위에 쓰는 05~13시 */
    val rankSlots: List<SlotScore> get() = slots.filter { it.slot.startHour < TimeSlot.FISHING_END }

    val best: SlotScore get() = rankSlots.maxBy { it.score }

    /** 05~13시 평균 확률 – 순위 기준 */
    val dayScore: Int get() = rankSlots.map { it.score }.average().roundToInt()

    /** 05~13시 중 가장 좋은 연속 2시간 */
    val bestWindow: Pair<Int, Int> get() {
        val r = rankSlots
        if (r.size < 2) return best.slot.startHour to best.slot.endHour
        val i = (0 until r.size - 1).maxBy { r[it].score + r[it + 1].score }
        return r[i].slot.startHour to r[i + 1].slot.endHour
    }

    val profile: PointProfile get() = PointModel.profile(point)
}

/**
 * 시간별 확률 모델.
 * 낚시 시간 05~13시를 1시간씩 나눠, 그 시각의 물때(들물·날물·물돌이)·조류 세기·바람·체감 파고·빛과
 * 계절(수온·선호 수심)·포인트 고정 성격(들물/날물 포인트, 너울 노출도, 지형)·과거 조과를 합산해 0~100으로 만든다.
 * 하루 순위는 05~13시 평균(dayScore).
 */
object ScoreEngine {

    fun evaluate(point: FishingPoint, species: Species, c: DayConditions, ctx: ScoreContext = ScoreContext()): PointResult =
        PointResult(point, species, TimeSlot.DISPLAY.map { scoreSlot(point, species, c, it, ctx) })

    /** 해당 포인트 대표 어종 중 하루 평균이 가장 높은 결과 */
    fun bestForPoint(point: FishingPoint, c: DayConditions, ctx: ScoreContext = ScoreContext()): PointResult {
        val candidates = point.species.mapNotNull { Species.byLabel(it) }.ifEmpty { Species.entries }
        return candidates.map { evaluate(point, it, c, ctx) }.maxBy { it.dayScore }
    }

    /** 가감 합계 → 0~100 (로지스틱). 합계 +44 ≈ 50점, +62 ≈ 80점, +26 ≈ 20점 */
    fun toScore(sum: Int): Int = (100.0 / (1.0 + kotlin.math.exp(-(sum - 44) / 13.0))).roundToInt().coerceIn(1, 99)

    private fun scoreSlot(p: FishingPoint, s: Species, c: DayConditions, slot: TimeSlot, ctx: ScoreContext): SlotScore {
        val r = mutableListOf<Reason>()
        val prof = PointModel.profile(p)
        val at = c.date.atTime(slot.startHour, 30)
        val hw = c.weather.filter { it.time.toLocalDate() == c.date && it.time.hour == slot.startHour }
            .ifEmpty { listOfNotNull(c.weather.minByOrNull { abs(Duration.between(it.time, at).toMinutes()) }) }
        val wind = hw.map { it.windSpeed }.average().takeIf { !it.isNaN() } ?: 0.0
        val windDir = hw.firstOrNull()?.windDir ?: 0
        val seaWave = hw.mapNotNull { it.wave }.maxOrNull()
        var wave = PointModel.effectiveWave(prof, p, seaWave, wind, windDir)
        // 실제 너울이 오는 방향: 정면이면 더 크게, 등지면 작게
        val swellDir = c.waveDir[slot.startHour]
        val swellAngle = swellDir?.let { Factors.angleDiff(it, p.facingDeg) }
        if (wave != null && swellAngle != null)
            wave = ((wave * if (swellAngle <= 70) 1.2 else if (swellAngle >= 110) 0.7 else 1.0) * 10).toInt() / 10.0
        val rainy = hw.any { it.precipType != 0 }
        var danger: String? = null
        val t = c.waterTemp

        // ── 계절 ─────────────────────────────
        val season = s.season[c.date.monthValue - 1]
        r += Reason("${s.label} 시즌 ${"★".repeat(season)}${"☆".repeat(3 - season)}", ((season - 1.5) * 7).roundToInt())
        val mid = (s.idealTemp.start + s.idealTemp.endInclusive) / 2
        val half = (s.idealTemp.endInclusive - s.idealTemp.start) / 2 + 2
        val tempFit = kotlin.math.exp(-((t - mid) / half).let { it * it })
        r += Reason("수온 %.1f°C (${s.label} 적정 %.0f~%.0f°C)".format(t, s.idealTemp.start, s.idealTemp.endInclusive), (tempFit * 18 - 8).roundToInt())
        c.waterTempChange?.let { d ->
            when {
                d >= 0.3 -> r += Reason("수온 상승 %+.1f°C – 활성 오름".format(d), 5)
                d <= -1.5 -> r += Reason("수온 급락 %+.1f°C – 입을 닫기 쉬움".format(d), -8)
                d <= -0.5 -> r += Reason("수온 하락 %+.1f°C".format(d), -4)
            }
        }

        // ── 포인트 × 계절: 수온별 선호 수심, 지형 ───────────
        val pref = PointModel.preferredDepth(s, t)
        val fit = PointModel.depthFit(p, pref)
        r += Reason("수심 ${p.depth} – 지금 수온의 ${s.label} 선호 수심 %.0f~%.0fm".format(pref.start, pref.endInclusive) +
                if (fit >= 0.6) " (맞음)" else if (fit > 0.2) " (일부)" else " (벗어남)", (fit * 14 - 5).roundToInt())
        terrainFit(p, s, t, c.date.monthValue)?.let { r += it }
        if (s == Species.GAMSEONG && c.date.monthValue == 5) r += Reason("5월 감성돔 금어기(5/1~5/31) – 잡으면 바로 방생", -15)
        if (s.habitat) habitatFit(p, s, prof)?.let { r += it }
        else if (s.label !in p.species) r += Reason("이 자리 대표 어종 아님", -8)
        if (p.localBias != 0) r += Reason(p.localNote.ifBlank { "현지 경험 보정" }, p.localBias)

        // ── 물때·조류 (이 시각) ────────────────
        val ts = Factors.tideState(at, c.tides)
        val strength = ts?.let { Factors.currentStrength(it, c.tideRangeFactor) } ?: 0.3
        val phaseText = ts?.text ?: "정보 없음"
        if (ts != null) {
            val nearTurn = ts.nearSlack
            if (nearTurn) {
                val which = if ((ts.progress < 0.5) == ts.prevIsHigh) "만조" else "간조"
                r += when (s) {
                    Species.GAMSEONG -> Reason("$which 물돌이 – 두도 대물 감성돔 입질 시간 (밴드 조황)", 9)
                    Species.BENGAE -> Reason("$which 정조 – 벵에돔은 조류가 있어야", -4)
                    Species.CHAMDOM -> Reason("$which 정조 – 참돔은 조류가 멈추면 입질 끊김", -4)
                    else -> Reason("$which 물돌이", 2)
                }
            } else {
                val cur = c.currents[slot.startHour]
                val suit = cur?.let { PointModel.suitAt(p, it.dirDeg) } ?: if (ts.incoming) prof.floodSuit else prof.ebbSuit
                val tideName = if (ts.incoming) "들물" else "날물"
                val curText = cur?.let { " · ${compass(it.dirDeg)}류 %.1fkn".format(it.speedKmh / 1.852) } ?: ""
                val power = if (cur != null && c.flow != null) (cur.speedKmh / c.flow.vRef).coerceIn(0.3, 1.0)
                    else (strength / 0.5).coerceIn(0.3, 1.0)
                val pts = (suit * 14 * power).roundToInt()
                val how = when {
                    suit >= 0.9 -> "조류가 공략 지점으로 뻗어 나감"
                    suit >= 0.75 -> "섬 하류 쪽 조경지대 형성"
                    suit >= 0.5 -> "조류가 갯바위를 따라 흐름"
                    suit > 0 -> "홈통 안으로 도는 물"
                    else -> "조류가 갯바위로 받혀 채비가 밀려옴"
                }
                r += Reason("$tideName ${(ts.progress * 100).roundToInt()}%$curText – $how (${prof.tideType})", pts)
                // 감성돔은 중들물~끝들물, 벵에돔은 조류가 살아 있는 중간 시간
                if (s == Species.GAMSEONG && ts.incoming && ts.progress >= 0.4) r += Reason("중들물~끝들물", 5)
                if (s == Species.BENGAE && ts.progress in 0.25..0.75) r += Reason("조류 활발한 중간 물때", 4)
                if (s == Species.CHAMDOM && strength > 0.55 && suit >= 0.75) r += Reason("센 본류가 앞으로 뻗음 – 참돔 회유", 5)
                if (s == Species.NONGEO && ts.progress in 0.2..0.7) r += Reason("물이 살아 움직이는 시간 – 농어 사냥", 3)
                if (strength > 0.65 && p.terrain == "곶부리" && suit < 0.85) r += Reason("센 조류 – 곶부리 정면은 물살이 너무 빠름", -4)
                if (strength > 0.65 && p.terrain == "홈통") r += Reason("센 조류 – 홈통 반탄류에 고기가 모임", 4)
                if (strength < 0.3 && p.terrain == "곶부리") r += Reason("약한 조류 – 물이 가는 곶부리 유리", 4)
            }
        }
        val f = c.tideRangeFactor
        if (s == Species.CHAMDOM && f > 0.75) r += Reason("사리 – 참돔은 센 물때 선호", 3)
        val ml = c.tideRangeCm?.let { "${c.mulName}(실제 조차 ${it}cm)" } ?: c.mulName
        r += when {
            f < 0.2 -> Reason("$ml – 조류 거의 없음", -6)
            f in 0.35..0.8 -> Reason("$ml – 적당한 조류", 5)
            f > 0.9 -> Reason("$ml – 사리, 조류 강함", if (p.depthMax >= 10) 1 else -3)
            else -> Reason(ml, 1)
        }

        // ── 빛 (해 뜨는 시각 기준) ──────────────
        val sinceRise = (slot.startHour * 60 + 30) - (c.sunrise.hour * 60 + c.sunrise.minute)
        r += when (s) {
            Species.GAMSEONG -> when {
                sinceRise in -60..60 -> Reason("해 뜰 무렵 피딩타임", 10)
                sinceRise in 61..150 -> Reason("아침 – 입질 이어짐", 5)
                slot.startHour >= 11 -> Reason("한낮 – 경계심 커짐", -3)
                sinceRise < -60 -> Reason("해 뜨기 전 어두운 시간", 2)
                else -> Reason("오전", 0)
            }
            Species.BENGAE -> when {
                sinceRise < 0 -> Reason("해 뜨기 전 – 벵에 활성 낮음", -5)
                slot.startHour in 7..11 -> Reason("오전 – 벵에 활성 시간", 5)
                else -> Reason("주간", 2)
            }
            Species.BOLLAK -> if (sinceRise < 30) Reason("새벽 – 볼락 활성", 8) else Reason("낮 – 볼락 활성 낮음", -10)
            Species.MUNUI -> if (sinceRise in -60..90) Reason("아침 피딩", 8) else Reason("주간", -2)
            Species.CHAMDOM -> when {
                sinceRise in -60..60 -> Reason("해 뜰 무렵 – 참돔 피딩", 8)
                sinceRise in 61..180 -> Reason("아침", 3)
                slot.startHour >= 11 -> Reason("한낮", -2)
                else -> Reason("주간", 0)
            }
            Species.NONGEO -> when {
                sinceRise <= 60 -> Reason("새벽·해 뜰 무렵 – 농어 활성 최고", 9)
                sinceRise <= 150 -> Reason("아침", 3)
                else -> Reason("낮 – 농어 경계심↑", -5)
            }
        }

        // ── 바람 (이 자리 기준) ─────────────────
        val onshore = Factors.angleDiff(windDir, p.facingDeg) <= 60
        val dirText = compass(windDir)
        when {
            wind >= 12 -> { danger = "강풍 %.0fm/s".format(wind); r += Reason("강풍 %.1fm/s – 출조 위험".format(wind), -30) }
            wind >= 9 -> r += Reason("$dirText %.1fm/s – 낚시 어려움".format(wind), if (onshore) -18 else -10)
            wind >= 6 && onshore -> {
                r += Reason("$dirText 맞바람 %.1fm/s – 채비 운용 어려움".format(wind), -12)
                if (wind >= 8) danger = "맞바람·파도 주의"
            }
            wind >= 6 -> r += Reason("$dirText %.1fm/s – 등지는 자리".format(wind), 0)
            wind >= 2.5 -> r += Reason("$dirText %.1fm/s – 적당한 물결".format(wind), 3)
            else -> r += Reason("바람 거의 없음 – 잔잔하면 경계심↑ (밴드 조황)", if (s == Species.GAMSEONG) -4 else 0)
        }
        if (wind >= 4 && windDir in 200..250) r += Reason("남서풍 – 수온 하강·조황 저하 경향 (밴드 조황)", -3)

        // ── 체감 파고 (외해 파고 × 너울 노출도) ───────
        if (wave != null) {
            val swellText = swellDir?.let { ", ${compass(it)}쪽 너울" + when { swellAngle!! <= 70 -> " 정면"; swellAngle >= 110 -> " 등짐"; else -> "" } } ?: ""
            val tag = "체감 파고 %.1fm (외해 %.1fm$swellText, ${prof.exposureText})".format(wave, seaWave ?: wave)
            when {
                wave >= 2.0 -> { danger = "파고 %.1fm – 갯바위 위험".format(wave); r += Reason(tag + " – 위험", -30) }
                wave > s.waveLimit -> r += Reason("$tag – 높음", -12)
                s == Species.GAMSEONG && wave in 0.4..1.2 -> r += Reason("$tag – 적당한 포말, 경계심 낮춤", 7)
                s == Species.BENGAE && wave in 0.3..1.0 -> r += Reason("$tag – 적당한 물결", 4)
                s == Species.NONGEO && wave >= 0.8 -> r += Reason("$tag – 파도·포말, 농어 최적", 8)
                s == Species.NONGEO && wave < 0.5 -> r += Reason("$tag – 잔잔하면 농어 경계", -6)
                s == Species.CHAMDOM && wave in 0.5..1.5 -> r += Reason("$tag – 적당한 물결", 3)
                wave < 0.3 && s.likesSomeWave -> r += Reason("$tag – 너무 잔잔함", -4)
                else -> r += Reason(tag, 0)
            }
            if ((onshore || (swellAngle != null && swellAngle <= 70)) && wave >= 1.5 && danger == null) danger = "정면 너울 – 갯바위 진입 주의"
        }
        if (rainy) r += Reason("강수 예보", -3)

        // ── 과거 조과 ─────────────────────────
        r += Factors.historyReasons(p, s, c, slot, wind, windDir, seaWave, ctx)
        r += Factors.dayReasons(s, c, slot, wind, windDir, seaWave, ctx)

        var score = toScore(r.sumOf { it.delta })
        if (danger != null) score = score.coerceAtMost(15)
        return SlotScore(slot, score, r, danger, wind, windDir, wave, phaseText)
    }

    /**
     * 어종 × 지형 × 계절. 감성돔 계절 이동: 봄 오름(3~4월, 조류 잘 가는 여밭·곶부리) → 산란(5~6월, 해조류 붙은 얕은 홈통·여밭)
     * → 고수온기(7~8월, 조류 소통 좋은 곶부리) → 가을 연안 회유(9~11월, 여밭) → 겨울(12~2월, 깊은 직벽·수로)
     */
    private fun terrainFit(p: FishingPoint, s: Species, t: Double, m: Int): Reason? = when (s) {
        Species.GAMSEONG -> {
            val tr = p.terrain
            val deep = tr == "직벽" || p.depthMax >= 11
            when {
                t < 13.5 && deep -> Reason("깊은 직벽·수로 – 저수온기 감성돔 은신처", 6)
                m in 9..11 -> when (tr) {
                    "여밭" -> Reason("가을 연안 회유 – 여밭에 붙는 시기", 6)
                    "홈통" -> Reason("홈통 – 밑밥이 모이는 지형", 3)
                    "곶부리" -> Reason("곶부리 – 조류 경계", 2)
                    else -> null
                }
                m == 12 || m <= 2 -> if (deep) Reason("겨울 – 깊은 직벽·수로로 빠지는 시기", 5) else if (p.depthMax <= 6) Reason("겨울 – 얕은 자리에선 빠짐", -3) else null
                m == 3 || m == 4 -> if (tr == "여밭" || tr == "곶부리") Reason("봄 오름 – 조류 잘 가는 여밭·곶부리", 4) else null
                m == 5 || m == 6 -> if (tr == "홈통" || tr == "여밭") Reason("산란기 – 해조류 붙은 얕은 홈통·여밭", 3) else null
                else -> when (tr) {
                    "곶부리" -> Reason("고수온기 – 조류 소통 좋은 곶부리", 3)
                    "홈통" -> Reason("고수온기 – 물이 고이는 홈통", -2)
                    else -> null
                }
            }
        }
        Species.BENGAE -> when (p.terrain) {
            "곶부리" -> Reason("곶부리 – 조류 받는 벵에 자리", 5)
            "직벽" -> Reason("직벽 – 벵에 은신처", 4)
            "여밭" -> Reason("여밭 – 수중여 주변", 3)
            else -> null
        }
        Species.BOLLAK -> if (p.terrain == "여밭" || p.terrain == "홈통") Reason("여밭·홈통 – 볼락 은신처", 4) else null
        Species.MUNUI -> if (p.terrain == "곶부리" || p.terrain == "직벽") Reason("돌출부·직벽 – 에깅 유리", 4) else null
        Species.CHAMDOM, Species.NONGEO -> null
    }

    /** 부수 어종 자리 판정 (대표 어종 목록 대신) */
    private fun habitatFit(p: FishingPoint, s: Species, prof: PointProfile): Reason? = when (s) {
        Species.CHAMDOM -> when {
            prof.exposure >= 0.9 && p.depthMax >= 10 -> Reason("외해 본류대·깊은 수심 – 참돔 회유 길목", 6)
            prof.exposure >= 0.65 && p.depthMax >= 8 -> Reason("반쯤 열린 깊은 자리 – 참돔 가능", 1)
            else -> Reason("막혔거나 얕은 자리 – 참돔 회유 적음", -6)
        }
        Species.NONGEO -> when {
            (p.terrain == "곶부리" || p.terrain == "여밭") && prof.exposure >= 0.65 -> Reason("포말 지는 곶부리·여밭 – 농어 사냥터", 6)
            prof.exposure >= 0.65 -> Reason("열린 갯바위 – 농어 가능", 1)
            else -> Reason("막힌 홈통·안쪽 – 농어 적음", -5)
        }
        else -> null
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
