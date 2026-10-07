package com.dudo.fishing.scoring

import com.dudo.fishing.data.DayConditions
import com.dudo.fishing.data.FishingPoint
import kotlin.math.abs

/** 공략법 한 줄 */
data class Tip(val kind: Kind, val title: String, val text: String) {
    enum class Kind { SAFETY, SPOT, CURRENT, RIG, BAIT, TIMING }
}

/**
 * 오늘의 공략법 – 포인트 지형 + 물때·조류 방향 + 바람·파도 + 어종으로 찌낚시 공략을 문장으로 만든다.
 *
 * 근거
 * - 밑밥은 조류 위쪽에 뿌려 채비와 함께 흘러가게(동조) 하는 것이 찌낚시 기본
 * - 본류와 지류가 만나는 경계·조류가 맴도는 곳이 1급 포인트 (본류대 낚시 기법)
 * - 감성돔은 수심 4~8m 여밭이 핵심 (박진철 B조법), 약한 조류·깊은 여에는 잠길찌, 탁하고 원거리 바닥층은 반유동
 * - 두도 현지(은파 밴드·조행기): 가벼운 전유동에 입질 집중, 물돌이 전후 대물, 잔잔하면 먼 거리 입질,
 *   9월 옥수수·11월 크릴·경단·4월 모에비, 전방 10~15m 수심 4~6m 여밭
 */
object Tactics {

    fun build(p: FishingPoint, s: Species, slot: SlotScore, c: DayConditions): List<Tip> {
        val tips = mutableListOf<Tip>()
        val center = c.date.atTime(slot.slot.startHour, 0).plusMinutes(((slot.slot.endHour - slot.slot.startHour) * 30).toLong())
        val t = Factors.tideState(center, c.tides)
        val strength = t?.let { Factors.currentStrength(it, c.tideRangeFactor) } ?: 0.3
        val wind = slot.windSpeed
        val wave = slot.wave
        val deep = p.depthMin >= 8 || p.terrain == "직벽"

        // 0. 안전
        slot.danger?.let { tips += Tip(Tip.Kind.SAFETY, "안전 먼저", "$it – 이 시간대는 갯바위 진입을 피하거나 높은 자리에서 구명조끼·스파이크 필수.") }

        // 1. 공략 지점
        if (p.target.isNotBlank()) tips += Tip(
            Tip.Kind.SPOT, "어디를 노리나",
            "${p.target}. ${ScoreEngine.compass(p.facingDeg)}쪽 전방 약 ${p.targetDistance}m, 수심 ${p.depth}. (지도의 원이 공략 지점)"
        )

        // 2. 조류와 밑밥
        tips += Tip(Tip.Kind.CURRENT, "조류와 밑밥", currentText(p, t, strength))

        // 3. 채비
        tips += Tip(Tip.Kind.RIG, "채비", rigText(p, s, strength, deep, wind, wave))

        // 4. 미끼
        baitText(s, c.date.monthValue)?.let { tips += Tip(Tip.Kind.BAIT, "미끼", it) }

        // 5. 타이밍
        val timing = mutableListOf<String>()
        c.tides.filter { it.time.toLocalDate() == c.date && it.time.hour in slot.slot.startHour until slot.slot.endHour }
            .forEach { e ->
                timing += "%02d:%02d %s 물돌이 전후 30분에 집중하세요%s".format(
                    e.time.hour, e.time.minute, if (e.isHigh) "만조" else "간조",
                    if (s == Species.GAMSEONG) " – 두도 대물 감성돔이 자주 무는 시간(밴드 조황)." else "."
                )
            }
        if (c.sunrise.hour in (slot.slot.startHour - 1) until slot.slot.endHour) timing += "해 뜰 무렵(%02d:%02d) 피딩타임 – 첫 밑밥은 해 뜨기 전에 깔아 두세요.".format(c.sunrise.hour, c.sunrise.minute)
        if (c.sunset.hour in (slot.slot.startHour - 1) until slot.slot.endHour) timing += "해 질 무렵(%02d:%02d) 피딩타임.".format(c.sunset.hour, c.sunset.minute)
        if (timing.isNotEmpty()) tips += Tip(Tip.Kind.TIMING, "타이밍", timing.joinToString(" "))

        return tips
    }

