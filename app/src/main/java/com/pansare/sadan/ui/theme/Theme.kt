package com.pansare.sadan.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Solarized-light inspired background with a deeper teal for navigation/chrome.
private val SolarBase3 = Color(0xFFFDF6E3)
private val SolarBase2 = Color(0xFFEEE8D5)
private val SolarBase1 = Color(0xFF93A1A1)
private val SolarBase00 = Color(0xFF657B83)
private val SolarBase01 = Color(0xFF586E75)
private val SolarBase02 = Color(0xFF073642)
private val SolarTeal = Color(0xFF2AA198)
private val SolarBlue = Color(0xFF268BD2)
private val SolarGreen = Color(0xFF859900)
private val SolarYellow = Color(0xFFB58900)
private val SolarRed = Color(0xFFDC322F)

object StatusColors {
    val paid = SolarGreen
    val partial = SolarYellow
    val unpaid = SolarRed
    val vacant = SolarBase00
}

private val SolarizedLightScheme = lightColorScheme(
    primary = SolarTeal,
    onPrimary = Color.White,
    primaryContainer = SolarBase02,
    onPrimaryContainer = SolarBase3,
    secondary = SolarBlue,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD7EAF3),
    onSecondaryContainer = SolarBase02,
    tertiary = SolarYellow,
    onTertiary = Color.White,
    error = SolarRed,
    onError = Color.White,
    errorContainer = Color(0xFFF9DAD4),
    onErrorContainer = Color(0xFF7C221E),
    background = SolarBase3,
    onBackground = SolarBase02,
    surface = Color(0xFFFFFBF0),
    onSurface = SolarBase02,
    surfaceVariant = SolarBase2,
    onSurfaceVariant = SolarBase01,
    outline = SolarBase1,
    outlineVariant = Color(0xFFD8D1BC)
)

private val AppTypography = Typography(
    headlineSmall = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, lineHeight = 32.sp),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, lineHeight = 28.sp),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, lineHeight = 24.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium)
)

/** Always light, even when Android itself is using dark mode. */
@Composable
fun SadanTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SolarizedLightScheme,
        typography = AppTypography,
        content = content
    )
}
