package com.udaytank.browse.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Set of Material typography styles to start with
val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    )
    /* Other default text styles to override
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
    */
)

// ── Orbit v3.1 named text styles ──────────────────────────────
// Used via MaterialTheme/LocalOrbit-aware composables (later tasks); defined
// here so Task 1 establishes the full token surface up front.
val orbitDisplay = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.W800,
    fontSize = 24.sp,
    letterSpacing = (-0.3).sp,
)

val orbitTitle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.W700,
    fontSize = 17.sp,
)

val orbitBody = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.W400,
    fontSize = 13.sp,
    lineHeight = 20.sp,
)

val orbitCaption = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.W400,
    fontSize = 11.sp,
)