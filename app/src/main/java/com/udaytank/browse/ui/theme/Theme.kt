package com.udaytank.browse.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

// Orbit design language: Andromeda's own palette replaces wallpaper-based
// dynamic color — the brand look is the same on every device.

// M3 color schemes stay in sync with the active OrbitScheme so existing
// Material-based screens (which don't consume Orbit tokens directly yet)
// don't visually diverge from the new dark/light schemes: surface -> M3
// surface, accent.solid -> primary, text.primary -> onSurface.
private val OrbitDarkScheme = darkColorScheme(
    primary = darkOrbit.accent.solid,
    onPrimary = SpaceBlack,
    primaryContainer = NebulaContainer,
    onPrimaryContainer = NebulaOnContainer,
    secondary = OrbitAccentMidpoint,
    onSecondary = StarWhite,
    background = darkOrbit.surfaces.base,
    onBackground = darkOrbit.text.primary,
    surface = darkOrbit.surfaces.surface,
    onSurface = darkOrbit.text.primary,
    surfaceVariant = darkOrbit.surfaces.elevated,
    onSurfaceVariant = darkOrbit.text.secondary,
    outline = SpaceOutline,
    error = OrbitError,
)

private val OrbitLightScheme = lightColorScheme(
    primary = lightOrbit.accent.solid,
    onPrimary = DaySurface,
    primaryContainer = DayContainer,
    onPrimaryContainer = DayOnContainer,
    secondary = OrbitAccentMidpoint,
    onSecondary = DaySurface,
    background = lightOrbit.surfaces.base,
    onBackground = lightOrbit.text.primary,
    surface = lightOrbit.surfaces.surface,
    onSurface = lightOrbit.text.primary,
    surfaceVariant = lightOrbit.surfaces.elevated,
    onSurfaceVariant = lightOrbit.text.secondary,
    outline = DayOutline,
    error = OrbitError,
)

@Composable
fun BrowseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val orbitScheme = if (darkTheme) darkOrbit else lightOrbit
    CompositionLocalProvider(LocalOrbit provides orbitScheme) {
        MaterialTheme(
            colorScheme = if (darkTheme) OrbitDarkScheme else OrbitLightScheme,
            typography = Typography,
            shapes = OrbitShapes,
            content = content,
        )
    }
}
