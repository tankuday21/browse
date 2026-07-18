package com.udaytank.browse.ui

import android.app.DownloadManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.udaytank.browse.BrowserViewModel
import com.udaytank.browse.data.DownloadEntry
import com.udaytank.browse.ui.components.FaviconOrLetter
import com.udaytank.browse.ui.components.OrbitTopBar
import com.udaytank.browse.ui.theme.OrbitRadii
import com.udaytank.browse.ui.theme.OrbitSpacing
import com.udaytank.browse.ui.theme.orbit
import com.udaytank.browse.ui.theme.orbitBody
import com.udaytank.browse.ui.theme.orbitCaption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale


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
    val scheme = orbit()
    var previewEntry by remember { mutableStateOf<DownloadEntry?>(null) }

    Scaffold(
        topBar = { OrbitTopBar(title = "Downloads", onBack = onBack) },
        containerColor = scheme.surfaces.base,
    ) { innerPadding ->
        if (entries.isEmpty()) {
            DownloadsEmptyState(modifier = Modifier.fillMaxSize().padding(innerPadding))
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
                    // Flat separation: a whisper-thin low-alpha rule instead of a hairline outline.
                    HorizontalDivider(color = scheme.text.muted.copy(alpha = 0.08f))
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
private fun DownloadsEmptyState(modifier: Modifier = Modifier) {
    val scheme = orbit()
    Column(
        modifier = modifier.padding(OrbitSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            shape = CircleShape,
            color = scheme.surfaces.elevated,
            modifier = Modifier.size(88.dp),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    Icons.Filled.Download,
                    contentDescription = null,
                    tint = scheme.text.secondary,
                    modifier = Modifier.size(36.dp),
                )
            }
        }
        Text(
            "No downloads yet",
            style = orbitBody,
            color = scheme.text.muted,
            modifier = Modifier.padding(top = OrbitSpacing.md),
        )
    }
}

