package com.dudo.fishing.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ── 바다낚시 팔레트 ──────────────────────────────
val Abyss = Color(0xFF061A2E)      // 깊은 바다 (헤더·다크 배경)
val DeepSea = Color(0xFF0B3556)    // 짙은 남색
val Tide = Color(0xFF0E7C8C)       // 물빛 청록 (기본 강조)
val Shallow = Color(0xFF5FC6C9)    // 얕은 물
val Foam = Color(0xFFF2FBFC)       // 포말
val Coral = Color(0xFFFF7A4F)      // 찌 끝 (최고 점수·포인트 강조)
val Sand = Color(0xFFF4EEE3)
val Rock = Color(0xFF5B6770)       // 갯바위 회색

// 점수 색: 찌 색처럼 구분되도록
val Good = Color(0xFF16A06A)       // 좋음
val Mid = Color(0xFFE5A50A)        // 보통
val Bad = Color(0xFFD6493B)        // 나쁨

fun scoreColor(score: Int) = when {
    score >= 75 -> Coral
    score >= 62 -> Good
    score >= 45 -> Mid
    else -> Bad
}

fun scoreLabel(score: Int) = when {
    score >= 75 -> "강력 추천"
    score >= 62 -> "좋음"
    score >= 45 -> "보통"
    else -> "비추천"
}

/** 헤더 배경: 수면에서 심해로 내려가는 그라데이션 */
val SeaGradient = Brush.verticalGradient(listOf(DeepSea, Abyss))

@Composable
fun DudoTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val scheme = if (dark)
        darkColorScheme(
            primary = Shallow, onPrimary = Abyss, secondary = Coral,
            background = Abyss, surface = Color(0xFF0C2740), surfaceVariant = Color(0xFF123452),
            onBackground = Foam, onSurface = Foam, onSurfaceVariant = Color(0xFFB7CEDB),
        )
    else
        lightColorScheme(
            primary = Tide, onPrimary = Color.White, secondary = Coral,
            background = Color(0xFFEFF5F8), surface = Color.White, surfaceVariant = Color(0xFFE2EEF3),
            onBackground = Abyss, onSurface = Abyss, onSurfaceVariant = Rock,
        )
    val base = Typography()
    val type = base.copy(
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.Bold),
        labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.3.sp),
    )
    MaterialTheme(colorScheme = scheme, typography = type, content = content)
}