    private fun currentText(p: FishingPoint, t: TideState?, strength: Double): String {
        if (t == null) return "조석 정보가 없어요. 찌가 흐르는 방향을 먼저 확인하고, 밑밥은 그보다 위쪽에 뿌리세요."
        val tideName = if (t.incoming) "들물(북동쪽으로 흐름)" else "날물(남서쪽으로 흐름)"
        if (t.nearSlack) return "물돌이 무렵이라 조류가 거의 멈춰요. 밑밥이 발 앞에 고이니 공략 거리를 짧게 잡고, 같은 자리에 밑밥을 쌓으세요. " +
                "조류가 다시 살아나는 방향을 지켜보다가 그 위쪽으로 밑밥을 옮기세요."
        val flowDeg = if (t.incoming) Factors.FLOOD_FLOW_DEG else Factors.EBB_FLOW_DEG
        var rel = ((flowDeg - p.facingDeg) % 360 + 360) % 360
        if (rel > 180) rel -= 360
        val dir = when {
            abs(rel) <= 45 -> "조류가 앞쪽(공략 방향)으로 뻗어 나가요. 발 앞 2~3m에 밑밥을 뿌리고, 채비를 조류에 태워 공략 지점까지 흘린 뒤 원줄을 살짝 잡아 견제하세요."
            rel in 46..134 -> "조류가 왼쪽에서 오른쪽으로 흘러요. 밑밥은 공략 지점의 왼쪽(조류 위쪽) 5~10m에, 채비는 그보다 조금 더 왼쪽에 던져 밑밥과 함께 흘러 들어가게(동조) 하세요."
            rel in -134..-46 -> "조류가 오른쪽에서 왼쪽으로 흘러요. 밑밥은 공략 지점의 오른쪽(조류 위쪽) 5~10m에, 채비는 그보다 조금 더 오른쪽에 던져 함께 흘러 들어가게 하세요."
            p.terrain == "홈통" -> "조류가 갯바위 쪽으로 밀려와요. 홈통 안에 밑밥이 쌓이니 발밑 여 가장자리와 홈통 입구를 번갈아 노리세요."
            else -> "조류가 갯바위 쪽으로 받혀요. 채비가 발밑으로 밀려오니 공략 지점보다 멀리 던지고 수중찌를 키워 빨리 내려, 발 앞 여에 걸리기 전 구간에서 입질을 받으세요."
        }
        val power = when {
            strength > 0.65 -> " 지금은 조류가 빨라요 – 밑밥을 더 위쪽에 뿌리고, 채비가 떠오르지 않게 무게를 늘리세요. 본류 바로 옆 조류가 맴도는 경계가 노림수예요."
            strength < 0.3 -> " 조류가 약해요 – 같은 자리에 밑밥을 자주 반복해 밑밥띠를 만들고, 가벼운 채비로 천천히 가라앉히세요."
            else -> ""
        }
        return "$tideName. $dir$power"
    }

    private fun rigText(p: FishingPoint, s: Species, strength: Double, deep: Boolean, wind: Double, wave: Double?): String {
        val parts = mutableListOf<String>()
        when (s) {
            Species.GAMSEONG -> {
                parts += when {
                    deep && strength < 0.35 -> "잠길찌(2B~0.5호)로 직벽·깊은 여를 따라 바닥층에 오래 머물게 하세요. 찌매듭으로 수심 ${(p.depthMax - 1).toInt()}m부터 탐색."
                    deep -> "0.5~1호 반유동, 찌밑 수심 ${(p.depthMax - 1).toInt()}~${p.depthMax.toInt()}m. 바닥 걸림이 나면 50cm씩 올리세요."
                    strength > 0.65 -> "2B~3B 반유동으로 바닥층을 지키세요. 찌밑 수심 ${p.depthMax.toInt()}m 전후."
                    else -> "B~G2 전유동, 또는 B조법(B찌·제로쿠션 수중찌·목줄 4m). 찌매듭은 예상 수심(${p.depth})보다 1~2m 깊게. 두도에선 가벼운 전유동에 입질이 몰려요."
                }
                if (wind < 2.5 && (wave ?: 0.5) < 0.4) parts += "바람·파도가 없으면 경계심이 커요 – 평소보다 멀리(15m 이상) 던지고 목줄은 1.2호 이하로."
                else if (wave != null && wave in 0.5..1.2) parts += "적당한 포말이 있어 얕은 여 가까이 붙여도 돼요."
            }
            Species.BENGAE -> parts += "0~G2 전유동 또는 목줄찌 채비, 수심 2~5m 상층부터. 밑밥을 조금씩 자주 뿌려 띠를 만들고 그 띠 속으로 채비를 흘리세요. 입질이 없으면 한 마디씩 깊게."
            Species.BOLLAK -> parts += "가벼운 찌(G2~B) 볼락 채비, 수심 2~4m 여 가장자리와 홈통 그늘. 밤에는 집어등 쪽보다 그 경계를 노리세요."
            Species.MUNUI -> parts += "에기 2.5~3.5호. 조류가 걸리는 곶부리·직벽 앞에서 바닥까지 가라앉힌 뒤 2~3번 저킹하고 폴링 때 입질을 받으세요."
        }
        if (wind >= 6) parts += "바람이 세요(%.0fm/s) – 원줄이 날리지 않게 한 단계 무거운 찌를 쓰고, 초릿대를 물에 담가 원줄을 가라앉히세요.".format(wind)
        return parts.joinToString(" ")
    }

    private fun baitText(s: Species, month: Int): String? = when (s) {
        Species.GAMSEONG -> when (month) {
            7, 8, 9 -> "크릴 + 옥수수. 잡어·살감성돔이 많으면 옥수수로 (밴드: 9월 옥수수 최고)."
            10, 11, 12 -> "크릴·경단 위주 (밴드: 11월엔 옥수수 반응이 약하고 크릴·경단에 입질)."
            1, 2, 3 -> "크릴 – 잡어가 없어도 크릴이 가장 무난 (밴드 1월 조황)."
            else -> "크릴, 모에비 (밴드: 4월 모에비 입질 좋음). 5월은 감성돔 금어기(5/1~5/31)."
        }
        Species.BENGAE -> "크릴(부서지지 않게 작은 것). 밑밥은 크릴+벵에 전용 집어제."
        Species.BOLLAK -> "크릴, 청갯지렁이."
        Species.MUNUI -> null
    }
}
