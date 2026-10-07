package com.dudo.fishing.data

import android.content.Context
import org.json.JSONObject

object PointRepository {
    private fun prefs(context: Context) = context.getSharedPreferences("point_locations", Context.MODE_PRIVATE)

    /** 지도에서 직접 고친 포인트 위치 저장 (확인된 위치로 표시) */
    fun saveLocation(context: Context, id: String, lat: Double, lng: Double) {
        prefs(context).edit().putString(id, "%.6f,%.6f".format(java.util.Locale.US, lat, lng)).apply()
    }

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
                species = (0 until sp.length()).map { sp.getString(it) },
                note = o.optString("note"),
                coordVerified = o.optBoolean("coordVerified", false),
            )
        }
    }
}
