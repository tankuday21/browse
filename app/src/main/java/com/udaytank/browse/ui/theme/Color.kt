package com.udaytank.browse.ui.theme

import androidx.compose.ui.graphics.Color

// ── Orbit palette ─────────────────────────────────────────────
// Derived from the Andromeda brand mark: deep-space surfaces,
// royal-blue → cyan accent gradient.

// Brand accent
val OrbitBlue = Color(0xFF1E4FD8)
val OrbitCyan = Color(0xFF35C3F3)
val OrbitAccentMidpoint = Color(0xFF2A8BE8) // gradient midpoint, solid-use accent

// Dark (brand-default) surfaces
val SpaceBlack = Color(0xFF0B0B1C)
val SpaceDeep = Color(0xFF14142E)
val SpaceRaised = Color(0xFF232349)
val SpaceOutline = Color(0xFF3A3F5C)
val StarWhite = Color(0xFFE4E6F1)
val StarDim = Color(0xFFA9AEC7)
val NebulaContainer = Color(0xFF1E2A5E)
val NebulaOnContainer = Color(0xFFCFE0FF)

// Light surfaces
val DayBackground = Color(0xFFF7F8FD)
val DaySurface = Color(0xFFFFFFFF)
val DayVariant = Color(0xFFE8EAF6)
val DayOutline = Color(0xFFC2C7DE)
val InkDark = Color(0xFF171A2C)
val InkDim = Color(0xFF4A4F6A)
val DayContainer = Color(0xFFDCE6FF)
val DayOnContainer = Color(0xFF10307E)

val OrbitError = Color(0xFFFF6B6B)

// ── Orbit design tokens (v3.2 premium refresh) ───────────────
// Dark (signature) scheme — deeper near-OLED space surfaces, brighter premium whites.
val OrbitDarkBase = Color(0xFF070716)
val OrbitDarkSurface = Color(0xFF111228)
val OrbitDarkElevated = Color(0xFF1A1B3C)
val OrbitDarkTextPrimary = Color(0xFFF2F3FF)
val OrbitDarkTextSecondary = Color(0xFFC4C7E8)
val OrbitDarkTextMuted = Color(0xFF868AB4)

// Light scheme — inverted neutrals (reuses the existing Day*/Ink* palette
// above), same accent as dark. Only the missing "muted" tier is new.
val OrbitLightTextMuted = Color(0xFF8A8FB0)

// Accent — brand blue→cyan gradient, enriched with the cosmic violet from the hero art.
// Solid is the single-color brand blue; the 3-stop gradient is for wordmark/emphasis.
val OrbitAccentSolid = Color(0xFF2C5BE6)
val OrbitAccentGradientStart = Color(0xFF2C5BE6) // launch blue
val OrbitAccentGradientMid = Color(0xFF7A5CFF)   // cosmic violet
val OrbitAccentGradientEnd = Color(0xFF46D0F5)   // cyan
