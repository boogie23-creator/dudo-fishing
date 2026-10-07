package com.dudo.fishing.data

import android.content.Context
import com.dudo.fishing.BuildConfig

/** 사용자 설정 (API 키, 수온 직접 입력) */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /**
     * 공공데이터포털(data.go.kr) 인증키 – 기상청 단기예보용.
     * 설정 화면에 입력한 값이 우선이고, 비어 있으면 빌드할 때 넣은 키(GitHub Secret KMA_API_KEY)를 쓴다.
     */
    var dataGoKrKey: String
        get() = userKmaKey.ifBlank { BuildConfig.KMA_API_KEY }
        set(v) = prefs.edit().putString("dataGoKrKey", v.trim()).apply()

    /** 설정 화면에 사용자가 직접 입력한 키 */
    val userKmaKey: String get() = prefs.getString("dataGoKrKey", "") ?: ""

    val hasBuiltInKmaKey: Boolean get() = BuildConfig.KMA_API_KEY.isNotBlank()

    /** 바다누리(khoa.go.kr) 인증키 – 조석예보용 */
    var khoaKey: String
        get() = prefs.getString("khoaKey", "") ?: ""
        set(v) = prefs.edit().putString("khoaKey", v.trim()).apply()

    /** 수온·파고를 받을 해수욕장 번호 (직접 입력하거나 자동으로 찾은 값) */
    var beachNum: Int?
        get() = prefs.getInt("beachNum", -1).takeIf { it > 0 }
        set(v) = prefs.edit().putInt("beachNum", v ?: -1).apply()


    /** 현장 수온을 알면 직접 입력 (빈 값이면 월별 평균으로 추정) */
    var manualWaterTemp: Double?
        get() = prefs.getString("manualWaterTemp", null)?.toDoubleOrNull()
        set(v) = prefs.edit().putString("manualWaterTemp", v?.toString()).apply()
}
