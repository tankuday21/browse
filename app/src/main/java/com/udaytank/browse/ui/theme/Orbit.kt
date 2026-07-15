package com.udaytank.browse.ui.theme

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Shared Orbit design-language primitives. */
object Orbit {
    /** The brand gradient — progress, focus glows, active accents. */
    val Gradient = Brush.horizontalGradient(listOf(OrbitBlue, OrbitCyan))

    /** Emphasized-decelerate: everything arrives, nothing bounces. */
    val Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    const val MotionMs = 300
}

val OrbitShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(24.dp),
    large = RoundedCornerShape(28.dp),
)

// ── Orbit v3.1 design tokens ───────────────────────────────────
// Single source of truth for the premium-UI redesign: spacing, radii,
// surfaces/text/accent color roles, and motion specs. Read via LocalOrbit /
// orbit() — never inline a literal dp/sp/color that belongs here.

/** Spacing scale — every layout gap/padding snaps to one of these. */
object OrbitSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}

/** Corner-radius scale — chips, cards, the bar/sheet, and pills. */
object OrbitRadii {
    val chip = 10.dp
    val card = 16.dp
    val bar = 22.dp
    const val pill = 50 // percent
}

/** Layered surfaces from the base canvas up through elevated overlays. */
data class OrbitSurfaces(val base: Color, val surface: Color, val elevated: Color)

/** Text color roles, from full-emphasis primary down to muted. */
data class OrbitText(val primary: Color, val secondary: Color, val muted: Color)

/** Brand accent — a solid for single-color use, a gradient for emphasis. */
data class OrbitAccent(val solid: Color, val gradient: List<Color>)

/** Shared motion curves — structural for sheets/bar-shrink, quick for taps/toggles. */
object OrbitMotion {
    val structural: AnimationSpec<Float> =
        spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)
    val quick: AnimationSpec<Float> = tween(120)

    /** [structural], typed for animating [Dp] values (e.g. bar height). */
    fun structuralDp(): AnimationSpec<Dp> =
        spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)

    /** [quick], typed for animating [Dp] values. */
    fun quickDp(): AnimationSpec<Dp> = tween(120)
}

/** A full color scheme: surfaces + text + accent, tagged with its brightness. */
data class OrbitScheme(
    val surfaces: OrbitSurfaces,
    val text: OrbitText,
    val accent: OrbitAccent,
    val dark: Boolean,
)

val darkOrbit = OrbitScheme(
    surfaces = OrbitSurfaces(
        base = OrbitDarkBase,
        surface = OrbitDarkSurface,
        elevated = OrbitDarkElevated,
    ),
    text = OrbitText(
        primary = OrbitDarkTextPrimary,
        secondary = OrbitDarkTextSecondary,
        muted = OrbitDarkTextMuted,
    ),
    accent = OrbitAccent(
        solid = OrbitAccentSolid,
        gradient = listOf(OrbitAccentGradientStart, OrbitAccentGradientMid, OrbitAccentGradientEnd),
    ),
    dark = true,
)

val lightOrbit = OrbitScheme(
    surfaces = OrbitSurfaces(
        base = DayBackground,
        surface = DaySurface,
        elevated = DayVariant,
    ),
    text = OrbitText(
        primary = InkDark,
        secondary = InkDim,
        muted = OrbitLightTextMuted,
    ),
    accent = OrbitAccent(
        solid = OrbitAccentSolid,
        gradient = listOf(OrbitAccentGradientStart, OrbitAccentGradientMid, OrbitAccentGradientEnd),
    ),
    dark = false,
)

/** The active [OrbitScheme], provided by [com.udaytank.browse.ui.theme.BrowseTheme]. */
val LocalOrbit: ProvidableCompositionLocal<OrbitScheme> = staticCompositionLocalOf { darkOrbit }

/** Convenience accessor: `orbit().surfaces.surface`, etc. */
@Composable
fun orbit(): OrbitScheme = LocalOrbit.current
