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
) {
    GAMSEONG(
        "감성돔", 15.0..22.0, 11.0..25.0,
        // 두도 조행기: 7~9월에도 마릿수 조과 → 여름 0점이던 값을 올림
        intArrayOf(3, 3, 2, 2, 2, 1, 1, 2, 3, 3, 3, 3),
        Activity.TWILIGHT, likesIncoming = true, likesSomeWave = true, waveLimit = 1.5
    ),
    BENGAE(
        "벵에돔", 17.0..23.0, 14.0..26.0,
        intArrayOf(0, 0, 0, 1, 2, 3, 3, 3, 3, 3, 2, 1),
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
    );

    companion object {
        fun byLabel(label: String) = entries.firstOrNull { it.label == label }
    }
}

enum class Activity { DAY, NIGHT, TWILIGHT }

/** 하루를 나눈 시간대 */
enum class TimeSlot(val label: String, val startHour: Int, val endHour: Int) {
    DAWN("새벽", 4, 7),
    MORNING("오전", 7, 11),
    MIDDAY("한낮", 11, 15),
    AFTERNOON("오후", 15, 19),
    NIGHT("밤", 19, 24);

    val rangeText get() = "%02d~%02d시".format(startHour, endHour)
}
