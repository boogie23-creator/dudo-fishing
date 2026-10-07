package com.dudo.fishing.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.dudo.fishing.data.FishingPoint
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import kotlin.math.cos
import kotlin.math.sin

/** Esri 위성사진 타일 (갯바위 지형이 보이도록). 출처 표기는 지도 오른쪽 아래에 나온다. */
private val EsriImagery = object : OnlineTileSourceBase(
    "EsriWorldImagery", 0, 19, 256, "",
    arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/"),
    "© Esri, Maxar, Earthstar Geographics",
) {
    override fun getTileURLString(pMapTileIndex: Long): String =
        baseUrl + MapTileIndex.getZoom(pMapTileIndex) + "/" +
                MapTileIndex.getY(pMapTileIndex) + "/" + MapTileIndex.getX(pMapTileIndex)
}

/** 지도에 표시할 포인트 하나 (점수 색으로 칠함) */
data class MapPin(val point: FishingPoint, val score: Int?)

/**
 * 두도 포인트 위성지도.
 * - 각 포인트는 번호가 적힌 원(점수 색)으로 표시
 * - 선택한 포인트는 크게, 바라보는 방향을 선으로 표시
 * - editMode 이면 지도를 탭한 곳으로 선택 포인트 위치를 옮긴다
 */
@Composable
fun PointsMap(
    pins: List<MapPin>,
    selectedId: String?,
    modifier: Modifier = Modifier,
    zoom: Double = 17.6,
    editMode: Boolean = false,
    onPinClick: (String) -> Unit = {},
    onMapTap: (Double, Double) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapView = remember {
        Configuration.getInstance().userAgentValue = context.packageName
        MapView(context).apply {
            setTileSource(EsriImagery)
            setMultiTouchControls(true)
            isTilesScaledToDpi = true
            minZoomLevel = 12.0
            maxZoomLevel = 19.5
            zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
        }
    }
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> {}
            }
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs); mapView.onDetach() }
    }
    // 처음 한 번만 화면 중심을 맞춘다 (사용자가 움직인 지도를 다시 끌어오지 않도록)
    remember(selectedId) {
        val center = pins.firstOrNull { it.point.id == selectedId }?.point
            ?: pins.map { it.point }.let { ps -> if (ps.isEmpty()) null else ps.first().copy(lat = ps.map { it.lat }.average(), lng = ps.map { it.lng }.average()) }
        center?.let { mapView.controller.setZoom(zoom); mapView.controller.setCenter(GeoPoint(it.lat, it.lng)) }
        true
    }

    AndroidView(factory = { mapView }, modifier = modifier, update = { map ->
        map.overlays.clear()
        map.overlays += MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                if (editMode) onMapTap(p.latitude, p.longitude)
                return editMode
            }
            override fun longPressHelper(p: GeoPoint): Boolean = false
        })
        val selected = pins.firstOrNull { it.point.id == selectedId }
        // 선택 포인트가 바라보는 방향 (갯바위에서 바다 쪽)
        selected?.let { s ->
            val p = s.point
            val d = 45.0
            val rad = Math.toRadians(p.facingDeg.toDouble())
            val end = GeoPoint(p.lat + d * cos(rad) / 111_320.0, p.lng + d * sin(rad) / (111_320.0 * cos(Math.toRadians(p.lat))))
            map.overlays += Polyline(map).apply {
                setPoints(listOf(GeoPoint(p.lat, p.lng), end))
                outlinePaint.color = Foam.toArgb()
                outlinePaint.strokeWidth = 6f
                outlinePaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(14f, 10f), 0f)
                title = "바라보는 방향"
            }
        }
        // 선택 안 된 포인트를 먼저, 선택 포인트를 맨 위에
        pins.sortedBy { it.point.id == selectedId }.forEach { pin ->
            val isSel = pin.point.id == selectedId
            map.overlays += Marker(map).apply {
                position = GeoPoint(pin.point.lat, pin.point.lng)
                icon = pinDrawable(context, pin.point.name.removePrefix("두도 ").removeSuffix("번"), pin.score, isSel, selectedId != null && !isSel)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                title = pin.point.name + (pin.score?.let { " · ${it}점" } ?: "")
                setOnMarkerClickListener { _, _ -> onPinClick(pin.point.id); true }
            }
        }
        map.overlays += CopyrightOverlay(context).apply { setTextColor(android.graphics.Color.WHITE) }
        map.invalidate()
    })
}

/** 번호가 적힌 원형 마커 이미지 */
private fun pinDrawable(context: Context, label: String, score: Int?, selected: Boolean, dimmed: Boolean): BitmapDrawable {
    val density = context.resources.displayMetrics.density
    val sizeDp = if (selected) 40 else 28
    val size = (sizeDp * density).toInt()
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = (score?.let { scoreColor(it) } ?: Tide).toArgb()
        alpha = if (dimmed) 170 else 255
    }
    val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = (if (selected) 3.5f else 2f) * density
        color = android.graphics.Color.WHITE
    }
    val r = size / 2f - ring.strokeWidth
    c.drawCircle(size / 2f, size / 2f, r, fill)
    c.drawCircle(size / 2f, size / 2f, r, ring)
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
        textSize = (if (label.length > 3) 9f else if (selected) 13f else 11f) * density
    }
    val y = size / 2f - (text.descent() + text.ascent()) / 2
    c.drawText(if (label == "직벽") "벽" else label, size / 2f, y, text)
    return BitmapDrawable(context.resources, bmp)
}
