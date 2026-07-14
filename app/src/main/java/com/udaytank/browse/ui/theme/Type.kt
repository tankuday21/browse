package com.udaytank.browse.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.udaytank.browse.R

// ── Andromeda official type system (v3.2) ─────────────────────
// Space Grotesk (display) + DM Sans (body), bundled as variable fonts in res/font.
// minSdk 26 supports variable fonts: passing a FontWeight to a variable Font makes Compose
// apply the matching `wght` axis via the default variationSettings — no per-weight static files.
//
// Role split (see design spec): Display = brand/structure (wordmark, titles, section labels,
// numbers); Body = everything read (menus, settings, feed body, long text).
val Display = FontFamily(
    Font(R.font.space_grotesk, FontWeight.Medium),
    Font(R.font.space_grotesk, FontWeight.Bold),
)

val Body = FontFamily(
    Font(R.font.dm_sans, FontWeight.Normal),
    Font(R.font.dm_sans, FontWeight.Medium),
)

// Material typography — mapped onto the Andromeda families so Material-based screens match.
val Typography = Typography(
    displaySmall = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 36.sp, lineHeight = 44.sp),
    headlineSmall = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.Medium, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp),
    bodyLarge = TextStyle(fontFamily = Body, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.3.sp),
    bodyMedium = TextStyle(fontFamily = Body, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontFamily = Body, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    labelSmall = TextStyle(fontFamily = Body, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp),
)

// ── Orbit named text styles ───────────────────────────────────
val orbitDisplay = TextStyle(
    fontFamily = Display,
    fontWeight = FontWeight.Bold,
    fontSize = 30.sp,
    letterSpacing = (-0.4).sp,
)

val orbitTitle = TextStyle(
    fontFamily = Display,
    fontWeight = FontWeight.Medium,
    fontSize = 18.sp,
    letterSpacing = (-0.2).sp,
)

val orbitBody = TextStyle(
    fontFamily = Body,
    fontWeight = FontWeight.Normal,
    fontSize = 13.sp,
    lineHeight = 20.sp,
)

val orbitCaption = TextStyle(
    fontFamily = Body,
    fontWeight = FontWeight.Normal,
    fontSize = 11.sp,
    lineHeight = 15.sp,
)
