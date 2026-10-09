package com.dudo.fishing.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ── 바다낚시 팔레트 ──────────────────────────────
val Abyss = Color(0xFF061A2E)      // 깊은 바다 (헤더·다크 배경)
val DeepSea = Color(0xFF0B3556)    // 짙은 남색
val Tide = Color(0xFF1FB5C4)       // 물빛 청록 (기본 강조)
val Shallow = Color(0xFF5FC6C9)    // 얕은 물
val Foam = Color(0xFFF2FBFC)       // 포말
val Coral = Color(0xFFFF7A4F)      // 찌 끝 (최고 점수·포인트 강조)
val Sand = Color(0xFFF4EEE3)
val Rock = Color(0xFF5B6770)       // 갯바위 회색

// ── 두도 피싱 디자인 (짙은 남색 + 청록 포인트) ──
val Night = Color(0xFF051626)      // 화면 배경
val Panel = Color(0xFF0B2236)      // 카드
val Panel2 = Color(0xFF112E47)     // 카드 안 칸
val Line = Color(0xFF1D4260)       // 카드 테두리
val Aqua = Color(0xFF3FE0E6)       // 강조 (선택 칩·버튼)
val Mist = Color(0xFF9DB5C7)       // 보조 글자
val Gold = Color(0xFFF2C14E)       // 1위 배지·왕관

// 점수 색: 찌 색처럼 구분되도록
val Good = Color(0xFF16A06A)       // 좋음
val Mid = Color(0xFFE5A50A)        // 보통
val Bad = Color(0xFFD6493B)        // 나쁨

// 확률 색: 높음→낮음 = 파랑·초록·노랑·주황·빨강
val S1Blue = Color(0xFF2F6FD6)
val S2Green = Color(0xFF25A35A)
val S3Yellow = Color(0xFFE0B400)
val S4Orange = Color(0xFFF08A1C)
val S5Red = Color(0xFFE03B2F)
/** 위험(너울·강풍) 시간 표시용 회색 – 빨강은 비추천에 쓰므로 구분 */
val Warn = Color(0xFF6B7A86)

fun scoreColor(score: Int) = when {
    score >= 75 -> S1Blue
    score >= 62 -> S2Green
    score >= 50 -> S3Yellow
    score >= 38 -> S4Orange
    else -> S5Red
}

fun scoreLabel(score: Int) = when {
    score >= 75 -> "강력 추천"
    score >= 62 -> "좋음"
    score >= 50 -> "보통"
    score >= 38 -> "낮음"
    else -> "비추천"
}

/** 헤더 배경: 수면에서 심해로 내려가는 그라데이션 */
val SeaGradient = Brush.verticalGradient(listOf(DeepSea, Abyss))

@Composable
fun DudoTheme(content: @Composable () -> Unit) {
    // 디자인 시안처럼 항상 짙은 바다색 화면
    val scheme = darkColorScheme(
        primary = Aqua, onPrimary = Night, secondary = Coral,
        background = Night, surface = Panel, surfaceVariant = Panel2,
        onBackground = Foam, onSurface = Foam, onSurfaceVariant = Mist,
        outline = Line, outlineVariant = Line,
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
