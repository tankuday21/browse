package com.udaytank.browse.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.udaytank.browse.BrowserViewModel
import com.udaytank.browse.ui.components.OrbitListRow
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
    val scheme = orbit()
    var query by remember { mutableStateOf("") }
    val entries = remember(allEntries, query) {
        if (query.isBlank()) allEntries
        else allEntries.filter { it.title.contains(query, true) || it.url.contains(query, true) }
    }

    val grouped = entries.groupBy { entry ->
        Instant.ofEpochMilli(entry.visitedAt).atZone(ZoneId.systemDefault()).toLocalDate()
    }

    Scaffold(
        topBar = {
            Column {
                OrbitTopBar(
                    title = "History",
                    onBack = onBack,
                    actions = {
                        IconButton(onClick = viewModel::onClearHistory) {
                            Icon(
                                Icons.Filled.DeleteSweep,
                                contentDescription = "Clear all history",
                                tint = scheme.text.primary,
                            )
                        }
                    },
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search history") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = OrbitSpacing.lg, vertical = OrbitSpacing.xs),
                )
            }
        },
        containerColor = scheme.surfaces.base,
    ) { innerPadding ->
        if (entries.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(innerPadding).padding(OrbitSpacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    Icons.Filled.History,
                    contentDescription = null,
                    tint = scheme.text.muted,
                    modifier = Modifier.size(48.dp).padding(bottom = OrbitSpacing.md),
                )
                Text("No history yet", style = orbitBody, color = scheme.text.muted)
            }
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
                        OrbitListRow(
                            leadingIcon = null,
                            title = entry.title,
                            subtitle = entry.url,
                            onClick = { onOpenUrl(entry.url) },
                            trailing = {
                                IconButton(onClick = { viewModel.onDeleteHistoryEntry(entry.id) }) {
                                    Icon(
                                        Icons.Filled.Delete,
                                        contentDescription = "Delete entry",
                                        tint = scheme.text.secondary,
                                    )
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

private fun formatDay(date: LocalDate): String = when (date) {
    LocalDate.now() -> "Today"
    LocalDate.now().minusDays(1) -> "Yesterday"
    else -> date.format(DateTimeFormatter.ofPattern("d MMMM yyyy"))
}
