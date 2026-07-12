package com.udaytank.browse.ui

import android.content.Intent
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.udaytank.browse.BrowserViewModel
import com.udaytank.browse.data.DownloadEntry
import java.io.File
import java.util.Locale

private const val FILE_PROVIDER_AUTHORITY = "com.udaytank.andromeda.files"

/** Human-readable byte size, e.g. "12.3 MB". */
private fun bytesHuman(bytes: Long): String {
    if (bytes < 0) return "?"
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unitIndex = -1
    while (value >= 1024 && unitIndex < units.lastIndex) {
        value /= 1024
        unitIndex++
    }
    return String.format(Locale.US, "%.1f %s", value, units[unitIndex])
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(viewModel: BrowserViewModel, onBack: () -> Unit) {
    val entries by viewModel.downloads.collectAsStateWithLifecycle()
    var previewEntry by remember { mutableStateOf<DownloadEntry?>(null) }

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
                        onPause = { viewModel.onPauseDownload(entry.id) },
                        onResume = { viewModel.onResumeDownload(entry.id) },
                        onCancel = { viewModel.onCancelDownload(entry.id) },
                        onRetry = { viewModel.onRetryDownload(entry.id) },
                        onDelete = { viewModel.onDeleteDownload(entry.id) },
                        onOpenPreview = { previewEntry = entry },
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    previewEntry?.let { entry ->
        DownloadPreviewSheet(entry = entry, onDismiss = { previewEntry = null }, onDelete = {
            viewModel.onDeleteDownload(entry.id)
            previewEntry = null
        })
    }
}

@Composable
private fun stateChipColors(state: String) = when (state) {
    "RUNNING" -> AssistChipDefaults.assistChipColors(
        containerColor = MaterialTheme.colorScheme.primary,
        labelColor = MaterialTheme.colorScheme.onPrimary,
    )
    "FAILED" -> AssistChipDefaults.assistChipColors(
        containerColor = MaterialTheme.colorScheme.error,
        labelColor = MaterialTheme.colorScheme.onError,
    )
    "PAUSED", "SCHEDULED" -> AssistChipDefaults.assistChipColors(
        containerColor = MaterialTheme.colorScheme.secondary,
        labelColor = MaterialTheme.colorScheme.onSecondary,
    )
    else -> AssistChipDefaults.assistChipColors()
}

@Composable
private fun DownloadRow(
    entry: DownloadEntry,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
    onOpenPreview: () -> Unit,
) {
    val context = LocalContext.current
    var menuExpanded by remember { mutableStateOf(false) }

    // Speed tracking: remember last (bytes, time) sample and a rolling history of B/s samples.
    val speedSamples = remember(entry.id) { mutableStateListOf<Long>() }
    var lastSample by remember(entry.id) { mutableStateOf(entry.downloadedBytes to System.currentTimeMillis()) }
    var currentSpeed by remember(entry.id) { mutableStateOf(0L) }

    LaunchedEffect(entry.id, entry.downloadedBytes, entry.state) {
        if (entry.state == "RUNNING") {
            val (lastBytes, lastTime) = lastSample
            val now = System.currentTimeMillis()
            val dt = now - lastTime
            if (entry.downloadedBytes != lastBytes && dt > 0) {
                val speed = ((entry.downloadedBytes - lastBytes) * 1000L) / dt
                if (speed >= 0) {
                    currentSpeed = speed
                    speedSamples.add(speed)
                    if (speedSamples.size > 30) speedSamples.removeAt(0)
                }
                lastSample = entry.downloadedBytes to now
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = entry.state == "DONE") { onOpenPreview() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(entry.fileName, maxLines = 1, style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(4.dp))
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = { Text(entry.state, style = MaterialTheme.typography.labelSmall) },
                    colors = stateChipColors(entry.state),
                )
                Spacer(modifier = Modifier.height(4.dp))
                val subtitle = buildString {
                    append(bytesHuman(entry.downloadedBytes))
                    if (entry.totalBytes > 0) {
                        append(" / ")
                        append(bytesHuman(entry.totalBytes))
                    }
                    if (entry.state == "RUNNING" && currentSpeed > 0) {
                        append(" · ")
                        append(bytesHuman(currentSpeed))
                        append("/s")
                    }
                    if (entry.state == "FAILED" && entry.error != null) {
                        append(" · ")
                        append(entry.error)
                    }
                }
                Text(subtitle, style = MaterialTheme.typography.bodySmall, maxLines = 1)
            }
            if (entry.state == "RUNNING" && speedSamples.size >= 2) {
                Spacer(modifier = Modifier.size(8.dp))
                SpeedSparkline(samples = speedSamples)
            }
        }

        if (entry.state != "DONE" && entry.state != "CANCELLED") {
            Spacer(modifier = Modifier.height(4.dp))
            if (entry.totalBytes > 0) {
                LinearProgressIndicator(
                    progress = { (entry.downloadedBytes.toFloat() / entry.totalBytes.toFloat()).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }

        Row(
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            when (entry.state) {
                "RUNNING" -> {
                    IconButton(onClick = onPause) {
                        Icon(Icons.Filled.Pause, contentDescription = "Pause")
                    }
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Filled.Cancel, contentDescription = "Cancel")
                    }
                }
                "PAUSED" -> {
                    IconButton(onClick = onResume) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "Resume")
                    }
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Filled.Cancel, contentDescription = "Cancel")
                    }
                }
                "FAILED" -> {
                    IconButton(onClick = onRetry) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Retry")
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete")
                    }
                }
                "SCHEDULED" -> {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Filled.Cancel, contentDescription = "Cancel")
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete")
                    }
                }
                "PENDING" -> {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Filled.Cancel, contentDescription = "Cancel")
                    }
                }
                "DONE" -> {
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text("Share") },
                                onClick = {
                                    menuExpanded = false
                                    shareFile(context, entry)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Delete") },
                                onClick = {
                                    menuExpanded = false
                                    onDelete()
                                },
                            )
                        }
                    }
                }
                "CANCELLED" -> {
                    IconButton(onClick = onRetry) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Retry")
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete")
                    }
                }
            }
        }
    }
}

