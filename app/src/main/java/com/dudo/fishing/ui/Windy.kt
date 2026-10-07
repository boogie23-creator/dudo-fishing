package com.dudo.fishing.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.MotionEvent
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Air
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import java.util.Locale

/** 윈디에서 보여줄 레이어 */
enum class WindyLayer(val label: String, val key: String) {
    WIND("바람", "wind"),
    WAVES("파도", "waves"),
    SWELL("너울", "swell1"),
    GUST("돌풍", "gust"),
}

private fun f(v: Double) = String.format(Locale.US, "%.4f", v)

/** 윈디 앱(설치돼 있으면) 또는 웹으로 해당 위치를 연다 */
fun openWindy(context: Context, lat: Double, lng: Double, layer: WindyLayer = WindyLayer.WIND) {
    val url = "https://www.windy.com/${f(lat)}/${f(lng)}?${layer.key},${f(lat)},${f(lng)},14"
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

/** 윈디 임베드 지도 주소 (무료 임베드, 단위는 m/s·°C) */
private fun embedUrl(lat: Double, lng: Double, layer: WindyLayer, zoom: Int) =
    "https://embed.windy.com/embed2.html?lat=${f(lat)}&lon=${f(lng)}&detailLat=${f(lat)}&detailLon=${f(lng)}" +
            "&zoom=$zoom&level=surface&overlay=${layer.key}&product=ecmwf&menu=&message=true&marker=true" +
            "&calendar=now&pressure=&type=map&location=coordinates&detail=&metricWind=m%2Fs&metricTemp=%C2%B0C&radarRange=-1"

/**
 * 앱 안에 넣는 윈디 지도. 바람·파도·너울·돌풍 레이어를 바꿔 볼 수 있다.
 * 보기 전용이며 점수 계산에는 쓰지 않는다.
 */
@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
fun WindyCard(lat: Double, lng: Double, modifier: Modifier = Modifier, height: Int = 380, zoom: Int = 10) {
    val context = LocalContext.current
    var layer by remember { mutableStateOf(WindyLayer.WIND) }
    Card(
        modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Air, null, tint = Tide, modifier = Modifier.size(20.dp))
                Text(" 바람·파도 흐름", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text("Windy", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                WindyLayer.entries.forEach { l ->
                    val sel = l == layer
                    Text(
                        l.label, fontSize = 13.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                        color = if (sel) Color.White else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.clip(RoundedCornerShape(50))
                            .background(if (sel) Tide else MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { layer = l }.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        webViewClient = WebViewClient()
                        webChromeClient = WebChromeClient()
                        // 지도를 손가락으로 움직일 때 화면 전체가 같이 스크롤되지 않도록
                        setOnTouchListener { v, e ->
                            if (e.action == MotionEvent.ACTION_DOWN || e.action == MotionEvent.ACTION_MOVE)
                                v.parent?.requestDisallowInterceptTouchEvent(true)
                            false
                        }
                        tag = ""
                    }
                },
                update = { web ->
                    val url = embedUrl(lat, lng, layer, zoom)
                    if (web.tag != url) { web.tag = url; web.loadUrl(url) }
                },
                modifier = Modifier.fillMaxWidth().height(height.dp).clip(RoundedCornerShape(14.dp))
            )
            WindyButton(lat, lng, layer)
        }
    }
}

/** "윈디에서 보기" 버튼 */
@Composable
fun WindyButton(lat: Double, lng: Double, layer: WindyLayer = WindyLayer.WIND, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(DeepSea)
            .clickable { openWindy(context, lat, lng, layer) }.padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.AutoMirrored.Filled.OpenInNew, null, tint = Foam, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text("윈디에서 보기", color = Foam, fontWeight = FontWeight.Bold)
    }
}
