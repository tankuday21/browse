package com.udaytank.browse.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.udaytank.browse.data.OrbitEntity
import com.udaytank.browse.ui.theme.OrbitRadii
import com.udaytank.browse.ui.theme.OrbitSpacing
import com.udaytank.browse.ui.theme.orbit
import com.udaytank.browse.ui.theme.orbitBody
import com.udaytank.browse.ui.theme.orbitCaption
import com.udaytank.browse.ui.theme.orbitTitle

/**
 * Quick-switch sheet (Task 7): every Orbit at a glance — color dot, name, tab count — with the
 * active one checked. Tapping a row switches to it; a final "Manage Orbits" row hands off to the
 * Orbit management sheet (hoisted by the caller; Task 8 builds what it opens). Mirrors
 * [BrowserMenuSheet]'s tonal [ModalBottomSheet] + grouped-row styling, Orbit tokens throughout.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrbitQuickSwitchSheet(
    orbits: List<OrbitEntity>,
    activeOrbitId: Long,
    tabCountFor: (Long) -> Int,
    onSwitch: (Long) -> Unit,
    onManage: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scheme = orbit()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = scheme.surfaces.elevated,
        shape = RoundedCornerShape(topStart = OrbitRadii.bar, topEnd = OrbitRadii.bar),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding(),
        ) {
            Text(
                "Orbits",
                style = orbitTitle,
                color = scheme.text.primary,
                modifier = Modifier.padding(horizontal = OrbitSpacing.lg, vertical = OrbitSpacing.md),
            )
            orbits.forEach { entry ->
                OrbitRow(
                    entry = entry,
                    isActive = entry.id == activeOrbitId,
                    tabCount = tabCountFor(entry.id),
                    onClick = { onSwitch(entry.id) },
                )
            }
            HorizontalDivider(color = scheme.text.muted.copy(alpha = 0.15f))
            ManageOrbitsRow(onClick = onManage)
        }
    }
}

/** One Orbit row: a color dot, its name, its tab count, and a check when it's the active Orbit. */
@Composable
private fun OrbitRow(
    entry: OrbitEntity,
    isActive: Boolean,
    tabCount: Int,
    onClick: () -> Unit,
) {
    val scheme = orbit()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = OrbitSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OrbitSpacing.lg),
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .background(Color(entry.colorArgb), CircleShape),
        )
        Text(
            entry.name,
            style = orbitBody,
            color = scheme.text.primary,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        Text(
            if (tabCount == 1) "1 tab" else "$tabCount tabs",
            style = orbitCaption,
            color = scheme.text.muted,
        )
        if (isActive) {
            Icon(
                Icons.Filled.Check,
                contentDescription = "Active Orbit",
                tint = scheme.accent.solid,
                modifier = Modifier
                    .padding(start = OrbitSpacing.sm)
                    .size(20.dp),
            )
        }
    }
}

/** Final row: hands off to the Orbit management sheet (hoisted by the caller). */
@Composable
private fun ManageOrbitsRow(onClick: () -> Unit) {
    val scheme = orbit()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = OrbitSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OrbitSpacing.lg),
    ) {
        Icon(
            Icons.Filled.Tune,
            contentDescription = null,
            tint = scheme.accent.solid,
            modifier = Modifier.size(24.dp),
        )
        Text(
            "Manage Orbits",
            style = orbitBody,
            color = scheme.text.primary,
            modifier = Modifier.weight(1f),
        )
    }
}
