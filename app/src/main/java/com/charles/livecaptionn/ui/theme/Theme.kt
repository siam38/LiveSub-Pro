package com.charles.livecaptionn.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// LiveSub Pro Studio Design System:
// High-contrast cinema slate & electric indigo with emerald live-signal accents.
private val LightColors = lightColorScheme(
    primary = Color(0xFF2546D0),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDCE4FF),
    onPrimaryContainer = Color(0xFF081956),
    secondary = Color(0xFF3E527E),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDCE4F8),
    onSecondaryContainer = Color(0xFF122347),
    tertiary = Color(0xFF0A7B63),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFC5EFE4),
    onTertiaryContainer = Color(0xFF032E24),
    background = Color(0xFFF4F6FB),
    onBackground = Color(0xFF111622),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF111622),
    surfaceVariant = Color(0xFFE6EAF4),
    onSurfaceVariant = Color(0xFF434A5B),
    outline = Color(0xFFBFC6D8),
    outlineVariant = Color(0xFFD8DFEC),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF94B0FF),
    onPrimary = Color(0xFF081C5B),
    primaryContainer = Color(0xFF1E3378),
    onPrimaryContainer = Color(0xFFDCE4FF),
    secondary = Color(0xFFB0C0E8),
    onSecondary = Color(0xFF19284B),
    secondaryContainer = Color(0xFF28375B),
    onSecondaryContainer = Color(0xFFDCE4F8),
    tertiary = Color(0xFF5EE0C5),
    onTertiary = Color(0xFF033026),
    tertiaryContainer = Color(0xFF0A4D3E),
    onTertiaryContainer = Color(0xFFC5EFE4),
    background = Color(0xFF0B0E14),
    onBackground = Color(0xFFE6EAF3),
    surface = Color(0xFF131822),
    onSurface = Color(0xFFE6EAF3),
    surfaceVariant = Color(0xFF1E2636),
    onSurfaceVariant = Color(0xFFA4AEC4),
    outline = Color(0xFF364054),
    outlineVariant = Color(0xFF252D3D),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC)
)

private val LiveSubTypography = Typography(
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.3).sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.2).sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.5.sp,
        lineHeight = 19.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.5.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.2.sp
    )
)

private val LiveSubShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(26.dp)
)

@Composable
fun LiveSubProTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = LiveSubTypography,
        shapes = LiveSubShapes,
        content = content
    )
}
