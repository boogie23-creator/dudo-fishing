package com.dudo.fishing.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/**
 * 섬이 떠 있는 바다 풍경 (사진 대신 그린 그림).
 * seed 마다 섬 모양이 조금씩 달라서 포인트별 썸네일로도 쓴다.
 * dusk = true 면 해질녘 하늘.
 */
@Composable
fun SeaScene(modifier: Modifier = Modifier, seed: Int = 0, dusk: Boolean = false, islandScale: Float = 1f) {
    Canvas(modifier) { drawSeaScene(seed, dusk, islandScale) }
}

fun DrawScope.drawSeaScene(seed: Int, dusk: Boolean, islandScale: Float) {
    val w = size.width; val h = size.height
    val horizon = h * 0.58f
    // 하늘
    drawRect(
        Brush.verticalGradient(
            if (dusk) listOf(Color(0xFF1B3557), Color(0xFF4F6E92), Color(0xFFE7B98A))
            else listOf(Color(0xFF2B5E8C), Color(0xFF6FA6CF), Color(0xFFCFE6F2)),
            endY = horizon
        ), size = Size(w, horizon)
    )
    // 먼 구름
    val cloud = Color.White.copy(alpha = if (dusk) 0.18f else 0.35f)
    drawOval(cloud, Offset(w * 0.05f, horizon * 0.25f), Size(w * 0.35f, horizon * 0.10f))
    drawOval(cloud, Offset(w * 0.55f, horizon * 0.15f), Size(w * 0.30f, horizon * 0.08f))
    // 바다
    drawRect(
        Brush.verticalGradient(listOf(Color(0xFF1F6F94), Color(0xFF0C3B5C), Color(0xFF06223A)), startY = horizon, endY = h),
        Offset(0f, horizon), Size(w, h - horizon)
    )
    // 먼 섬 (옅게)
    island(w * 0.78f, horizon, w * 0.22f * islandScale, h * 0.10f * islandScale, seed + 7, Color(0xFF3E5F6E).copy(alpha = 0.8f))
    // 본섬
    island(w * 0.42f, horizon + h * 0.02f, w * 0.34f * islandScale, h * 0.24f * islandScale, seed, Color(0xFF1F3B2C))
    // 갯바위 (섬 앞 바위)
    val rock = Color(0xFF2C3438)
    drawOval(rock, Offset(w * 0.12f, horizon + h * 0.03f), Size(w * 0.14f, h * 0.05f))
    drawOval(rock, Offset(w * 0.64f, horizon + h * 0.05f), Size(w * 0.10f, h * 0.04f))
    // 물결 반짝임
    val glint = Color.White.copy(alpha = 0.22f)
    var y = horizon + h * 0.10f
    var i = 0
    while (y < h) {
        val x0 = ((i * 37 + seed * 13) % 100) / 100f * w
        drawLine(glint, Offset(x0, y), Offset(x0 + w * 0.12f, y), strokeWidth = 1.2f)
        drawLine(glint, Offset((x0 + w * 0.45f) % w, y + 4f), Offset((x0 + w * 0.53f) % w, y + 4f), strokeWidth = 1f)
        y += h * 0.07f; i++
    }
}

/** 둥근 봉우리가 2~3개 있는 섬 실루엣 + 아래 하얀 포말 */
private fun DrawScope.island(cx: Float, base: Float, halfW: Float, height: Float, seed: Int, color: Color) {
    val p = Path()
    p.moveTo(cx - halfW, base)
    val n = 40
    for (k in 0..n) {
        val t = k / n.toFloat()
        val x = cx - halfW + 2 * halfW * t
        val bump = sin(PI * t).toFloat()
        val ripple = 0.18f * sin(2 * PI * (t * (2.2 + (seed % 3) * 0.4) + seed * 0.37)).toFloat()
        p.lineTo(x, base - height * (bump * (0.85f + ripple)).coerceAtLeast(0f))
    }
    p.lineTo(cx + halfW, base); p.close()
    drawPath(p, color)
    drawLine(Color.White.copy(alpha = 0.55f), Offset(cx - halfW * 0.95f, base), Offset(cx + halfW * 0.95f, base), strokeWidth = 2.5f)
}

/** 어종별 물고기 그림 색 (몸통, 등) */
fun fishColors(label: String): Pair<Color, Color> = when (label) {
    "감성돔" -> Color(0xFFB9C3CC) to Color(0xFF5E6B77)
    "벵에돔" -> Color(0xFF4A5E78) to Color(0xFF1E2C3D)
    "참돔" -> Color(0xFFF08C80) to Color(0xFFC9473F)
    "볼락" -> Color(0xFFB08A63) to Color(0xFF6B4E33)
    "농어" -> Color(0xFFD7DEE4) to Color(0xFF6F8796)
    "무늬오징어" -> Color(0xFFE9D9C9) to Color(0xFFA7836A)
    else -> Color(0xFFB9C3CC) to Color(0xFF5E6B77)
}

