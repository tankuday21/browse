package com.udaytank.browse.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.udaytank.browse.BrowserViewModel
import com.udaytank.browse.data.HistoryEntry
import com.udaytank.browse.data.OrbitEntity
import com.udaytank.browse.ui.components.FaviconOrLetter
import com.udaytank.browse.ui.components.OrbitAvatar
import com.udaytank.browse.ui.components.OrbitTextField
import com.udaytank.browse.ui.components.OrbitTopBar
import com.udaytank.browse.ui.theme.OrbitSpacing
import com.udaytank.browse.ui.theme.orbit
import com.udaytank.browse.ui.theme.orbitBody
import com.udaytank.browse.ui.theme.orbitCaption
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun HistoryScreen(
    viewModel: BrowserViewModel,
    onOpenUrl: (String) -> Unit,
    onBack: () -> Unit,
) {
    val allEntries by viewModel.historyEntries.collectAsStateWithLifecycle()
    val orbits by viewModel.orbits.collectAsStateWithLifecycle()
    val activeOrbitId by viewModel.activeOrbitId.collectAsStateWithLifecycle()
    // v4.3: history is Orbit-scoped — the list, clear, and empty state all speak of "this Orbit".
    val activeOrbit = remember(orbits, activeOrbitId) { orbits.firstOrNull { it.id == activeOrbitId } }
    val scheme = orbit()
    var query by remember { mutableStateOf("") }
    var showClearConfirm by remember { mutableStateOf(false) }
    val entries = remember(allEntries, query) {
        if (query.isBlank()) allEntries
        else allEntries.filter { it.title.contains(query, true) || it.url.contains(query, true) }
    }

    val grouped = entries.groupBy { entry ->
        Instant.ofEpochMilli(entry.visitedAt).atZone(ZoneId.systemDefault()).toLocalDate()
    }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(scheme.surfaces.base)) {
                OrbitTopBar(
                    title = "History",
                    onBack = onBack,
                    actions = {
                        if (allEntries.isNotEmpty()) {
                            IconButton(onClick = { showClearConfirm = true }) {
                                Icon(
                                    Icons.Filled.DeleteSweep,
                                    contentDescription = "Clear ${activeOrbit?.name ?: "this Orbit"}'s history",
                                    tint = scheme.text.primary,
                                )
                            }
                        }
                    },
                )
                if (activeOrbit != null) OrbitScopeHeader(activeOrbit)
                OrbitTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = "Search history",
                    leadingIcon = Icons.Filled.Search,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = OrbitSpacing.lg, vertical = OrbitSpacing.xs),
                )
            }
        },
        containerColor = scheme.surfaces.base,
    ) { innerPadding ->
        if (entries.isEmpty()) {
            HistoryEmptyState(
                searching = query.isNotBlank(),
                activeOrbit = activeOrbit,
                modifier = Modifier.fillMaxSize().padding(innerPadding),
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                grouped.forEach { (date, dayEntries) ->
                    item(key = "header-$date") {
                        Text(
                            text = formatDay(date),
                            style = orbitCaption,
                            color = scheme.text.muted,
                            modifier = Modifier.padding(horizontal = OrbitSpacing.lg, vertical = OrbitSpacing.sm),
                        )
                    }
                    items(dayEntries, key = { it.id }) { entry ->
                        HistoryRow(
                            entry = entry,
                            onClick = { onOpenUrl(entry.url) },
                            onDelete = { viewModel.onDeleteHistoryEntry(entry.id) },
                        )
                    }
                }
            }
        }
    }

    if (showClearConfirm) {
        val name = activeOrbit?.name ?: "this Orbit"
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear $name history?") },
            text = { Text("This removes every page visited in $name. Other Orbits keep their history. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.onClearHistory()
                    showClearConfirm = false
                }) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("Cancel") }
            },
        )
    }
}

/**
 * A slim strip under the top bar naming whose history is on screen: the active Orbit's avatar +
 * name, closed by a hairline tinted in the Orbit's own color — the same ambient-identity cue the
 * tab switcher uses, so "this list belongs to this Orbit" reads at a glance.
 */
@Composable
private fun OrbitScopeHeader(activeOrbit: OrbitEntity) {
    val scheme = orbit()
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = OrbitSpacing.lg, vertical = OrbitSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(OrbitSpacing.sm),
        ) {
            OrbitAvatar(colorArgb = activeOrbit.colorArgb, iconKey = activeOrbit.iconKey, size = 24.dp)
            Text(
                text = "${activeOrbit.name} · history",
                style = orbitCaption,
                color = scheme.text.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(Color(activeOrbit.colorArgb).copy(alpha = 0.6f)),
        )
    }
}

@Composable
private fun HistoryRow(
    entry: HistoryEntry,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val scheme = orbit()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = OrbitSpacing.lg, vertical = OrbitSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OrbitSpacing.lg),
    ) {
        FaviconOrLetter(url = entry.url, label = entry.title, size = 36.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                entry.title,
                style = orbitBody,
                color = scheme.text.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                entry.url,
                style = orbitCaption,
                color = scheme.text.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = "Delete entry",
                tint = scheme.text.secondary,
            )
        }
    }
}

@Composable
private fun HistoryEmptyState(searching: Boolean, activeOrbit: OrbitEntity?, modifier: Modifier = Modifier) {
    val scheme = orbit()
    Column(
        modifier = modifier.padding(OrbitSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (!searching && activeOrbit != null) {
            OrbitAvatar(colorArgb = activeOrbit.colorArgb, iconKey = activeOrbit.iconKey, size = 72.dp)
        } else {
            Surface(
                shape = CircleShape,
                color = scheme.surfaces.elevated,
                modifier = Modifier.size(88.dp),
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        Icons.Filled.History,
                        contentDescription = null,
                        tint = scheme.text.secondary,
                        modifier = Modifier.size(36.dp),
                    )
                }
            }
        }
        Text(
            when {
                searching -> "No matching history"
                activeOrbit != null -> "No history in ${activeOrbit.name} yet"
                else -> "No history yet"
            },
            style = orbitBody,
            color = scheme.text.muted,
            modifier = Modifier.padding(top = OrbitSpacing.md),
        )
    }
}

private fun formatDay(date: LocalDate): String = when (date) {
    LocalDate.now() -> "Today"
    LocalDate.now().minusDays(1) -> "Yesterday"
    else -> date.format(DateTimeFormatter.ofPattern("d MMMM yyyy"))
}
