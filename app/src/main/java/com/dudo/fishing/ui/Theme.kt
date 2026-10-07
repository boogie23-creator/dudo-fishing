package com.dudo.fishing.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Sea = Color(0xFF0B4F6C)
val SeaLight = Color(0xFF5FA8C6)
val Good = Color(0xFF2E9E5B)
val Mid = Color(0xFFE0A100)
val Bad = Color(0xFFD1453B)

fun scoreColor(score: Int) = when {
    score >= 70 -> Good
    score >= 50 -> Mid
    else -> Bad
}

@Composable
fun DudoTheme(content: @Composable () -> Unit) {
    val scheme = if (isSystemInDarkTheme())
        darkColorScheme(primary = SeaLight, secondary = SeaLight)
    else
        lightColorScheme(primary = Sea, secondary = Sea, surfaceVariant = Color(0xFFE8F1F5))
    MaterialTheme(colorScheme = scheme, content = content)
}