/** 작은 물고기(또는 오징어) 아이콘 */
@Composable
fun FishIcon(label: String, size: Dp = 18.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size * 1.4f, size)) { drawFish(label) }
}

fun DrawScope.drawFish(label: String, w: Float = size.width, h: Float = size.height) {
    val (body, back) = fishColors(label)
    if (label == "무늬오징어") {
        // 오징어: 몸통 + 지느러미 + 다리
        drawOval(body, Offset(w * 0.30f, h * 0.30f), Size(w * 0.55f, h * 0.40f))
        drawOval(back.copy(alpha = 0.8f), Offset(w * 0.25f, h * 0.22f), Size(w * 0.65f, h * 0.56f), style = Stroke(1.2f))
        for (k in 0..3) {
            val yy = h * (0.38f + k * 0.08f)
            drawLine(back, Offset(w * 0.32f, yy), Offset(w * 0.04f, yy + (k - 1.5f) * h * 0.06f), strokeWidth = h * 0.06f)
        }
        drawCircle(Color.Black, h * 0.05f, Offset(w * 0.40f, h * 0.45f))
        return
    }
    // 꼬리
    val tail = Path().apply {
        moveTo(w * 0.80f, h * 0.50f); lineTo(w * 1.0f, h * 0.18f); lineTo(w * 0.94f, h * 0.50f); lineTo(w * 1.0f, h * 0.82f); close()
    }
    drawPath(tail, back)
    // 몸통
    drawOval(Brush.verticalGradient(listOf(back, body, body.copy(alpha = 0.9f)), startY = h * 0.15f, endY = h * 0.85f),
        Offset(w * 0.02f, h * 0.15f), Size(w * 0.84f, h * 0.70f))
    // 등지느러미
    val fin = Path().apply { moveTo(w * 0.30f, h * 0.20f); lineTo(w * 0.45f, h * 0.02f); lineTo(w * 0.66f, h * 0.22f); close() }
    drawPath(fin, back)
    // 눈
    drawCircle(Color.White, h * 0.09f, Offset(w * 0.18f, h * 0.42f))
    drawCircle(Color.Black, h * 0.05f, Offset(w * 0.18f, h * 0.42f))
    if (label == "벵에돔" || label == "참돔") {
        // 비늘 무늬
        drawLine(Color.White.copy(alpha = 0.25f), Offset(w * 0.30f, h * 0.55f), Offset(w * 0.75f, h * 0.55f), strokeWidth = 1f)
    }
}

/** 큰 물고기 그림 (조황 기록 썸네일) – 물빛 배경 위 */
@Composable
fun FishThumb(label: String, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        drawRect(Brush.verticalGradient(listOf(Color(0xFF34404A), Color(0xFF1B2329))))
        // 바위 질감
        drawCircle(Color.White.copy(alpha = 0.04f), size.minDimension * 0.6f, Offset(size.width * 0.2f, size.height * 0.1f))
        val fw = size.width * 0.8f; val fh = fw / 1.9f
        translate((size.width - fw) / 2, (size.height - fh) / 2) { drawFish(label, fw, fh) }
    }
}

/** '오늘의 입질 지수' 카드 배경: 물속 + 물고기 떼 */
fun DrawScope.drawFishSchool() {
    val w = size.width; val h = size.height
    drawRect(Brush.linearGradient(listOf(Color(0xFF0B5A73), Color(0xFF0A3550), Color(0xFF07243A)), Offset(0f, 0f), Offset(w, h)))
    // 빛 내림
    drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.12f), Color.Transparent), endY = h * 0.6f))
    val fish = Color(0xFF9FD8E6).copy(alpha = 0.18f)
    val spots = listOf(0.62f to 0.18f, 0.78f to 0.30f, 0.70f to 0.52f, 0.88f to 0.12f, 0.92f to 0.48f, 0.56f to 0.70f, 0.84f to 0.74f, 0.47f to 0.35f)
    spots.forEachIndexed { i, (fx, fy) ->
        val fw = w * (0.10f + (i % 3) * 0.025f); val fh = fw * 0.42f
        val x = w * fx; val y = h * fy
        drawOval(fish, Offset(x, y), Size(fw, fh))
        val t = Path().apply { moveTo(x + fw * 0.95f, y + fh / 2); lineTo(x + fw * 1.25f, y); lineTo(x + fw * 1.25f, y + fh); close() }
        drawPath(t, fish)
    }
    // 거품
    for (k in 0..6) drawCircle(Color.White.copy(alpha = 0.10f), 2f + k % 3 * 1.5f, Offset(w * (0.5f + k * 0.07f), h * (0.9f - k * 0.11f)))
}
