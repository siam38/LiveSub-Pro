package com.charles.livecaptionn.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// LiveSub Pro theme: deep indigo on clean slate neutrals. Replaces the stock
// Material baseline (the generic lavender look) everywhere in the app.
private val LightColors = lightColorScheme(
    primary = Color(0xFF2E4BD7),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDCE4FF),
    onPrimaryContainer = Color(0xFF0A1B5C),
    secondary = Color(0xFF4A5B8C),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDCE4F7),
    onSecondaryContainer = Color(0xFF16264D),
    tertiary = Color(0xFF0E7C66),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFC9EDE4),
    onTertiaryContainer = Color(0xFF043028),
    background = Color(0xFFF6F7FB),
    onBackground = Color(0xFF14181F),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF14181F),
    surfaceVariant = Color(0xFFE7EAF3),
    onSurfaceVariant = Color(0xFF454B59),
    outline = Color(0xFFC3C9D9),
    outlineVariant = Color(0xFFD8DCE8),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9DB4FF),
    onPrimary = Color(0xFF0A1E5E),
    primaryContainer = Color(0xFF24377A),
    onPrimaryContainer = Color(0xFFDCE4FF),
    secondary = Color(0xFFB3C2E8),
    onSecondary = Color(0xFF1B2A4D),
    secondaryContainer = Color(0xFF2C3A5F),
    onSecondaryContainer = Color(0xFFDCE4F7),
    tertiary = Color(0xFF6FD3BC),
    onTertiary = Color(0xFF043028),
    tertiaryContainer = Color(0xFF0B4A3C),
    onTertiaryContainer = Color(0xFFC9EDE4),
    background = Color(0xFF0D1117),
    onBackground = Color(0xFFE6EAF2),
    surface = Color(0xFF161B24),
    onSurface = Color(0xFFE6EAF2),
    surfaceVariant = Color(0xFF232A38),
    onSurfaceVariant = Color(0xFFA7B0C2),
    outline = Color(0xFF3A4356),
    outlineVariant = Color(0xFF2A3140),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC)
)

@Composable
fun LiveSubProTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
