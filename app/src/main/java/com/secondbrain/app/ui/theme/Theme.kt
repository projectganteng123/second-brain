package com.secondbrain.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary          = Lavender600,
    onPrimary        = Color.White,
    primaryContainer = Lavender50,
    onPrimaryContainer = Lavender800,
    secondary        = Mint600,
    onSecondary      = Color.White,
    secondaryContainer = Mint50,
    onSecondaryContainer = Mint800,
    tertiary         = Sky600,
    onTertiary       = Color.White,
    background       = Gray50,
    onBackground     = Lavender800,
    surface          = Color.White,
    onSurface        = Lavender800,
    surfaceVariant   = Lavender50,
    onSurfaceVariant = Gray600,
    outline          = Lavender200,
    error            = Rose600,
    onError          = Color.White
)

private val DarkColors = darkColorScheme(
    primary          = Lavender200,
    onPrimary        = Lavender800,
    primaryContainer = Lavender800,
    onPrimaryContainer = Lavender100,
    secondary        = Mint200,
    onSecondary      = Mint800,
    secondaryContainer = Mint800,
    onSecondaryContainer = Mint50,
    tertiary         = Sky200,
    onTertiary       = Sky800,
    background       = Lavender900,
    onBackground     = Lavender50,
    surface          = Color(0xFF252040),
    onSurface        = Lavender50,
    surfaceVariant   = Color(0xFF2E2850),
    onSurfaceVariant = Lavender200,
    outline          = Color(0xFF4A4470),
    error            = Rose200,
    onError          = Rose800
)

@Composable
fun SecondBrainTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        typography = Typography,
        content = content
    )
}
