package com.dudo.fishing.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 조과 기록 저장소.
 * - assets/catches.json : 공개 조행기에서 모은 기록 (날짜·포인트·어종·마릿수)
 * - 사용자 기록 : 앱에서 "조과 기록하기"로 남긴 것 (그 시점의 바람·파고·수온을 함께 저장)
 *
 * 조행기 기록에는 그날의 날씨가 없으므로, 휴대폰에서 Open-Meteo 과거자료(무료, 키 불필요)를
 * 한 번 받아 낚시한 시간대의 평균 바람·최대 파고·평균 수온을 채우고 저장해 둔다.
 */
class HistoryRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("history", Context.MODE_PRIVATE)

    // 두도 일대 (해양 자료는 육지 격자를 피하려고 살짝 바다 쪽 좌표 사용)
    private val lat = 35.049
    private val lng = 129.015
    private val seaLat = 35.035
    private val seaLng = 129.03

    fun bundled(): List<CatchRecord> {
        val text = context.assets.open("catches.json").bufferedReader().use { it.readText() }
        val arr = JSONObject(text).getJSONArray("records")
        return (0 until arr.length()).map { fromJson(arr.getJSONObject(it), byUser = false) }
    }

    fun userRecords(): List<CatchRecord> {
        val arr = JSONArray(prefs.getString("user_records", "[]"))
        return (0 until arr.length()).map { fromJson(arr.getJSONObject(it), byUser = true) }
    }

    fun addUserRecord(r: CatchRecord) {
        val arr = JSONArray(prefs.getString("user_records", "[]"))
        arr.put(toJson(r))
        prefs.edit().putString("user_records", arr.toString()).apply()
    }

    fun deleteUserRecord(id: String) {
        val arr = JSONArray(prefs.getString("user_records", "[]"))
        val keep = JSONArray()
        for (i in 0 until arr.length()) if (arr.getJSONObject(i).optString("id") != id) keep.put(arr.getJSONObject(i))
        prefs.edit().putString("user_records", keep.toString()).apply()
    }

    /** 모든 기록. 조행기 기록은 저장된 과거 날씨가 있으면 붙이고, 없으면 받아온다(실패하면 날씨 없이 사용). */
    suspend fun all(): List<CatchRecord> = withContext(Dispatchers.IO) {
        val records = bundled()
        val missing = records.filter { cached(it.id) == null && !prefs.contains("fail_${it.id}") }
        // 날짜가 가까운 기록끼리 묶어서(60일 이내) 기간 단위로 한 번에 받는다
        clusters(missing).forEach { group -> runCatching { fetchCluster(group) } }
        records.map { r -> cached(r.id)?.let { withConditions(r, it) } ?: r } + userRecords()
    }

    private fun clusters(list: List<CatchRecord>): List<List<CatchRecord>> {
        val sorted = list.sortedBy { it.date }
        val out = mutableListOf<MutableList<CatchRecord>>()
        for (r in sorted) {
            val last = out.lastOrNull()
            if (last != null && java.time.temporal.ChronoUnit.DAYS.between(last.first().date, r.date) <= 60) last += r
            else out += mutableListOf(r)
        }
        return out
    }

    private fun cached(id: String): JSONObject? = prefs.getString("cond_$id", null)?.let { JSONObject(it) }

    private fun withConditions(r: CatchRecord, c: JSONObject) = r.copy(
        windSpeed = c.optDouble("windSpeed").takeIf { !it.isNaN() },
        windDir = c.optInt("windDir", -1).takeIf { it >= 0 },
        wave = c.optDouble("wave").takeIf { !it.isNaN() },
        waterTemp = c.optDouble("waterTemp").takeIf { !it.isNaN() },
    )

    /** Open-Meteo 과거 바람(archive) + 파고·수온(marine)을 기간 단위로 받아 각 기록의 낚시 시간대로 요약 */
    private fun fetchCluster(group: List<CatchRecord>) {
        val start = group.first().date
        val end = group.last().date
        val wx = runCatching {
            JSONObject(Http.get(
                "https://archive-api.open-meteo.com/v1/archive?latitude=$lat&longitude=$lng" +
                        "&start_date=$start&end_date=$end&hourly=wind_speed_10m,wind_direction_10m" +
                        "&wind_speed_unit=ms&timezone=Asia%2FSeoul"
            )).getJSONObject("hourly")
        }.getOrNull()
        val sea = runCatching {
            JSONObject(Http.get(
                "https://marine-api.open-meteo.com/v1/marine?latitude=$seaLat&longitude=$seaLng" +
                        "&start_date=$start&end_date=$end&hourly=wave_height,sea_surface_temperature" +
                        "&timezone=Asia%2FSeoul"
            )).getJSONObject("hourly")
        }.getOrNull()
        if (wx == null && sea == null) return

        val edit = prefs.edit()
        for (r in group) {
            val dayOffset = java.time.temporal.ChronoUnit.DAYS.between(start, r.date).toInt()
            val hours = (r.startHour until r.endHour.coerceAtLeast(r.startHour + 1)).map { dayOffset * 24 + it }
            val out = JSONObject()
            if (wx != null) {
                val ws = values(wx, "wind_speed_10m", hours)
                val wd = values(wx, "wind_direction_10m", hours)
                if (ws.isNotEmpty()) out.put("windSpeed", (ws.average() * 10).roundToInt() / 10.0)
                if (wd.isNotEmpty()) out.put("windDir", meanDirection(wd))
            }
            if (sea != null) {
                values(sea, "wave_height", hours).maxOrNull()?.let { out.put("wave", (it * 10).roundToInt() / 10.0) }
                values(sea, "sea_surface_temperature", hours).takeIf { it.isNotEmpty() }
                    ?.let { out.put("waterTemp", (it.average() * 10).roundToInt() / 10.0) }
            }
            // 자료가 없는 날(최근 며칠 등)은 표시만 해 두고 다시 요청하지 않는다
            if (out.length() > 0) edit.putString("cond_${r.id}", out.toString()) else edit.putBoolean("fail_${r.id}", true)
        }
        edit.apply()
    }

    private fun values(hourly: JSONObject, key: String, hours: List<Int>): List<Double> {
        val arr = hourly.optJSONArray(key) ?: return emptyList()
        return hours.filter { it < arr.length() && !arr.isNull(it) }.mapNotNull { h -> arr.optDouble(h).takeIf { !it.isNaN() } }
    }

    private fun meanDirection(degs: List<Double>): Int {
        val s = degs.sumOf { sin(Math.toRadians(it)) }
        val c = degs.sumOf { cos(Math.toRadians(it)) }
        return ((Math.toDegrees(atan2(s, c)) + 360) % 360).roundToInt()
    }

    private fun fromJson(o: JSONObject, byUser: Boolean): CatchRecord {
        val c = o.getJSONObject("catches")
        val rating = o.optJSONObject("rating")
        val poor = if (rating == null) emptySet() else rating.keys().asSequence().filter { k -> rating.optInt(k, 0) < 0 }.toSet()
        return CatchRecord(
            id = o.getString("id"),
            date = LocalDate.parse(o.getString("date")),
            startHour = o.optInt("startHour", 6),
            endHour = o.optInt("endHour", 12),
            pointId = o.optString("pointId").ifBlank { null },
            sideFacingDeg = if (o.has("sideFacingDeg")) o.getInt("sideFacingDeg") else null,
            catches = c.keys().asSequence().associateWith { c.getInt(it) },
            note = o.optString("note"),
            source = o.optString("source", if (byUser) "내 기록" else ""),
            url = o.optString("url").ifBlank { null },
            byUser = byUser,
            windSpeed = o.optDouble("windSpeed").takeIf { !it.isNaN() },
            windDir = o.optInt("windDir", -1).takeIf { it >= 0 },
            wave = o.optDouble("wave").takeIf { !it.isNaN() },
            waterTemp = o.optDouble("waterTemp").takeIf { !it.isNaN() },
            poorSpecies = poor,
        )
    }

    private fun toJson(r: CatchRecord) = JSONObject().apply {
        put("id", r.id); put("date", r.date.toString())
        put("startHour", r.startHour); put("endHour", r.endHour)
        r.pointId?.let { put("pointId", it) }
        r.sideFacingDeg?.let { put("sideFacingDeg", it) }
        put("catches", JSONObject(r.catches))
        put("note", r.note); put("source", r.source)
        r.windSpeed?.let { put("windSpeed", it) }
        r.windDir?.let { put("windDir", it) }
        r.wave?.let { put("wave", it) }
        r.waterTemp?.let { put("waterTemp", it) }
    }
}
