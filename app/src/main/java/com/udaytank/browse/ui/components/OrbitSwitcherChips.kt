package com.udaytank.browse.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.udaytank.browse.data.OrbitEntity
import com.udaytank.browse.ui.theme.OrbitMotion
import com.udaytank.browse.ui.theme.OrbitRadii
import com.udaytank.browse.ui.theme.OrbitSpacing
import com.udaytank.browse.ui.theme.orbit
import com.udaytank.browse.ui.theme.orbitCaption

/**
 * Horizontally-scrolling Orbit selector for the tab switcher (v4.2). One chip per Orbit (a color
 * dot + name + open-tab count), a trailing "+" chip to quick-create a new Orbit, and an Incognito
 * chip at the end. Replaces the old Tabs/Incognito sliding-pill mode control: Orbits are now the
 * primary "which tabs am I looking at" axis, with Incognito as one more always-present option.
 *
 * Flat/tonal throughout — no hairline-bordered rectangles. Unselected chips sit on
 * `orbit().surfaces.elevated`; the selected Orbit chip fills with the Orbit's own
 * [OrbitEntity.colorArgb]; the selected Incognito chip fills with the scheme's accent solid
 * (it has no Orbit color of its own). Fill color animates via [OrbitMotion.standard], and chip
 * text/icon color is picked for contrast against whatever color is filled, so this reads correctly
 * in both light and dark regardless of which color an Orbit was given.
 */
@Composable
fun OrbitSwitcherChips(
    orbits: List<OrbitEntity>,
    activeOrbitId: Long,
    incognitoMode: Boolean,
    tabCountFor: (orbitId: Long) -> Int,
    incognitoCount: Int,
    onSelectOrbit: (Long) -> Unit,
    onSelectIncognito: () -> Unit,
    onAddOrbit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = OrbitSpacing.md, vertical = OrbitSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(OrbitSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        orbits.forEach { entity ->
            OrbitChip(
                selected = !incognitoMode && entity.id == activeOrbitId,
                fillColor = Color(entity.colorArgb),
                label = entity.name,
                count = tabCountFor(entity.id),
                onClick = { onSelectOrbit(entity.id) },
            )
        }
        AddOrbitChip(onClick = onAddOrbit)
        IncognitoChip(
            selected = incognitoMode,
            count = incognitoCount,
            onClick = onSelectIncognito,
        )
    }
}

/** One Orbit's chip: color dot + name + tab count, filling with the Orbit's own color when active. */
@Composable
private fun OrbitChip(
    selected: Boolean,
    fillColor: Color,
    label: String,
    count: Int,
    onClick: () -> Unit,
) {
    val scheme = orbit()
    val containerColor by animateColorAsState(
        targetValue = if (selected) fillColor else scheme.surfaces.elevated,
        animationSpec = OrbitMotion.standard(),
        label = "orbitChipContainer",
    )
    val onContainer = if (selected) contrastingOn(fillColor) else scheme.text.primary
    val pillShape = RoundedCornerShape(percent = OrbitRadii.pill)
    Surface(
        shape = pillShape,
        color = containerColor,
        modifier = Modifier
            .heightIn(min = 44.dp)
            .clip(pillShape)
            .clickable(onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(OrbitSpacing.xs),
            modifier = Modifier.padding(horizontal = OrbitSpacing.md, vertical = OrbitSpacing.sm),
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(fillColor)
                    // A thin ring keeps the dot legible even when the chip is filled with the
                    // same Orbit color (selected state) — otherwise dot-on-same-color vanishes.
                    .border(1.dp, onContainer.copy(alpha = 0.35f), CircleShape),
            )
            Text(
                if (count > 0) "$label ($count)" else label,
                style = orbitCaption,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = onContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Trailing "+" chip — always neutral, never a selectable state, just opens Orbit quick-create. */
@Composable
private fun AddOrbitChip(onClick: () -> Unit) {
    val scheme = orbit()
    Surface(
        shape = CircleShape,
        color = scheme.surfaces.elevated,
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Icon(
                Icons.Filled.Add,
                contentDescription = "New Orbit",
                tint = scheme.text.secondary,
            )
        }
    }
}

/** Trailing Incognito chip — fills with the scheme's accent (it has no Orbit color of its own). */
@Composable
private fun IncognitoChip(selected: Boolean, count: Int, onClick: () -> Unit) {
    val scheme = orbit()
    val containerColor by animateColorAsState(
        targetValue = if (selected) scheme.accent.solid else scheme.surfaces.elevated,
        animationSpec = OrbitMotion.standard(),
        label = "incognitoChipContainer",
    )
    val onContainer = if (selected) contrastingOn(scheme.accent.solid) else scheme.text.primary
    val pillShape = RoundedCornerShape(percent = OrbitRadii.pill)
    Surface(
        shape = pillShape,
        color = containerColor,
        modifier = Modifier
            .heightIn(min = 44.dp)
            .widthIn(min = 44.dp)
            .clip(pillShape)
            .clickable(onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(OrbitSpacing.xs),
            modifier = Modifier.padding(horizontal = OrbitSpacing.md, vertical = OrbitSpacing.sm),
        ) {
            Icon(
                Icons.Filled.VisibilityOff,
                contentDescription = null,
                tint = onContainer,
                modifier = Modifier.size(16.dp),
            )
            Text(
                if (count > 0) "Incognito ($count)" else "Incognito",
                style = orbitCaption,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = onContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Picks black or white — whichever contrasts more — for text/icons drawn on top of [bg]. */
private fun contrastingOn(bg: Color): Color = if (bg.luminance() > 0.5f) Color.Black else Color.White
