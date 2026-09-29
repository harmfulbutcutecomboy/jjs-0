package com.jjs.studio.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val BlackBackground = Color(0xFF05060A)
val DarkSurface = Color(0xFF0B0D12)
val CardBackground = Color(0xFF12141C)
val CardSurfaceVariant = Color(0xFF181B24)
val BorderSubtle = Color(0xFF242833)
val BorderMedium = Color(0xFF323746)
val BorderHighlight = Color(0xFF4A5166)

val StarkWhite = Color(0xFFF7F8FC)
val MutedGray = Color(0xFF9AA0B0)
val SubtleGray = Color(0xFF6B7285)

val JjsAccentPink = Color(0xFFF7D7F8)
val JjsAccentPurple = Color(0xFFC45EC8)
val JjsAccentGlow = Color(0x66C45EC8)
val JjsNeonGreen = Color(0xFF5DCEA6)
val JjsWarningYellow = Color(0xFFE0B25A)
val JjsDangerRed = Color(0xFFE06B6B)
val JjsCyan = Color(0xFF5EC8E8)

private val DarkColorScheme = darkColorScheme(
    primary = JjsAccentPink,
    onPrimary = BlackBackground,
    primaryContainer = CardSurfaceVariant,
    onPrimaryContainer = StarkWhite,
    secondary = JjsAccentPurple,
    onSecondary = StarkWhite,
    tertiary = JjsCyan,
    background = BlackBackground,
    onBackground = StarkWhite,
    surface = DarkSurface,
    onSurface = StarkWhite,
    surfaceVariant = CardBackground,
    onSurfaceVariant = MutedGray,
    outline = BorderSubtle,
    outlineVariant = BorderMedium,
    error = JjsDangerRed
)

private val JjsTypography = Typography(
    displayLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Black, fontSize = 34.sp, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 22.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 18.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 13.sp, color = MutedGray),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.1.sp)
)

@Composable
fun JjsStudioTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = JjsTypography,
        content = content
    )
}
