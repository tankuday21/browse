package com.udaytank.browse.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.graphics.Brush
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
