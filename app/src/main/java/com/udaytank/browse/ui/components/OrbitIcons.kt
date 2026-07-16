package com.udaytank.browse.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Work
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * The curated identity-icon set for Orbits (v4.2). Each Orbit stores an [OrbitIconOption.key]
 * (`OrbitEntity.iconKey`) and renders that icon on its colored avatar — a real per-identity glyph
 * (Firefox-container style) rather than a bare color dot. One consistent vector family (Material
 * filled), fixed keys so stored values stay stable across releases.
 */
data class OrbitIconOption(val key: String, val label: String, val icon: ImageVector)

val OrbitIcons: List<OrbitIconOption> = listOf(
    OrbitIconOption("person", "Personal", Icons.Filled.Person),
    OrbitIconOption("work", "Work", Icons.Filled.Work),
    OrbitIconOption("cart", "Shopping", Icons.Filled.ShoppingCart),
    OrbitIconOption("home", "Home", Icons.Filled.Home),
    OrbitIconOption("globe", "Browse", Icons.Filled.Public),
    OrbitIconOption("school", "Study", Icons.Filled.School),
    OrbitIconOption("favorite", "Social", Icons.Filled.Favorite),
    OrbitIconOption("bolt", "Focus", Icons.Filled.Bolt),
    OrbitIconOption("code", "Dev", Icons.Filled.Code),
    OrbitIconOption("music", "Media", Icons.Filled.MusicNote),
    OrbitIconOption("pets", "Pets", Icons.Filled.Pets),
    OrbitIconOption("camera", "Photos", Icons.Filled.PhotoCamera),
)

private val orbitIconByKey: Map<String, ImageVector> = OrbitIcons.associate { it.key to it.icon }

/** Resolve an Orbit's [iconKey] to its vector; unknown/legacy keys fall back to Person. */
fun orbitIcon(iconKey: String): ImageVector = orbitIconByKey[iconKey] ?: Icons.Filled.Person
