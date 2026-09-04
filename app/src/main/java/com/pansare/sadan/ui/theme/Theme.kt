package com.pansare.sadan.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// A restrained, business-like palette. Green reads as "settled", amber as "attention",
// red as "owing" — but every status is also spelled out in words in the UI.

private val Green = Color(0xFF2E7D4F)
private val GreenDark = Color(0xFF1B5E3A)
private val GreenLight = Color(0xFF7FCFA0)
private val Amber = Color(0xFFB26A00)
private val Red = Color(0xFFC0392B)
private val Slate = Color(0xFF5A6470)

object StatusColors {
    val paid = Color(0xFF1E7B45)
    val partial = Color(0xFFB26A00)
    val unpaid = Color(0xFFC0392B)
    val vacant = Color(0xFF6B7280)
}

private val LightScheme = lightColorScheme(
    primary = Green,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3EEDD),
    onPrimaryContainer = GreenDark,
    secondary = Slate,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE2E6EB),
    onSecondaryContainer = Color(0xFF2A313A),
    tertiary = Amber,
    onTertiary = Color.White,
    error = Red,
    onError = Color.White,
    errorContainer = Color(0xFFFBE1DE),
    onErrorContainer = Color(0xFF7A1F16),
    background = Color(0xFFF7F8F7),
    onBackground = Color(0xFF1A1C1A),
    surface = Color.White,
    onSurface = Color(0xFF1A1C1A),
    surfaceVariant = Color(0xFFEDEFEE),
    onSurfaceVariant = Color(0xFF4A5158),
    outline = Color(0xFFB9BFC4),
    outlineVariant = Color(0xFFDCE0E3)
)

private val DarkScheme = darkColorScheme(
    primary = GreenLight,
    onPrimary = Color(0xFF00391F),
    primaryContainer = Color(0xFF1B5E3A),
    onPrimaryContainer = Color(0xFFD3EEDD),
    secondary = Color(0xFFB6C0CB),
    onSecondary = Color(0xFF212A33),
    tertiary = Color(0xFFF2B65C),
    onTertiary = Color(0xFF432C00),
    error = Color(0xFFF2B8B2),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C2A20),
    onErrorContainer = Color(0xFFFBE1DE),
    background = Color(0xFF12140F),
    onBackground = Color(0xFFE3E3DE),
    surface = Color(0xFF1A1C19),
    onSurface = Color(0xFFE3E3DE),
    surfaceVariant = Color(0xFF424940),
    onSurfaceVariant = Color(0xFFC2C9BE),
    outline = Color(0xFF8C9389)
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

@Composable
fun SadanTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = AppTypography,
        content = content
    )
}
