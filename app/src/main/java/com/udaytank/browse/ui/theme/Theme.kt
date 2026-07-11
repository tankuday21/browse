package com.udaytank.browse.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// Orbit design language: Andromeda's own palette replaces wallpaper-based
// dynamic color — the brand look is the same on every device.

private val OrbitDarkScheme = darkColorScheme(
    primary = OrbitCyan,
    onPrimary = SpaceBlack,
    primaryContainer = NebulaContainer,
    onPrimaryContainer = NebulaOnContainer,
    secondary = OrbitAccent,
    onSecondary = StarWhite,
    background = SpaceBlack,
    onBackground = StarWhite,
    surface = SpaceDeep,
    onSurface = StarWhite,
    surfaceVariant = SpaceRaised,
    onSurfaceVariant = StarDim,
    outline = SpaceOutline,
    error = OrbitError,
)

private val OrbitLightScheme = lightColorScheme(
    primary = OrbitBlue,
    onPrimary = DaySurface,
    primaryContainer = DayContainer,
    onPrimaryContainer = DayOnContainer,
    secondary = OrbitAccent,
    onSecondary = DaySurface,
    background = DayBackground,
    onBackground = InkDark,
    surface = DaySurface,
    onSurface = InkDark,
    surfaceVariant = DayVariant,
    onSurfaceVariant = InkDim,
    outline = DayOutline,
    error = OrbitError,
)

@Composable
fun BrowseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) OrbitDarkScheme else OrbitLightScheme,
        typography = Typography,
        shapes = OrbitShapes,
        content = content,
    )
}
