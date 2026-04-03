package com.glassvpn.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val GlassDarkColorScheme = darkColorScheme(
    primary = CyanAccent,
    onPrimary = BackgroundDeep,
    primaryContainer = Color(0xFF003A4D),
    onPrimaryContainer = CyanAccent,
    secondary = PurpleAccent,
    onSecondary = BackgroundDeep,
    secondaryContainer = Color(0xFF2D1F5E),
    onSecondaryContainer = PurpleAccent,
    tertiary = RedAccent,
    onTertiary = BackgroundDeep,
    background = BackgroundDeep,
    onBackground = TextPrimary,
    surface = BackgroundMid,
    onSurface = TextPrimary,
    surfaceVariant = BackgroundSurface,
    onSurfaceVariant = TextSecondary,
    outline = GlassBorder,
    outlineVariant = GlassFill,
    error = RedAccent,
    onError = BackgroundDeep,
    scrim = Color(0xCC000000),
)

@Composable
fun GlassVPNTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = GlassDarkColorScheme,
        typography = GlassTypography,
        content = content
    )
}
