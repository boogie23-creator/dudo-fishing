package com.dudo.fishing.data

import android.content.Context
import org.json.JSONObject

object PointRepository {
    private fun prefs(context: Context) = context.getSharedPreferences("point_locations", Context.MODE_PRIVATE)

    /** 지도에서 직접 고친 포인트 위치 저장 (확인된 위치로 표시) */
    fun saveLocation(context: Context, id: String, lat: Double, lng: Double) {
        prefs(context).edit().putString(id, "%.6f,%.6f".format(java.util.Locale.US, lat, lng)).apply()
    }

    /** 지도에서 고친 위치 목록(JSON) – 카톡 등으로 보내 기본 위치 데이터에 반영할 수 있게 */
    fun exportLocations(context: Context): String {
        val o = JSONObject()
        prefs(context).all.toSortedMap().forEach { (id, v) ->
            val parts = (v as? String)?.split(",") ?: return@forEach
            val lat = parts.getOrNull(0)?.toDoubleOrNull() ?: return@forEach
            val lng = parts.getOrNull(1)?.toDoubleOrNull() ?: return@forEach
            o.put(id, org.json.JSONArray().put(lat).put(lng))
        }
        return o.toString()
    }

    fun movedCount(context: Context): Int = prefs(context).all.size

    fun resetLocation(context: Context, id: String) {
        prefs(context).edit().remove(id).apply()
    }

    fun load(context: Context): List<FishingPoint> = loadAssets(context).map { p ->
        val saved = prefs(context).getString(p.id, null)?.split(",")
        val lat = saved?.getOrNull(0)?.toDoubleOrNull()
        val lng = saved?.getOrNull(1)?.toDoubleOrNull()
        if (lat != null && lng != null) p.copy(lat = lat, lng = lng, coordVerified = true) else p
    }

    private fun loadAssets(context: Context): List<FishingPoint> {
        val text = context.assets.open("points.json").bufferedReader().use { it.readText() }
        val arr = JSONObject(text).getJSONArray("points")
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val sp = o.getJSONArray("species")
            FishingPoint(
                id = o.getString("id"),
                name = o.getString("name"),
                area = o.getString("area"),
                lat = o.getDouble("lat"),
                lng = o.getDouble("lng"),
                facingDeg = o.getInt("facingDeg"),
                depth = o.optString("depth"),
                depthMin = o.optDouble("depthMin", 0.0),
                depthMax = o.optDouble("depthMax", 0.0),
                bottom = o.optString("bottom"),
                terrain = o.optString("terrain", "평면"),
                target = o.optString("target"),
                targetDistance = o.optInt("targetDistance", 10),
                species = (0 until sp.length()).map { sp.getString(it) },
                note = o.optString("note"),
                coordVerified = o.optBoolean("coordVerified", false),
                localBias = o.optInt("localBias", 0),
                localNote = o.optString("localNote"),
                tideType = o.optString("tideType").ifBlank { null },
                altSpots = o.optJSONArray("altSpots")?.let { a ->
                    (0 until a.length()).map { j ->
                        val s = a.getJSONObject(j)
                        AltSpot(
                            tide = s.getString("tide"), label = s.getString("label"),
                            lat = s.getDouble("lat"), lng = s.getDouble("lng"), facingDeg = s.getInt("facingDeg"),
                            terrain = s.optString("terrain").ifBlank { null },
                            targetDistance = if (s.has("targetDistance")) s.getInt("targetDistance") else null,
                            target = s.optString("target").ifBlank { null },
                        )
                    }
                }.orEmpty(),
            )
        }
    }
}
