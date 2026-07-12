package com.udaytank.browse.ui

import android.Manifest
import android.os.Build
import android.webkit.WebView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.udaytank.browse.BrowserViewModel
import com.udaytank.browse.browser.ReaderExtraction
import com.udaytank.browse.browser.ReaderMode
import com.udaytank.browse.data.ReaderTheme
import com.udaytank.browse.reading.ReadAloudService

/**
 * A distraction-free reader: extracts the active tab's article and renders
 * it in its own WebView from a data URL. The extracted title/content is
 * cached per tab, so the font/theme/width controls re-theme without
 * re-extracting. Falls back to a friendly message when a page has no
 * readable article.
 */
@Composable
fun ReaderOverlay(
    viewModel: BrowserViewModel,
    holder: WebViewHolder,
    tabId: Long,
    dark: Boolean = isSystemInDarkTheme(),
    background: Color,
    onText: Color,
) {
    var article by remember(tabId) { mutableStateOf<ReaderExtraction.Result?>(null) }
    var extractionFailed by remember(tabId) { mutableStateOf(false) }
    LaunchedEffect(tabId) {
        holder.extractReaderContent(tabId) { json ->
            val result = ReaderExtraction.parse(json)
            if (result != null) article = result else extractionFailed = true
        }
    }

    val fontScale by viewModel.readerFontScale.collectAsStateWithLifecycle()
    val theme by viewModel.readerTheme.collectAsStateWithLifecycle()
    val wide by viewModel.readerWide.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        val current = article
        val html = when {
            current != null -> ReaderMode.buildReaderHtml(
                current.title, current.content, theme,
                systemDark = dark, fontScale = fontScale, wide = wide,
            )
            extractionFailed -> ReaderMode.buildReaderHtml(
                "No article found",
                "<p>This page doesn't have a readable article. Tap reader again to return.</p>",
                theme, systemDark = dark, fontScale = fontScale, wide = wide,
            )
            else -> null
        }
        if (html != null) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        setBackgroundColor(background.toArgb())
                        settings.javaScriptEnabled = false
                    }
                },
                update = { webView ->
                    if (webView.tag != html) {
                        webView.tag = html
                        webView.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
                    }
                },
            )
            val context = LocalContext.current
            val ensureNotificationPermission = rememberNotificationPermissionRequest()
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                // Clear of the floating command bar at the bottom of the browser screen.
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 120.dp),
            ) {
                if (current != null) {
                    ListenPill(onClick = {
                        ensureNotificationPermission()
                        // Live-page read-aloud (incognito included: explicit user action;
                        // the notification only ever names this title). Content is handed
                        // over via the service's read-once static holder - no DB row.
                        ReadAloudService.playPending(context, current.title, current.content)
                    })
                }
                ReaderControls(
                    fontScale = fontScale,
                    theme = theme,
                    wide = wide,
                    onFontScale = viewModel::onReaderFontScaleChanged,
                    onTheme = viewModel::onReaderThemeSelected,
                    onWide = viewModel::onReaderWideToggled,
                )
            }
        }
    }
}

/**
 * Floating "Listen" pill starting [ReadAloudService]. Shared by the live
 * reader overlay (pending-content handoff) and the reading list's
 * saved-article reader (by row id).
 */
@Composable
fun ListenPill(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shadowElevation = 4.dp,
        modifier = modifier,
    ) {
        Icon(
            Icons.Filled.Headphones,
            contentDescription = "Listen",
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        )
    }
}

/**
 * One-shot POST_NOTIFICATIONS ask (same pattern as the download prompt in
 * BrowserScreen): returns a callback to invoke right before starting a
 * foreground service. Denial is fine - the service runs regardless, the
 * media notification just stays hidden.
 */
@Composable
fun rememberNotificationPermissionRequest(): () -> Unit {
    var asked by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* proceed regardless of grant result */ }
    return {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !asked) {
            asked = true
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

/**
 * Floating "Aa" pill opening the reader-appearance sheet: font-size stepper
 * (70–160%), theme row, and width toggle. Shared by the live reader overlay
 * and the reading list's saved-article reader; changes persist globally and
 * both readers re-render live.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderControls(
    fontScale: Int,
    theme: ReaderTheme,
    wide: Boolean,
    onFontScale: (Int) -> Unit,
    onTheme: (ReaderTheme) -> Unit,
    onWide: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var sheetOpen by remember { mutableStateOf(false) }
    Surface(
        onClick = { sheetOpen = true },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shadowElevation = 4.dp,
        modifier = modifier,
    ) {
        Text(
            "Aa",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        )
    }
    if (sheetOpen) {
        ModalBottomSheet(onDismissRequest = { sheetOpen = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp),
            ) {
                Text("Font size", style = MaterialTheme.typography.labelLarge)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { onFontScale((fontScale - 10).coerceAtLeast(70)) },
                        enabled = fontScale > 70,
                    ) {
                        Icon(Icons.Filled.Remove, contentDescription = "Smaller text")
                    }
                    Text(
                        "$fontScale%",
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = { onFontScale((fontScale + 10).coerceAtMost(160)) },
                        enabled = fontScale < 160,
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Larger text")
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text("Theme", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(8.dp))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    ReaderTheme.entries.forEachIndexed { index, entry ->
                        SegmentedButton(
                            selected = theme == entry,
                            onClick = { onTheme(entry) },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = ReaderTheme.entries.size,
                            ),
                        ) {
                            Text(entry.name.lowercase().replaceFirstChar { it.uppercase() })
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text("Width", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(8.dp))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = !wide,
                        onClick = { onWide(false) },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    ) { Text("Narrow") }
                    SegmentedButton(
                        selected = wide,
                        onClick = { onWide(true) },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    ) { Text("Full") }
                }
            }
        }
    }
}
