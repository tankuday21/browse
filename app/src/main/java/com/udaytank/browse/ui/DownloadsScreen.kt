package com.udaytank.browse.ui

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.udaytank.browse.BrowserViewModel
import com.udaytank.browse.data.DownloadEntry
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay

private data class DlStatus(val label: String, val progress: Float?, val completed: Boolean)

private fun queryStatus(dm: DownloadManager, downloadId: Long): DlStatus {
    dm.query(DownloadManager.Query().setFilterById(downloadId)).use { cursor ->
        if (!cursor.moveToFirst()) return DlStatus("Removed", null, completed = false)
        val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
        val done = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
        val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
        return when (status) {
            DownloadManager.STATUS_SUCCESSFUL -> DlStatus("Completed", null, completed = true)
            DownloadManager.STATUS_FAILED -> DlStatus("Failed", null, completed = false)
            DownloadManager.STATUS_PAUSED -> DlStatus("Paused", null, completed = false)
            DownloadManager.STATUS_PENDING -> DlStatus("Waiting…", null, completed = false)
            else -> DlStatus(
                "Downloading…",
                if (total > 0) done.toFloat() / total else null,
                completed = false,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(viewModel: BrowserViewModel, onBack: () -> Unit) {
    val entries by viewModel.downloads.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val dm = remember { context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager }
    var statuses by remember { mutableStateOf<Map<Long, DlStatus>>(emptyMap()) }

    // Poll transfer status once a second while this screen is visible.
    LaunchedEffect(entries) {
        while (true) {
            statuses = entries.associate { it.downloadId to queryStatus(dm, it.downloadId) }
            delay(1_000)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Downloads") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        if (entries.isEmpty()) {
            Column(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(24.dp)) {
                Text("No downloads yet", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                items(entries, key = { it.id }) { entry ->
                    DownloadRow(
                        entry = entry,
                        status = statuses[entry.downloadId],
                        onOpen = {
                            dm.getUriForDownloadedFile(entry.downloadId)?.let { uri ->
                                val view = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(uri, dm.getMimeTypeForDownloadedFile(entry.downloadId))
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                runCatching { context.startActivity(view) }
                            }
                        },
                        onDelete = {
                            dm.remove(entry.downloadId)
                            viewModel.onDeleteDownload(entry.id)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadRow(
    entry: DownloadEntry,
    status: DlStatus?,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    Column {
        ListItem(
            headlineContent = { Text(entry.fileName, maxLines = 1) },
            supportingContent = { Text(status?.label ?: "…", maxLines = 1) },
            trailingContent = {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete download")
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = status?.completed == true) { onOpen() },
        )
        status?.progress?.let { progress ->
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
        }
    }
}
