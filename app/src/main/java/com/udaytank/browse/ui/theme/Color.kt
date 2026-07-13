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

// ── Orbit v3.1 design tokens (premium UI redesign) ───────────
// Dark (signature) scheme — distinct base/surface/elevated layers.
val OrbitDarkBase = Color(0xFF08081C)
val OrbitDarkSurface = Color(0xFF12122E)
val OrbitDarkElevated = Color(0xFF181840)
val OrbitDarkTextPrimary = Color(0xFFE6E8F5)
val OrbitDarkTextSecondary = Color(0xFFC6C8E0)
val OrbitDarkTextMuted = Color(0xFF8A8CB5)

// Light scheme — inverted neutrals (reuses the existing Day*/Ink* palette
// above), same accent as dark. Only the missing "muted" tier is new.
val OrbitLightTextMuted = Color(0xFF8A8FB0)

// Accent (brand gradient) — shared by both schemes.
val OrbitAccentSolid = Color(0xFF1E4FD8)
val OrbitAccentGradientStart = Color(0xFF1E4FD8)
val OrbitAccentGradientEnd = Color(0xFF35C3F3)
