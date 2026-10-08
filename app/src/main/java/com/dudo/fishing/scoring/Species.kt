package com.dudo.fishing.scoring

/**
 * 어종별 선호 조건.
 * 숫자는 일반적인 남해안 갯바위 낚시 경험칙을 바탕으로 한 초기값이며,
 * 조과 기록이 쌓이면 보정하는 것을 전제로 한다.
 */
enum class Species(
    val label: String,
    val idealTemp: ClosedFloatingPointRange<Double>,
    val okTemp: ClosedFloatingPointRange<Double>,
    /** 1~12월 시즌 적합도 0(비시즌)~3(최성기) */
    val season: IntArray,
    val activeAt: Activity,
    /** 들물(밀물) 시간대를 특히 선호하는지 */
    val likesIncoming: Boolean,
    /** 약간의 파도(포말)를 좋아하는지 */
    val likesSomeWave: Boolean,
    val waveLimit: Double,
    /** 부수 어종: 포인트 대표 어종 목록 대신 지형·노출도로 자리를 판정 */
    val habitat: Boolean = false,
) {
    GAMSEONG(
        "감성돔", 15.0..22.0, 11.0..25.0,
        // 두도 조행기: 7~9월에도 마릿수 조과 → 여름 0점이던 값을 올림
        intArrayOf(3, 3, 2, 2, 2, 1, 1, 2, 3, 3, 3, 3),
        Activity.TWILIGHT, likesIncoming = true, likesSomeWave = true, waveLimit = 1.5
    ),
    BENGAE(
        "벵에돔", 18.0..24.0, 15.0..26.0,
        // 두도 밴드 조황: 10월부터는 감성돔 위주 → 벵에돔 10~12월 낮춤
        intArrayOf(0, 0, 0, 1, 2, 3, 3, 3, 3, 2, 1, 0),
        Activity.DAY, likesIncoming = false, likesSomeWave = true, waveLimit = 1.5
    ),
    BOLLAK(
        "볼락", 10.0..16.0, 8.0..19.0,
        intArrayOf(3, 3, 3, 3, 2, 1, 0, 0, 0, 1, 2, 3),
        Activity.NIGHT, likesIncoming = true, likesSomeWave = false, waveLimit = 1.2
    ),
    MUNUI(
        "무늬오징어", 17.0..24.0, 15.0..26.0,
        intArrayOf(0, 0, 0, 1, 2, 2, 1, 1, 3, 3, 2, 1),
        Activity.TWILIGHT, likesIncoming = false, likesSomeWave = false, waveLimit = 1.0
    ),
    // 부수 어종(초기값 – 두도 조황 기록이 쌓이면 보정)
    CHAMDOM(
        "참돔", 16.0..23.0, 13.0..26.0,
        intArrayOf(0, 0, 0, 1, 3, 3, 2, 1, 2, 3, 2, 1),
        Activity.TWILIGHT, likesIncoming = false, likesSomeWave = true, waveLimit = 1.5, habitat = true
    ),
    NONGEO(
        "농어", 14.0..22.0, 10.0..25.0,
        intArrayOf(1, 0, 0, 2, 3, 3, 2, 1, 2, 3, 3, 2),
        Activity.TWILIGHT, likesIncoming = false, likesSomeWave = true, waveLimit = 1.9, habitat = true
    );

    companion object {
        fun byLabel(label: String) = entries.firstOrNull { it.label == label }
    }
}

enum class Activity { DAY, NIGHT, TWILIGHT }

/** 시간대 (기본: 낚시 시간 05~13시를 1시간 단위로) */
data class TimeSlot(val label: String, val startHour: Int, val endHour: Int) {
    val rangeText get() = "%02d~%02d시".format(startHour, endHour)

    companion object {
        /** 보통 낚시 시간 05~13시 */
        const val FISHING_START = 5
        const val FISHING_END = 13
        val FISHING: List<TimeSlot> = (FISHING_START until FISHING_END).map { TimeSlot("%02d시".format(it), it, it + 1) }
        /** 화면에 보여주는 시간 05~18시 (13시 이후는 참고용, 순위에는 안 들어감) */
        const val DISPLAY_END = 18
        val DISPLAY: List<TimeSlot> = (FISHING_START until DISPLAY_END).map { TimeSlot("%02d시".format(it), it, it + 1) }
    }
}
