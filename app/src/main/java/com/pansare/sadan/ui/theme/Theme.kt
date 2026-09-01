package com.pansare.sadan.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ──────────────────────────────────────────────────────
// Color Palette — Professional property management
// ──────────────────────────────────────────────────────

val PrimaryGreen = Color(0xFF2E7D4F)
val PrimaryGreenDark = Color(0xFF1B5E3A)
val SecondaryAmber = Color(0xFFF59E0B)
val TertiaryPurple = Color(0xFF7C3AED)
val ErrorRed = Color(0xFFDC2626)

val SurfaceLight = Color(0xFFFAFAF8)
val BackgroundLight = Color(0xFFF5F5F0)
val OnSurfaceLight = Color(0xFF1A1A1A)
val OnSurfaceVariant = Color(0xFF4A4A4A)

// Status colors
val StatusPaid = Color(0xFF16A34A)
val StatusPartiallyPaid = Color(0xFFF59E0B)
val StatusUnpaid = Color(0xFFDC2626)
val StatusRegular = Color(0xFF16A34A)
val StatusDefaulter = Color(0xFFDC2626)

private val LightScheme = lightColorScheme(
    primary = PrimaryGreen,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD4EDDA),
    onPrimaryContainer = PrimaryGreenDark,
    secondary = SecondaryAmber,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFF3CD),
    onSecondaryContainer = Color(0xFF92400E),
    tertiary = TertiaryPurple,
    onTertiary = Color.White,
    error = ErrorRed,
    onError = Color.White,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    onSurfaceVariant = OnSurfaceVariant,
    background = BackgroundLight,
    onBackground = OnSurfaceLight,
    surfaceVariant = Color(0xFFEEEDE8),
    outline = Color(0xFFBBBBB0)
)

@Composable
fun PansareTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightScheme,
        typography = Typography(),
        content = content
    )
}
