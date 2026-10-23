package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val TelemetryDarkColorScheme = darkColorScheme(
    primary = CyanPrecip,
    onPrimary = SlateBackground,
    primaryContainer = SlateSurfaceBright,
    onPrimaryContainer = TextPrimary,
    secondary = AmberTemp,
    onSecondary = SlateBackground,
    secondaryContainer = SlateSurfaceElevated,
    onSecondaryContainer = TextPrimary,
    tertiary = VioletWind,
    onTertiary = SlateBackground,
    background = SlateBackground,
    onBackground = TextPrimary,
    surface = SlateSurface,
    onSurface = TextPrimary,
    surfaceVariant = SlateSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    outline = SlateBorder,
    error = ScoreRed,
    onError = TextPrimary
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = TelemetryDarkColorScheme,
        typography = Typography,
        content = content
    )
}
