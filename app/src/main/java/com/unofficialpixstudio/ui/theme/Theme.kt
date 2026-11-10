package com.unofficialpixstudio.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = PixCyan,
    onPrimary = PixOnCyan,
    primaryContainer = PixCyanContainer,
    onPrimaryContainer = PixOnCyanContainer,
    secondary = PixMagenta,
    onSecondary = PixOnMagenta,
    secondaryContainer = PixMagentaContainer,
    onSecondaryContainer = PixOnMagentaContainer,
    tertiary = PixYellow,
    onTertiary = PixOnYellow,
    background = PixNight,
    onBackground = PixNightText,
    surface = PixNight,
    onSurface = PixNightText,
    surfaceVariant = PixNightSurfaceHigh,
    onSurfaceVariant = PixNightTextDim,
    surfaceContainerLowest = PixNight,
    surfaceContainerLow = PixNightSurface,
    surfaceContainer = PixNightSurface,
    surfaceContainerHigh = PixNightSurfaceHigh,
    surfaceContainerHighest = PixNightSurfaceHighest,
    outline = PixNightOutline,
    outlineVariant = PixNightSurfaceHighest,
    error = PixError,
)

private val LightColorScheme = lightColorScheme(
    primary = PixTeal,
    onPrimary = PixDaySurface,
    primaryContainer = PixTealContainer,
    onPrimaryContainer = PixOnTealContainer,
    secondary = PixRose,
    onSecondary = PixDaySurface,
    secondaryContainer = PixRoseContainer,
    onSecondaryContainer = PixOnRoseContainer,
    tertiary = PixAmber,
    onTertiary = PixDaySurface,
    background = PixDay,
    onBackground = PixDayText,
    surface = PixDay,
    onSurface = PixDayText,
    surfaceVariant = PixDaySurfaceHigh,
    onSurfaceVariant = PixDayTextDim,
    surfaceContainerLowest = PixDaySurface,
    surfaceContainerLow = PixDaySurface,
    surfaceContainer = PixDaySurface,
    surfaceContainerHigh = PixDaySurfaceHigh,
    surfaceContainerHighest = PixDaySurfaceHighest,
    outline = PixDayOutline,
    outlineVariant = PixDaySurfaceHighest,
    error = PixDayError,
)

private val PixShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/** App theme. Uses the brand palette rather than wallpaper-based dynamic colour. */
@Composable
fun UnofficialPixStudioTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        shapes = PixShapes,
        content = content,
    )
}
