package com.claudecode.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Teal = Color(0xFFD97757)
private val Bg = Color(0xFF0B1220)
private val Surface = Color(0xFF111A2E)
private val Surface2 = Color(0xFF16213A)

private val Scheme = darkColorScheme(
    primary = Teal,
    onPrimary = Color(0xFF06231F),
    background = Bg,
    onBackground = Color(0xFFE6EDF3),
    surface = Surface,
    onSurface = Color(0xFFE6EDF3),
    surfaceVariant = Surface2,
    onSurfaceVariant = Color(0xFF9FB0C7),
    secondary = Color(0xFF34D399),
    error = Color(0xFFF87171),
)

@Composable
fun ClaudeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}
