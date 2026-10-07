package com.dudo.fishing.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Phishing
import androidx.compose.material.icons.filled.SetMeal
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dudo.fishing.data.TideEvent
import com.dudo.fishing.scoring.Species
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** 심해 그라데이션 위에 아래쪽이 파도 모양으로 끝나는 헤더 */
@Composable
fun WaveHeader(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val bg = MaterialTheme.colorScheme.background
    Box(modifier.fillMaxWidth().background(SeaGradient)) {
        content()
        Canvas(Modifier.fillMaxWidth().height(22.dp).align(Alignment.BottomCenter)) {
            // 뒤쪽 옅은 파도 + 앞쪽 배경색 파도 → 화면 본문으로 자연스럽게 이어짐
            fun wave(phase: Float, amp: Float, color: Color) {
                val p = Path().apply {
                    moveTo(0f, size.height)
                    var x = 0f
                    while (x <= size.width) {
                        lineTo(x, size.height * 0.55f + amp * sin((x / size.width) * 2 * PI.toFloat() * 2.2f + phase))
                        x += 6f
                    }
                    lineTo(size.width, size.height); close()
                }
                drawPath(p, color)
            }
            wave(1.2f, size.height * 0.25f, Shallow.copy(alpha = 0.35f))
            wave(0f, size.height * 0.22f, bg)
        }
    }
}

/**
 * 하루 물때 곡선. 만조·간조 시각을 이어 부드러운 곡선으로 그리고,
 * 오늘이면 지금 시각을 찌 색 점으로 표시한다.
 */
@Composable
fun TideChart(tides: List<TideEvent>, date: LocalDate, now: LocalDateTime?, modifier: Modifier = Modifier, light: Boolean = true) {
    val lineColor = if (light) Foam else Tide
    val labelColor = if (light) Foam.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant
    val start = date.atStartOfDay()
    val dayTides = tides.filter { it.time.toLocalDate() == date }
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(70.dp)) {
            if (tides.size < 2) return@Canvas
            fun level(t: LocalDateTime): Float {
                val prev = tides.lastOrNull { !it.time.isAfter(t) } ?: return 0f
                val next = tides.firstOrNull { it.time.isAfter(t) } ?: return if (prev.isHigh) 1f else -1f
                val pv = if (prev.isHigh) 1f else -1f
                val nv = if (next.isHigh) 1f else -1f
                val f = ChronoUnit.MINUTES.between(prev.time, t).toFloat() / ChronoUnit.MINUTES.between(prev.time, next.time).coerceAtLeast(1)
                return (pv + nv) / 2 + (pv - nv) / 2 * cos(PI.toFloat() * f)
            }
            val w = size.width; val h = size.height
            fun y(v: Float) = h * 0.5f - v * h * 0.38f
            val path = Path(); val fill = Path()
            for (m in 0..1440 step 10) {
                val x = w * m / 1440f
                val yy = y(level(start.plusMinutes(m.toLong())))
                if (m == 0) { path.moveTo(x, yy); fill.moveTo(x, h); fill.lineTo(x, yy) } else { path.lineTo(x, yy); fill.lineTo(x, yy) }
            }
            fill.lineTo(w, h); fill.close()
            drawPath(fill, Brush.verticalGradient(listOf(lineColor.copy(alpha = 0.35f), Color.Transparent)))
            drawPath(path, lineColor, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
            // 6시간 눈금
            for (hh in listOf(6, 12, 18)) {
                val x = w * hh / 24f
                drawLine(lineColor.copy(alpha = 0.18f), Offset(x, 0f), Offset(x, h), strokeWidth = 1.dp.toPx())
            }
            // 만조·간조 점
            dayTides.forEach { t ->
                val m = ChronoUnit.MINUTES.between(start, t.time).toFloat()
                drawCircle(if (t.isHigh) Shallow else lineColor.copy(alpha = 0.6f), 3.5.dp.toPx(), Offset(w * m / 1440f, y(if (t.isHigh) 1f else -1f)))
            }
            // 지금
            if (now != null && now.toLocalDate() == date) {
                val m = ChronoUnit.MINUTES.between(start, now).toFloat()
                val c = Offset(w * m / 1440f, y(level(now)))
                drawCircle(Color.White, 6.dp.toPx(), c)
                drawCircle(Coral, 4.dp.toPx(), c)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("0시", "6시", "12시", "18시", "24시").forEach { Text(it, color = labelColor, fontSize = 10.sp) }
        }
    }
}

/** 원형 점수 게이지 (입질 지수) */
@Composable
fun ScoreGauge(score: Int, size: Dp = 56.dp, stroke: Dp = 6.dp, showLabel: Boolean = false) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val color = scoreColor(score)
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val s = stroke.toPx()
            val arcSize = Size(this.size.width - s, this.size.height - s)
            val topLeft = Offset(s / 2, s / 2)
            drawArc(track, 135f, 270f, false, topLeft, arcSize, style = Stroke(s, cap = StrokeCap.Round))
            drawArc(color, 135f, 270f * score / 100f, false, topLeft, arcSize, style = Stroke(s, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$score", fontWeight = FontWeight.ExtraBold, fontSize = (size.value / 2.9).sp, color = MaterialTheme.colorScheme.onSurface)
            if (showLabel) Text(scoreLabel(score), fontSize = 10.sp, color = color, fontWeight = FontWeight.Bold)
        }
    }
}

/** 바람 화살표: 바람이 불어가는 쪽을 가리킨다 */
@Composable
fun WindArrow(fromDeg: Int, tint: Color, size: Dp = 16.dp) {
    Icon(Icons.Default.Navigation, "바람", tint = tint, modifier = Modifier.size(size).rotate((fromDeg + 180f) % 360f))
}

fun speciesIcon(s: Species): ImageVector = when (s) {
    Species.MUNUI -> Icons.Default.Phishing
    Species.BOLLAK -> Icons.Default.NightsStay
    else -> Icons.Default.SetMeal
}

/** 헤더 위 반투명 정보 칸 */
@Composable
fun GlassStat(label: String, value: String, modifier: Modifier = Modifier, icon: @Composable (() -> Unit)? = null) {
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.10f)).padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(label, color = Foam.copy(alpha = 0.7f), fontSize = 11.sp)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            icon?.invoke()
            Text(value, color = Foam, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
    }
}