@Composable
private fun stateChipColors(state: String) = when (state) {
    "RUNNING" -> AssistChipDefaults.assistChipColors(
        containerColor = orbit().accent.solid,
        labelColor = MaterialTheme.colorScheme.onPrimary,
    )
    "FAILED" -> AssistChipDefaults.assistChipColors(
        containerColor = MaterialTheme.colorScheme.errorContainer,
        labelColor = MaterialTheme.colorScheme.onErrorContainer,
    )
    "PAUSED", "SCHEDULED" -> AssistChipDefaults.assistChipColors(
        containerColor = orbit().surfaces.elevated,
        labelColor = orbit().text.secondary,
    )
    else -> AssistChipDefaults.assistChipColors(
        containerColor = orbit().surfaces.elevated,
        labelColor = orbit().text.secondary,
    )
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

    // Legacy hidden-setting rows (useSystemDownloader): the system DownloadManager owns the
    // transfer and we never poll it, so these rows have no real progress/speed data and never
    // leave "RUNNING" on their own. Render them as an always-actionable, static row instead.
    val isLegacy = entry.downloadId > 0

    // Speed tracking: remember last (bytes, time) sample and a rolling history of B/s samples.
    val speedSamples = remember(entry.id) { mutableStateListOf<Long>() }
    var lastSample by remember(entry.id) { mutableStateOf(entry.downloadedBytes to System.currentTimeMillis()) }
    var currentSpeed by remember(entry.id) { mutableStateOf(0L) }

    LaunchedEffect(entry.id, entry.downloadedBytes, entry.state) {
        if (!isLegacy && entry.state == "RUNNING") {
            val (lastBytes, lastTime) = lastSample
            val now = System.currentTimeMillis()
            val dt = now - lastTime
            if (entry.downloadedBytes != lastBytes && dt >= 250) {
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

    val scheme = orbit()
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(OrbitSpacing.lg),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = isLegacy || entry.state == "DONE") { onOpenPreview() }
            .padding(horizontal = OrbitSpacing.lg, vertical = OrbitSpacing.md),
    ) {
        FaviconOrLetter(
            url = entry.url,
            label = entry.fileName,
            size = 36.dp,
            modifier = Modifier.padding(top = 2.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(entry.fileName, maxLines = 1, overflow = TextOverflow.Ellipsis, style = orbitBody, color = scheme.text.primary)
                    Spacer(modifier = Modifier.height(4.dp))
                    AssistChip(
                        onClick = {},
                        enabled = false,
                        border = null,
                        label = { Text(entry.state, style = MaterialTheme.typography.labelSmall) },
                        colors = stateChipColors(entry.state),
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    val subtitle = if (isLegacy) {
                        "Managed by system downloader"
                    } else {
                        buildString {
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
                    }
                    Text(subtitle, style = orbitCaption, color = scheme.text.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (!isLegacy && entry.state == "RUNNING" && speedSamples.size >= 2) {
                    Spacer(modifier = Modifier.size(8.dp))
                    SpeedSparkline(samples = speedSamples)
                }
            }

            if (!isLegacy && entry.state != "DONE" && entry.state != "CANCELLED") {
                Spacer(modifier = Modifier.height(4.dp))
                if (entry.totalBytes > 0) {
                    LinearProgressIndicator(
                        progress = { (entry.downloadedBytes.toFloat() / entry.totalBytes.toFloat()).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                        trackColor = scheme.surfaces.elevated,
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), trackColor = scheme.surfaces.elevated)
                }
            }

            Row(
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isLegacy) {
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More options", tint = scheme.text.secondary)
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
                } else {
                    when (entry.state) {
                        "RUNNING" -> {
                            IconButton(onClick = onPause) {
                                Icon(Icons.Filled.Pause, contentDescription = "Pause", tint = scheme.text.secondary)
                            }
                            IconButton(onClick = onCancel) {
                                Icon(Icons.Filled.Cancel, contentDescription = "Cancel", tint = scheme.text.secondary)
                            }
                        }
                        "PAUSED" -> {
                            IconButton(onClick = onResume) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = "Resume", tint = scheme.text.secondary)
                            }
                            IconButton(onClick = onCancel) {
                                Icon(Icons.Filled.Cancel, contentDescription = "Cancel", tint = scheme.text.secondary)
                            }
                        }
                        "FAILED" -> {
                            IconButton(onClick = onRetry) {
                                Icon(Icons.Filled.Refresh, contentDescription = "Retry", tint = scheme.text.secondary)
                            }
                            IconButton(onClick = onDelete) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = scheme.text.secondary)
                            }
                        }
                        "SCHEDULED" -> {
                            IconButton(onClick = onCancel) {
                                Icon(Icons.Filled.Cancel, contentDescription = "Cancel", tint = scheme.text.secondary)
                            }
                            IconButton(onClick = onDelete) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = scheme.text.secondary)
                            }
                        }
                        "PENDING" -> {
                            IconButton(onClick = onCancel) {
                                Icon(Icons.Filled.Cancel, contentDescription = "Cancel", tint = scheme.text.secondary)
                            }
                        }
                        "DONE" -> {
                            Box {
                                IconButton(onClick = { menuExpanded = true }) {
                                    Icon(Icons.Filled.MoreVert, contentDescription = "More options", tint = scheme.text.secondary)
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
                                Icon(Icons.Filled.Refresh, contentDescription = "Retry", tint = scheme.text.secondary)
                            }
                            IconButton(onClick = onDelete) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = scheme.text.secondary)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SpeedSparkline(samples: List<Long>) {
    val color = orbit().accent.solid
    Canvas(modifier = Modifier.size(width = 80.dp, height = 24.dp)) {
        val max = (samples.maxOrNull() ?: 1L).coerceAtLeast(1L)
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

// Authority derives from the package (matches the manifest's ${applicationId}.files) so a
// future applicationIdSuffix can't silently break download open/share (v5.3 review).
private fun fileUriFor(context: android.content.Context, path: String) =
    FileProvider.getUriForFile(context, "${context.packageName}.files", File(path))

/** Resolves a downloaded-manager-owned uri for a legacy (`useSystemDownloader`) row, or null. */
private fun legacyUriFor(context: android.content.Context, downloadId: Long): Uri? {
    val dm = context.getSystemService(android.content.Context.DOWNLOAD_SERVICE) as DownloadManager
    return runCatching { dm.getUriForDownloadedFile(downloadId) }.getOrNull()
}

private fun legacyMimeFor(context: android.content.Context, downloadId: Long): String? {
    val dm = context.getSystemService(android.content.Context.DOWNLOAD_SERVICE) as DownloadManager
    return runCatching { dm.getMimeTypeForDownloadedFile(downloadId) }.getOrNull()
}

/**
 * Resolves a shareable/viewable (uri, mime) pair for an entry, whichever kind it is:
 * a normal engine-downloaded file on disk (via [FileProvider]), or a legacy row with no
 * [DownloadEntry.filePath] whose file only the system DownloadManager knows about.
 */
private fun resolvedUriAndMime(context: android.content.Context, entry: DownloadEntry): Pair<Uri, String>? {
    val path = entry.filePath
    if (path != null) {
        if (!File(path).exists()) return null
        return fileUriFor(context, path) to mimeOf(entry)
    }
    if (entry.downloadId > 0) {
        val uri = legacyUriFor(context, entry.downloadId) ?: return null
        val mime = legacyMimeFor(context, entry.downloadId) ?: mimeOf(entry)
        return uri to mime
    }
    return null
}

private fun shareFile(context: android.content.Context, entry: DownloadEntry) {
    val (uri, mime) = resolvedUriAndMime(context, entry) ?: return
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(Intent.createChooser(intent, "Share")) }
}

private fun openWithChooser(context: android.content.Context, entry: DownloadEntry) {
    val (uri, mime) = resolvedUriAndMime(context, entry) ?: return
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mime)
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

/** Longest edge downsampled images are decoded to, to keep large photos off the main thread cheaply. */
private const val MAX_PREVIEW_EDGE_PX = 2048

private fun computeInSampleSize(rawWidth: Int, rawHeight: Int, maxEdge: Int): Int {
    var inSampleSize = 1
    var width = rawWidth
    var height = rawHeight
    while (width / 2 >= maxEdge || height / 2 >= maxEdge) {
        inSampleSize *= 2
        width /= 2
        height /= 2
    }
    return inSampleSize
}

/**
 * Decodes [path] off the main thread, bounds-checking first so large photos are downsampled
 * to [MAX_PREVIEW_EDGE_PX] instead of fully decoded then discarded. Returns null while loading
 * and `Result.failure` (as opposed to a bare null) if the decode itself fails, so callers can
 * tell "still loading" apart from "couldn't preview this file".
 */
@Composable
private fun rememberDownsampledBitmap(path: String): Result<Bitmap>? {
    val state = produceState<Result<Bitmap>?>(initialValue = null, path) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(path, bounds)
                val sampleSize = computeInSampleSize(bounds.outWidth, bounds.outHeight, MAX_PREVIEW_EDGE_PX)
                val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                BitmapFactory.decodeFile(path, opts) ?: error("decodeFile returned null for $path")
            }
        }
    }
    return state.value
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DownloadPreviewSheet(entry: DownloadEntry, onDismiss: () -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    val path = entry.filePath
    val mime = entry.mimeType ?: ""
    val isLegacy = path == null && entry.downloadId > 0
    val fileExists = remember(path) { path != null && File(path).exists() }
    val scheme = orbit()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = scheme.surfaces.elevated,
        shape = RoundedCornerShape(topStart = OrbitRadii.bar, topEnd = OrbitRadii.bar),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(OrbitSpacing.lg)) {
            Text(entry.fileName, style = orbitBody, color = scheme.text.primary)
            Spacer(modifier = Modifier.height(12.dp))

            if (isLegacy) {
                // No filePath of our own — all we can do is ask the system DownloadManager
                // whether it still has the file, and hand off generic open/share actions.
                val legacyUri = remember(entry.downloadId) { legacyUriFor(context, entry.downloadId) }
                if (legacyUri == null) {
                    Text("File no longer exists", style = orbitCaption, color = scheme.text.muted)
                    Spacer(modifier = Modifier.height(12.dp))
                    TextButton(onClick = onDelete) { Text("Delete") }
                } else {
                    Text("Managed by system downloader", style = orbitCaption, color = scheme.text.muted)
                    Spacer(modifier = Modifier.height(12.dp))
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
                return@Column
            }

            if (path == null || !fileExists) {
                Text("File no longer exists", style = orbitCaption, color = scheme.text.muted)
                Spacer(modifier = Modifier.height(12.dp))
                TextButton(onClick = onDelete) { Text("Delete") }
                return@Column
            }

            when {
                mime.startsWith("image/") -> {
                    val bitmapResult = rememberDownsampledBitmap(path)
                    when {
                        bitmapResult == null -> CircularProgressIndicator()
                        bitmapResult.isSuccess -> Image(
                            bitmap = bitmapResult.getOrThrow().asImageBitmap(),
                            contentDescription = entry.fileName,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        else -> Text("Unable to preview image", style = orbitCaption, color = scheme.text.muted)
                    }
                }
                mime.startsWith("text/") -> {
                    val text = remember(path) {
                        runCatching {
                            File(path).inputStream().use { stream ->
                                val buffer = ByteArray(4096)
                                var totalRead = 0
                                while (totalRead < buffer.size) {
                                    val read = stream.read(buffer, totalRead, buffer.size - totalRead)
                                    if (read == -1) break
                                    totalRead += read
                                }
                                String(buffer, 0, totalRead)
                            }
                        }.getOrDefault("Unable to read file")
                    }
                    SelectionContainer {
                        Text(text, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, color = scheme.text.primary)
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