@Composable
private fun SpeedSparkline(samples: List<Long>) {
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier = Modifier.size(width = 80.dp, height = 24.dp)) {
        val max = samples.max().coerceAtLeast(1L)
        val stepX = size.width / (samples.size - 1).coerceAtLeast(1)
        val points = samples.mapIndexed { index, value ->
            val x = index * stepX
            val y = size.height - (value.toFloat() / max.toFloat()) * size.height
            Offset(x, y)
        }
        for (i in 0 until points.size - 1) {
            drawLine(
                color = color,
                start = points[i],
                end = points[i + 1],
                strokeWidth = 2f,
            )
        }
    }
}

private fun mimeOf(entry: DownloadEntry): String = entry.mimeType ?: "*/*"

private fun fileUriFor(context: android.content.Context, path: String) =
    FileProvider.getUriForFile(context, FILE_PROVIDER_AUTHORITY, File(path))

private fun shareFile(context: android.content.Context, entry: DownloadEntry) {
    val path = entry.filePath ?: return
    val file = File(path)
    if (!file.exists()) return
    val uri = fileUriFor(context, path)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mimeOf(entry)
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(Intent.createChooser(intent, "Share")) }
}

private fun openWithChooser(context: android.content.Context, entry: DownloadEntry) {
    val path = entry.filePath ?: return
    val uri = fileUriFor(context, path)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mimeOf(entry))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(Intent.createChooser(intent, "Open with")) }
}

private fun installApk(context: android.content.Context, entry: DownloadEntry) {
    val path = entry.filePath ?: return
    val uri = fileUriFor(context, path)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/vnd.android.package-archive")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(intent) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DownloadPreviewSheet(entry: DownloadEntry, onDismiss: () -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    val path = entry.filePath
    val mime = entry.mimeType ?: ""
    val fileExists = remember(path) { path != null && File(path).exists() }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(entry.fileName, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(12.dp))

            if (path == null || !fileExists) {
                Text("File no longer exists", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(12.dp))
                TextButton(onClick = onDelete) { Text("Delete") }
                return@Column
            }

            when {
                mime.startsWith("image/") -> {
                    val bitmap = remember(path) { runCatching { BitmapFactory.decodeFile(path) }.getOrNull() }
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = entry.fileName,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Text("Unable to preview image", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                mime.startsWith("text/") -> {
                    val text = remember(path) {
                        runCatching {
                            File(path).inputStream().use { stream ->
                                val buffer = ByteArray(4096)
                                val read = stream.read(buffer)
                                String(buffer, 0, read.coerceAtLeast(0))
                            }
                        }.getOrDefault("Unable to read file")
                    }
                    SelectionContainer {
                        Text(text, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                    }
                }
                mime == "application/vnd.android.package-archive" -> {
                    Button(onClick = { installApk(context, entry) }) {
                        Text("Install")
                    }
                }
                else -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { openWithChooser(context, entry) }) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.size(4.dp))
                            Text("Open with…")
                        }
                        Button(onClick = { shareFile(context, entry) }) {
                            Icon(Icons.Filled.Share, contentDescription = null)
                            Spacer(modifier = Modifier.size(4.dp))
                            Text("Share")
                        }
                    }
                }
            }
        }
    }
}
