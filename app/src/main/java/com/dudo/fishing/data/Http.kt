package com.dudo.fishing.data

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

internal object Http {
    fun get(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000
        try {
            val code = conn.responseCode
            val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) error("HTTP $code")
            return body
        } finally {
            conn.disconnect()
        }
    }

    /** 공공데이터포털 키: '인코딩 키'(%가 포함됨)는 그대로, '디코딩 키'는 인코딩해서 사용 */
    fun encodeKey(key: String): String =
        if (key.contains('%')) key else URLEncoder.encode(key, "UTF-8")
}
