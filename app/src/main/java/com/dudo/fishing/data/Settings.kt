package com.dudo.fishing.data

import android.content.Context

/** 사용자 설정 (API 키, 수온 직접 입력) */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** 공공데이터포털(data.go.kr) 인증키 – 기상청 단기예보용 */
    var dataGoKrKey: String
        get() = prefs.getString("dataGoKrKey", "") ?: ""
        set(v) = prefs.edit().putString("dataGoKrKey", v.trim()).apply()

    /** 바다누리(khoa.go.kr) 인증키 – 조석예보용 */
    var khoaKey: String
        get() = prefs.getString("khoaKey", "") ?: ""
        set(v) = prefs.edit().putString("khoaKey", v.trim()).apply()

    /** 현장 수온을 알면 직접 입력 (빈 값이면 월별 평균으로 추정) */
    var manualWaterTemp: Double?
        get() = prefs.getString("manualWaterTemp", null)?.toDoubleOrNull()
        set(v) = prefs.edit().putString("manualWaterTemp", v?.toString()).apply()
}
