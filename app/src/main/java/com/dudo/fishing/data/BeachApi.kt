package com.dudo.fishing.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.cos
import kotlin.math.hypot

/**
 * 기상청 전국 해수욕장 날씨 조회서비스 (공공데이터포털, 단기예보와 같은 인증키 사용)
 * http://apis.data.go.kr/1360000/BeachInfoservice  – 활용가이드 기준
 *
 * - getTwBuoyBeach   : 수온   (beach_num, searchTime=yyyyMMddHHmm) → tm, tw
 * - getWhBuoyBeach   : 파고   (beach_num, searchTime)             → tm, wh
 * - getTideInfoBeach : 조석   (beach_num, base_date=yyyyMMdd)     → tiTime(HH:mm), tiType(ET=간조, FT=만조), tilevel
 */
object BeachApi {
    private const val BASE = "https://apis.data.go.kr/1360000/BeachInfoservice"
    private val YMDHM = DateTimeFormatter.ofPattern("yyyyMMddHHmm")
    private val YMD = DateTimeFormatter.ofPattern("yyyyMMdd")

    data class Beach(val num: Int, val name: String, val lat: Double, val lng: Double) {
        fun distanceKm(lat2: Double, lng2: Double): Double =
            hypot((lat - lat2) * 111.0, (lng - lng2) * 111.0 * cos(Math.toRadians(lat)))
    }

    data class Reading(val value: Double, val time: String)

    /** assets/beaches.json (활용가이드 별첨 해변코드·위경도 표) */
    fun loadBeaches(context: Context): List<Beach> {
        val text = context.assets.open("beaches.json").bufferedReader().use { it.readText() }
        val arr = JSONObject(text).getJSONArray("beaches")
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Beach(o.getInt("num"), o.getString("name"), o.getDouble("lat"), o.getDouble("lng"))
        }
    }

    fun waterTemp(key: String, beachNum: Int, now: LocalDateTime): Reading? =
        latest(key, "getTwBuoyBeach", "tw", beachNum, now)

    fun waveHeight(key: String, beachNum: Int, now: LocalDateTime): Reading? =
        latest(key, "getWhBuoyBeach", "wh", beachNum, now)

    private fun latest(key: String, op: String, field: String, beachNum: Int, now: LocalDateTime): Reading? {
        // 관측은 보통 30분~1시간 간격이라, 정각 기준으로 최근 몇 시간을 거슬러 올라가며 찾는다
        for (back in 0..3) {
            val t = now.minusHours(back.toLong()).withMinute(0)
            val url = "$BASE/$op?serviceKey=${Http.encodeKey(key)}&numOfRows=10&pageNo=1&dataType=JSON" +
                    "&beach_num=$beachNum&searchTime=${t.format(YMDHM)}"
            val items = items(Http.get(url)) ?: continue
            for (i in items.length() - 1 downTo 0) {
                val o = items.getJSONObject(i)
                val v = o.optString(field).toDoubleOrNull() ?: continue
                if (v < -50 || v > 60) continue
                return Reading(v, prettyTime(o.optString("tm")))
            }
        }
        return null
    }

    /** 해당 날짜의 만조·간조 */
    fun tides(key: String, beachNum: Int, date: LocalDate): List<TideEvent> {
        val url = "$BASE/getTideInfoBeach?serviceKey=${Http.encodeKey(key)}&numOfRows=20&pageNo=1" +
                "&dataType=JSON&beach_num=$beachNum&base_date=${date.format(YMD)}"
        val items = items(Http.get(url)) ?: return emptyList()
        return (0 until items.length()).mapNotNull { i ->
            val o = items.getJSONObject(i)
            val time = parseHm(o.optString("tiTime")) ?: return@mapNotNull null
            val type = o.optString("tiType").uppercase()
            if (!type.startsWith("FT") && !type.startsWith("ET")) return@mapNotNull null
            val level = (o.optString("tilevel").ifBlank { o.optString("tiLevel") }).toDoubleOrNull()?.toInt()
            TideEvent(date.atTime(time), isHigh = type.startsWith("FT"), levelCm = level)
        }
    }

    /** 응답의 item 배열. 오류·자료없음이면 null */
    private fun items(body: String): JSONArray? {
        if (body.trimStart().startsWith("<")) return null
        val res = JSONObject(body).optJSONObject("response") ?: return null
        if (res.optJSONObject("header")?.optString("resultCode") != "00") return null
        val items = res.optJSONObject("body")?.opt("items") as? JSONObject ?: return null
        // 결과가 1건일 때 배열이 아니라 객체로 오는 경우도 처리
        return items.optJSONArray("item") ?: items.optJSONObject("item")?.let { JSONArray().put(it) }
    }

    private fun parseHm(s: String): LocalTime? {
        val digits = s.filter { it.isDigit() }
        if (digits.length < 3) return null
        val hm = digits.padStart(4, '0').takeLast(4)
        return runCatching { LocalTime.of(hm.substring(0, 2).toInt(), hm.substring(2, 4).toInt()) }.getOrNull()
    }

    private fun prettyTime(tm: String): String {
        val d = tm.filter { it.isDigit() }
        return if (d.length >= 12) "${d.substring(4, 6).toInt()}/${d.substring(6, 8).toInt()} ${d.substring(8, 10)}:${d.substring(10, 12)}" else tm
    }
}
