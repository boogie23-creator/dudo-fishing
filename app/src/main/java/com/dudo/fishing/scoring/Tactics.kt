package com.dudo.fishing.scoring

import com.dudo.fishing.data.DayConditions
import com.dudo.fishing.data.FishingPoint
import kotlin.math.abs

/** 공략법 한 줄 */
data class Tip(val kind: Kind, val title: String, val text: String) {
    enum class Kind { SAFETY, SPOT, CURRENT, RIG, CHUM, BAIT, TIMING, FIX }
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
        tips += Tip(Tip.Kind.RIG, "채비", rigText(p, s, strength, deep, wind, wave, c.waterTemp))

        // 3-1. 밑밥 운용
        chumText(s, c.waterTemp, strength, wind)?.let { tips += Tip(Tip.Kind.CHUM, "밑밥 운용", it) }

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

        // 6. 입질이 없을 때
        fixText(s)?.let { tips += Tip(Tip.Kind.FIX, "입질이 없을 때", it) }

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

    private fun rigText(p: FishingPoint, s: Species, strength: Double, deep: Boolean, wind: Double, wave: Double?, temp: Double): String {
        val parts = mutableListOf<String>()
        when (s) {
            Species.GAMSEONG -> {
                parts += when {
                    temp < 13.5 -> "저수온기 – 1.5~3호 고부력 반유동으로 30m 이상 먼 여밭·깊은 곳까지. 목줄은 굵게(1.75~2호) 써서 미끼가 먼저 흘러가게 하세요. 입질이 없으면 잠길찌로."
                    deep && strength < 0.35 -> "잠길찌(2B~0.5호)로 직벽·깊은 여를 따라 바닥층에 오래 머물게 하세요. 찌매듭으로 수심 ${(p.depthMax - 1).toInt()}m부터 탐색."
                    deep -> "0.5~1호 반유동, 찌밑 수심 ${(p.depthMax - 1).toInt()}~${p.depthMax.toInt()}m. 바닥 걸림이 나면 50cm씩 올리세요."
                    strength > 0.65 -> "2B~3B 반유동으로 바닥층을 지키세요. 찌밑 수심 ${p.depthMax.toInt()}m 전후."
                    else -> "B~G2 전유동, 또는 B조법(B찌·제로쿠션 수중찌·목줄 4m). 찌매듭은 예상 수심(${p.depth})보다 1~2m 깊게. 두도에선 가벼운 전유동에 입질이 몰려요."
                }
                if (wind < 2.5 && (wave ?: 0.5) < 0.4) parts += "바람·파도가 없으면 경계심이 커요 – 평소보다 멀리(15m 이상) 던지고 목줄은 1.2호 이하로."
                else if (wave != null && wave in 0.5..1.2) parts += "적당한 포말이 있어 얕은 여 가까이 붙여도 돼요."
                parts += "착수 후 뒷줄을 조금씩 잡아 미끼가 찌보다 먼저 흘러가게(미끼 선행) 하고, 찌가 잠기면 한 박자 늦게 챔질."
            }
            Species.BENGAE -> {
                parts += when {
                    temp < 17 -> "수온이 낮아 벵에가 잘 안 떠요 – 투제로·잠길찌로 4~6m 중하층을 천천히 훑으세요."
                    else -> "0 또는 G2 찌 띄울낚시, 찌밑 두 발(3m 안팎)부터. 활성이 오르면 찌매듭을 찌 쪽으로 내려 더 얕게."
                }
                parts += "목줄 1~1.2호(가늘수록 늦게 흘러 입질이 늘어요), 봉돌은 바늘 바로 위 작은 것으로 – 미끼가 위에서 천천히 내려오게."
                if (temp >= 20) parts += "잡어가 많으면 B찌 L조법: 직결부 30cm 아래 B봉돌로 잡어층을 빨리 통과시키고 4~5m 층에서 입질을 받으세요."
            }
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

    /**
     * 밑밥 배합·투척. 근거: 낚시춘추 '감성돔·벵에돔 고수 10인의 밑밥 솔루션', '잡어 많을 땐 압맥 비율',
     * 민병진 '벵에돔 밑밥 품질 타이밍', 영등철 원투 공략 기사.
     */
    private fun chumText(s: Species, temp: Double, strength: Double, wind: Double): String? {
        val parts = mutableListOf<String>()
        when (s) {
            Species.GAMSEONG -> {
                parts += when {
                    temp < 13.5 -> "하루 기준 크릴 8장 이상 + 무거운 집어제 3봉 + 압맥 6봉. 원투해야 하니 크릴을 잘게 부숴 단단히 뭉치세요."
                    temp < 20 -> "하루 기준 크릴 5~6장 + 집어제 2~3봉 + 압맥 3~4봉 (수온 20°C 아래로 내려가면 압맥을 늘림)."
                    else -> "하루 기준 크릴 5장 + 집어제 2봉 + 압맥 3봉. 잡어가 많으면 크릴을 줄이고 압맥을 늘려 바닥까지 빨리 내리세요."
                }
                parts += "입질 시간 전에 미리 깔아두는 게 핵심 – 간조·초들물부터 공략 지점에 넓게 베이스 밑밥을 깔고, 이후엔 채비 던질 때마다 5~6주걱씩 끊기지 않게."
                if (strength > 0.6) parts += "조류가 세니 건식 집어제 비율을 높여 덩어리째 바닥까지 내려가게 하세요."
                if (wind >= 6) parts += "맞바람이면 하루 전 숙성한 떡밑밥이 덜 흩어져요."
                parts += "발밑에 조금씩 뿌려 잡어를 묶어두고, 본 밑밥은 공략 지점 반경 1m 안으로 정투."
            }
            Species.BENGAE -> {
                parts += "선 밑밥 후 채비: 밑밥을 먼저 2~3주걱 뿌리고 5초 → 10초 → 15초로 시간 차를 바꿔가며 채비를 던져, 씨알이 가장 굵게 나오는 간격을 찾으세요."
                parts += "작은 씨알만 나오면 간격을 늘리고, 같은 간격에 입질이 줄면 던지는 위치를 앞뒤·좌우로 바꾸세요. 잡어 분리용 밑밥은 발밑에 꾸준히."
                parts += "밑밥 90%는 잡어 묶기용이라 생각하고 아끼지 마세요. 밑밥이 잡어에 다 먹혀도 큰 벵에의 관심은 상층에 남아 있어요."
            }
            else -> return null
        }
        return parts.joinToString(" ")
    }

    /** 입질 진단 – 미끼 상태와 입질 모양으로 수심·채비를 바꾸는 순서 */
    private fun fixText(s: Species): String? = when (s) {
        Species.GAMSEONG -> "① 미끼가 그대로 올라오면 → 수심을 50cm~1m 깊게, 그래도 없으면 공략 지점을 옮기세요. " +
                "② 미끼만 따이면(잡어) → 압맥 비율을 올리고 봉돌을 더해 잡어층을 빨리 통과. " +
                "③ 어신이 약하고 느리면 수심이 너무 깊은 것 – 조금 올리세요. " +
                "④ 그래도 없으면 잠길찌로 전환: 반유동 채비에 좁쌀봉돌을 더해 천천히 잠기게, 찌매듭은 날물 1m·들물 50cm 내리고 목줄은 1m로 짧게, 목줄·바늘 한 호수씩 낮추기."
        Species.BENGAE -> "① 미끼가 그대로면 → 찌밑 수심을 한 마디씩 깊게. ② 미끼만 따이면 → 시간 차를 늘리거나 L조법으로 잡어층 통과. " +
                "③ 경계심이 강하면 목줄을 1호로 낮추고 미끼(크릴)를 작고 부드러운 것으로."
        else -> null
    }
}
