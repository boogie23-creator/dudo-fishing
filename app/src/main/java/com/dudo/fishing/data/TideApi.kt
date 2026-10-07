package com.dudo.fishing.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 조석(만조·간조) 정보.
 *
 * 1) 바다누리(국립해양조사원) 조석예보 OpenAPI – 부산 관측소(DT_0005)
 *    국립해양조사원 API가 공공데이터포털(apis.data.go.kr/1192136/...)로 이전 중이므로
 *    키 발급 위치에 따라 [fetchKhoa] 의 URL만 바꾸면 된다.
 * 2) 키가 없거나 실패하면 달의 남중 시각으로 만조·간조를 '추정'한다.
 */
object TideApi {
    const val BUSAN_OBS_CODE = "DT_0005"
    private val D = DateTimeFormatter.ofPattern("yyyyMMdd")
    private val DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    /**
     * 국립해양조사원 조석예보(고, 저조) – 공공데이터포털 버전 (활용가이드 2025-12 기준, 단기예보와 같은 인증키)
     * https://apis.data.go.kr/1192136/tideFcstHghLw/GetTideFcstHghLwApiService
     * 요청: obsCode, reqDate(yyyyMMdd), type=json
     * 응답: predcDt("yyyy-MM-dd HH:mm"), predcTdlvVl(cm), extrSe(1 오전고조, 2 오전저조, 3 오후고조, 4 오후저조)
     */
    fun fetchDataGoKr(key: String, date: LocalDate, obsCode: String = BUSAN_OBS_CODE): List<TideEvent> {
        return listOf(date.minusDays(1), date, date.plusDays(1)).flatMap { d ->
            val url = "https://apis.data.go.kr/1192136/tideFcstHghLw/GetTideFcstHghLwApiService" +
                    "?serviceKey=${Http.encodeKey(key)}&obsCode=$obsCode&reqDate=${d.format(D)}" +
                    "&type=json&numOfRows=20&pageNo=1"
            parseDataGoKr(Http.get(url))
        }.sortedBy { it.time }.distinctBy { it.time }
    }

    private val PREDC = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    internal fun parseDataGoKr(body: String): List<TideEvent> {
        if (body.trimStart().startsWith("<")) {
            val msg = Regex("<returnAuthMsg>(.*?)</returnAuthMsg>|<resultMsg>(.*?)</resultMsg>").find(body)
                ?.groupValues?.drop(1)?.firstOrNull { it.isNotBlank() } ?: "알 수 없는 응답"
            error(if (msg.contains("NOT_REGISTERED")) "조석예보 활용신청이 안 됐거나 아직 승인 대기 중이에요" else msg)
        }
        val root = JSONObject(body)
        val res = root.optJSONObject("response") ?: root
        val code = res.optJSONObject("header")?.optString("resultCode") ?: "00"
        if (code != "00" && code != "0") error(res.optJSONObject("header")?.optString("resultMsg") ?: "조석예보 오류")
        val bodyObj = res.optJSONObject("body") ?: res
        val itemsAny = bodyObj.opt("items")
        val arr: JSONArray = when (itemsAny) {
            is JSONArray -> itemsAny
            is JSONObject -> itemsAny.optJSONArray("item") ?: itemsAny.optJSONObject("item")?.let { JSONArray().put(it) } ?: JSONArray()
            else -> JSONArray()
        }
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            val t = runCatching { LocalDateTime.parse(o.optString("predcDt").take(16), PREDC) }.getOrNull()
                ?: return@mapNotNull null
            val se = o.optString("extrSe").trim()
            TideEvent(t, isHigh = se == "1" || se == "3", levelCm = o.optString("predcTdlvVl").toDoubleOrNull()?.toInt())
        }
    }

    fun fetchKhoa(key: String, date: LocalDate): List<TideEvent> {
        // 앞뒤 날짜까지 받아야 자정 근처 물 흐름을 계산할 수 있다
        return listOf(date.minusDays(1), date, date.plusDays(1)).flatMap { d ->
            val url = "http://www.khoa.go.kr/api/oceangrid/tideObsPreTab/search.do" +
                    "?ServiceKey=${Http.encodeKey(key)}&ObsCode=$BUSAN_OBS_CODE" +
                    "&Date=${d.format(D)}&ResultType=json"
            parseKhoa(Http.get(url))
        }.sortedBy { it.time }
    }

    internal fun parseKhoa(body: String): List<TideEvent> {
        val result = JSONObject(body).getJSONObject("result")
        val data = result.optJSONArray("data") ?: error(result.optString("error", "조석 데이터 없음"))
        return (0 until data.length()).map { i ->
            val o = data.getJSONObject(i)
            TideEvent(
                time = LocalDateTime.parse(o.getString("tph_time"), DT),
                isHigh = o.getString("hl_code").contains("고"),
                levelCm = o.optString("tph_level").toIntOrNull(),
            )
        }
    }

    /**
     * 천문 추정치: 달이 남중한 뒤 일정 시간(고조간격) 후 만조가 온다고 가정.
     * BUSAN_HWI_MIN 은 근사값이며 실제 조석표와 30분~1시간 이상 차이 날 수 있다.
     */
    private const val BUSAN_HWI_MIN = 480
    private const val LUNAR_DAY_MIN = 1490.0   // 24시간 50분

    fun estimate(date: LocalDate, zone: ZoneId): List<TideEvent> {
        val events = mutableListOf<TideEvent>()
        for (offset in -1L..1L) {
            val day = date.plusDays(offset)
            val age = Astro.moonAge(day.atTime(12, 0).atZone(zone))
            // 동경 135도 표준시와 부산(129도)의 경도 차 보정 +24분
            val transitMin = 12 * 60 + 24 + age * (LUNAR_DAY_MIN - 1440)
            val high1 = day.atStartOfDay().plusMinutes((transitMin + BUSAN_HWI_MIN).toLong())
            val high2 = high1.plusMinutes((LUNAR_DAY_MIN / 2).toLong())
            for (h in listOf(high1, high2)) {
                events += TideEvent(h, true, null)
                events += TideEvent(h.minusMinutes((LUNAR_DAY_MIN / 4).toLong()), false, null)
            }
        }
        val from = date.atStartOfDay().minusHours(8)
        val to = date.plusDays(1).atStartOfDay().plusHours(8)
        return events.filter { it.time.isAfter(from) && it.time.isBefore(to) }
            .sortedBy { it.time }
            .distinctBy { it.time.withSecond(0).withNano(0).toString().substring(0, 15) }
    }
}
