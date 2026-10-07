package com.dudo.fishing.data

import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tan

/**
 * 기상청 단기예보 조회서비스 (공공데이터포털)
 * https://apis.data.go.kr/1360000/VilageFcstInfoService_2.0/getVilageFcst
 */
object KmaApi {
    private val D = DateTimeFormatter.ofPattern("yyyyMMdd")

    /**
     * 오늘 02시 발표분을 요청하면 오늘 03시~모레까지 시간별 예보가 들어있다.
     * 새벽 2시 10분 이전이면 어제 23시 발표분을 사용한다.
     */
    data class Result(val hours: List<HourWeather>, val issued: String, val grid: String, val hasWave: Boolean)

    fun fetch(key: String, lat: Double, lng: Double, now: LocalDateTime): Result {
        val (nx, ny) = toGrid(lat, lng)
        val useYesterday = now.toLocalTime().isBefore(LocalTime.of(2, 10))
        val baseDate = if (useYesterday) now.toLocalDate().minusDays(1) else now.toLocalDate()
        val baseTime = if (useYesterday) "2300" else "0200"
        var hours = request(key, baseDate, baseTime, nx, ny)

        // 파고(WAV)는 바다 격자에만 나온다. 해안 격자라 파고가 없으면 바로 남쪽(바다) 격자에서 받아 합친다.
        if (hours.none { it.wave != null }) {
            val sea = runCatching { request(key, baseDate, baseTime, nx, ny - 1) }.getOrDefault(emptyList())
            val waveByTime = sea.associate { it.time to it.wave }
            hours = hours.map { h -> h.copy(wave = waveByTime[h.time]) }
        }
        val issued = "${baseDate.monthValue}/${baseDate.dayOfMonth} ${baseTime.substring(0, 2)}시 발표"
        return Result(hours, issued, "$nx,$ny", hours.any { it.wave != null })
    }

    private fun request(key: String, baseDate: LocalDate, baseTime: String, nx: Int, ny: Int): List<HourWeather> {
        val url = "https://apis.data.go.kr/1360000/VilageFcstInfoService_2.0/getVilageFcst" +
                "?serviceKey=${Http.encodeKey(key)}&pageNo=1&numOfRows=2000&dataType=JSON" +
                "&base_date=${baseDate.format(D)}&base_time=$baseTime&nx=$nx&ny=$ny"
        return parse(Http.get(url))
    }

    /** 인증키 오류 등은 JSON 요청이어도 XML로 응답하므로 따로 해석한다 */
    private fun xmlError(body: String): String {
        val auth = Regex("<returnAuthMsg>(.*?)</returnAuthMsg>").find(body)?.groupValues?.get(1)
        val msg = Regex("<resultMsg>(.*?)</resultMsg>").find(body)?.groupValues?.get(1)
        val text = auth ?: msg ?: "알 수 없는 응답"
        return when {
            text.contains("NOT_REGISTERED") -> "인증키가 등록되지 않았어요. 활용신청 승인 후 1~2시간 뒤에 사용할 수 있어요."
            text.contains("LIMITED_NUMBER") -> "오늘 호출 한도를 넘었어요."
            text.contains("DEADLINE") -> "인증키 사용 기간이 끝났어요."
            else -> "기상청 응답 오류: $text"
        }
    }

    internal fun parse(body: String): List<HourWeather> {
        if (body.trimStart().startsWith("<")) error(xmlError(body))
        val root = JSONObject(body).getJSONObject("response")
        val code = root.getJSONObject("header").getString("resultCode")
        if (code != "00") error("기상청 응답 오류: " + root.getJSONObject("header").optString("resultMsg"))
        val items = root.getJSONObject("body").getJSONObject("items").getJSONArray("item")

        val byTime = sortedMapOf<LocalDateTime, MutableMap<String, String>>()
        for (i in 0 until items.length()) {
            val it = items.getJSONObject(i)
            val date = LocalDate.parse(it.getString("fcstDate"), D)
            val t = it.getString("fcstTime")
            val dt = date.atTime(t.substring(0, 2).toInt(), 0)
            byTime.getOrPut(dt) { mutableMapOf() }[it.getString("category")] = it.getString("fcstValue")
        }
        return byTime.map { (dt, m) ->
            HourWeather(
                time = dt,
                windSpeed = m["WSD"]?.toDoubleOrNull() ?: 0.0,
                windDir = m["VEC"]?.toDoubleOrNull()?.toInt() ?: 0,
                wave = m["WAV"]?.toDoubleOrNull(),
                rainProb = m["POP"]?.toIntOrNull() ?: 0,
                precipType = m["PTY"]?.toIntOrNull() ?: 0,
                temp = m["TMP"]?.toDoubleOrNull(),
            )
        }
    }

    /** 위경도 → 기상청 격자(nx, ny) 변환 (기상청 제공 LCC 공식) */
    fun toGrid(lat: Double, lon: Double): Pair<Int, Int> {
        val re = 6371.00877 / 5.0
        val deg = PI / 180.0
        val slat1 = 30.0 * deg
        val slat2 = 60.0 * deg
        val olon = 126.0 * deg
        val olat = 38.0 * deg
        var sn = tan(PI * 0.25 + slat2 * 0.5) / tan(PI * 0.25 + slat1 * 0.5)
        sn = ln(cos(slat1) / cos(slat2)) / ln(sn)
        var sf = tan(PI * 0.25 + slat1 * 0.5)
        sf = sf.pow(sn) * cos(slat1) / sn
        var ro = tan(PI * 0.25 + olat * 0.5)
        ro = re * sf / ro.pow(sn)
        var ra = tan(PI * 0.25 + lat * deg * 0.5)
        ra = re * sf / ra.pow(sn)
        var theta = lon * deg - olon
        if (theta > PI) theta -= 2.0 * PI
        if (theta < -PI) theta += 2.0 * PI
        theta *= sn
        val x = floor(ra * sin(theta) + 43.0 + 0.5).toInt()
        val y = floor(ro - ra * cos(theta) + 136.0 + 0.5).toInt()
        return x to y
    }

    /** API 키가 없을 때 쓰는 데모용 가상 예보 */
    fun demo(date: LocalDate): List<HourWeather> {
        val seed = date.toEpochDay()
        return (0 until 72).map { h ->
            val dt = date.atStartOfDay().plusHours(h.toLong())
            val wave = 0.5 + 0.4 * sin((h + seed) / 9.0)
            HourWeather(
                time = dt,
                windSpeed = 4.0 + 3.0 * sin((h + seed * 3) / 7.0),
                windDir = ((30 + h * 4 + seed * 37) % 360).toInt(),
                wave = (wave * 10).toInt() / 10.0,
                rainProb = 10,
                precipType = 0,
                temp = 20.0,
            )
        }
    }
}
