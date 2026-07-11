package com.udaytank.browse.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.udaytank.browse.BrowserViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: BrowserViewModel,
    onOpenUrl: (String) -> Unit,
    onBack: () -> Unit,
) {
    val entries by viewModel.historyEntries.collectAsStateWithLifecycle()

    val grouped = entries.groupBy { entry ->
        Instant.ofEpochMilli(entry.visitedAt).atZone(ZoneId.systemDefault()).toLocalDate()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("History") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::onClearHistory) {
                        Icon(Icons.Filled.DeleteSweep, contentDescription = "Clear all history")
                    }
                },
            )
        },
    ) { innerPadding ->
        if (entries.isEmpty()) {
            Column(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(24.dp)) {
                Text("No history yet", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                grouped.forEach { (date, dayEntries) ->
                    item(key = "header-$date") {
                        Text(
                            text = formatDay(date),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    items(dayEntries, key = { it.id }) { entry ->
                        ListItem(
                            headlineContent = { Text(entry.title, maxLines = 1) },
                            supportingContent = { Text(entry.url, maxLines = 1) },
                            trailingContent = {
                                IconButton(onClick = { viewModel.onDeleteHistoryEntry(entry.id) }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Delete entry")
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenUrl(entry.url) },
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
