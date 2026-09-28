package com.jjs.studio.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val BlackBackground = Color(0xFF000000)
val DarkSurface = Color(0xFF0A0B0E)
val CardBackground = Color(0xFF101216)
val CardSurfaceVariant = Color(0xFF16181F)
val BorderSubtle = Color(0xFF1E2028)
val BorderMedium = Color(0xFF2C2F3A)
val BorderHighlight = Color(0xFF404554)

val StarkWhite = Color(0xFFFFFFFF)
val MutedGray = Color(0xFF8E929E)
val SubtleGray = Color(0xFF5B606E)

val JjsAccentPink = Color(0xFFF7D7F8)
val JjsAccentPurple = Color(0xFFC45EC8)
val JjsNeonGreen = Color(0xFF5DCEA6)
val JjsWarningYellow = Color(0xFFE0B25A)
val JjsDangerRed = Color(0xFFE06B6B)

private val DarkColorScheme = darkColorScheme(
    primary = StarkWhite,
    onPrimary = BlackBackground,
    primaryContainer = CardSurfaceVariant,
    onPrimaryContainer = StarkWhite,
    secondary = JjsAccentPink,
    onSecondary = BlackBackground,
    background = BlackBackground,
    onBackground = StarkWhite,
    surface = DarkSurface,
    onSurface = StarkWhite,
    surfaceVariant = CardBackground,
    onSurfaceVariant = MutedGray,
    outline = BorderSubtle,
    outlineVariant = BorderMedium
)

@Composable
fun JjsStudioTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
