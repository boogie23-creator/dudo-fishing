package com.dudo.fishing.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs

/**
 * 기상청 전국 해수욕장 날씨 조회서비스 (공공데이터포털, 단기예보와 같은 인증키 사용)
 * https://apis.data.go.kr/1360000/BeachInfoservice
 *
 * - getTwBuoyBeach : 해수욕장 부이 실측 수온(tw)
 * - getWhBuoyBeach : 해수욕장 부이 실측 파고(wh)
 * - getUltraSrtNcstBeach : 초단기실황 – 응답에 해당 해수욕장의 기상 격자(nx, ny)가 들어 있어
 *   두도와 가장 가까운 해수욕장 번호를 자동으로 찾는 데 쓴다.
 */
object BeachApi {
    private const val BASE = "https://apis.data.go.kr/1360000/BeachInfoservice"
    private val YMDHM = DateTimeFormatter.ofPattern("yyyyMMddHHmm")
    private val YMD = DateTimeFormatter.ofPattern("yyyyMMdd")
    private val TM = listOf(
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
        DateTimeFormatter.ofPattern("yyyyMMddHHmm"),
    )

    data class Reading(val value: Double, val time: String)

    fun waterTemp(key: String, beachNum: Int, now: LocalDateTime): Reading? =
        latest(key, "getTwBuoyBeach", "tw", beachNum, now)

    fun waveHeight(key: String, beachNum: Int, now: LocalDateTime): Reading? =
        latest(key, "getWhBuoyBeach", "wh", beachNum, now)

    private fun latest(key: String, op: String, field: String, beachNum: Int, now: LocalDateTime): Reading? {
        val url = "$BASE/$op?serviceKey=${Http.encodeKey(key)}&numOfRows=24&pageNo=1&dataType=JSON" +
                "&beach_num=$beachNum&searchTime=${now.format(YMDHM)}"
        val items = items(Http.get(url)) ?: return null
        // 가장 최근 값(마지막 항목부터)을 찾는다. 결측은 빈 값이나 -99 같은 값으로 온다.
        for (i in items.length() - 1 downTo 0) {
            val o = items.getJSONObject(i)
            val v = o.optString(field).toDoubleOrNull() ?: continue
            if (v < -50 || v > 60) continue
            return Reading(v, prettyTime(o.optString("tm")))
        }
        return null
    }

    /** 해수욕장 번호 → 기상 격자(nx, ny). 실패하면 null */
    fun gridOf(key: String, beachNum: Int, now: LocalDateTime): Pair<Int, Int>? {
        val base = now.minusMinutes(45)
        val url = "$BASE/getUltraSrtNcstBeach?serviceKey=${Http.encodeKey(key)}&numOfRows=10&pageNo=1" +
                "&dataType=JSON&beach_num=$beachNum&base_date=${base.format(YMD)}" +
                "&base_time=%02d00".format(base.hour)
        val items = items(Http.get(url)) ?: return null
        if (items.length() == 0) return null
        val o = items.getJSONObject(0)
        val nx = o.optString("nx").toIntOrNull() ?: return null
        val ny = o.optString("ny").toIntOrNull() ?: return null
        return nx to ny
    }

    /**
     * 1~420번 해수욕장을 20개씩 동시에 조회해서 목표 격자와 가장 가까운 번호를 찾는다.
     * 같은 격자(거리 0)를 찾으면 바로 멈춘다. 최초 한 번만 실행하고 결과는 설정에 저장한다.
     */
    suspend fun findNearest(key: String, nx: Int, ny: Int, now: LocalDateTime): Int? = coroutineScope {
        var best: Pair<Int, Int>? = null     // 번호 to 거리
        for (chunk in (1..420).chunked(20)) {
            val found = chunk.map { n ->
                async(Dispatchers.IO) { n to runCatching { gridOf(key, n, now) }.getOrNull() }
            }.awaitAll()
            for ((n, g) in found) {
                if (g == null) continue
                val d = abs(g.first - nx) + abs(g.second - ny)
                if (best == null || d < best!!.second) best = n to d
            }
            if (best?.second == 0) break
        }
        best?.takeIf { it.second <= 3 }?.first
    }

    private fun items(body: String): JSONArray? {
        if (body.trimStart().startsWith("<")) return null
        val res = JSONObject(body).optJSONObject("response") ?: return null
        if (res.optJSONObject("header")?.optString("resultCode") != "00") return null
        return res.optJSONObject("body")?.optJSONObject("items")?.optJSONArray("item")
    }

    private fun prettyTime(tm: String): String {
        for (f in TM) {
            runCatching { return LocalDateTime.parse(tm, f).let { "%d/%d %02d:%02d".format(it.monthValue, it.dayOfMonth, it.hour, it.minute) } }
        }
        return tm
    }
}
