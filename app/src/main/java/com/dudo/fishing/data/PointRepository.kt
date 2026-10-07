package com.dudo.fishing.data

import android.content.Context
import org.json.JSONObject

object PointRepository {
    fun load(context: Context): List<FishingPoint> {
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
                bottom = o.optString("bottom"),
                species = (0 until sp.length()).map { sp.getString(it) },
                note = o.optString("note"),
                coordVerified = o.optBoolean("coordVerified", false),
            )
        }
    }
}
