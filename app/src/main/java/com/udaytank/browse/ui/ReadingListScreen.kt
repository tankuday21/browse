package com.udaytank.browse.ui

import android.text.format.DateUtils
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OfflinePin
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.udaytank.browse.BrowserViewModel
import com.udaytank.browse.browser.ReaderMode
import com.udaytank.browse.browser.UrlHosts
import com.udaytank.browse.data.ReadingListEntry
import com.udaytank.browse.reading.ReadAloudService
import com.udaytank.browse.ui.components.FaviconOrLetter
import com.udaytank.browse.ui.components.OrbitTopBar
import com.udaytank.browse.ui.theme.OrbitSpacing
import com.udaytank.browse.ui.theme.orbit
import com.udaytank.browse.ui.theme.orbitBody
import com.udaytank.browse.ui.theme.orbitCaption
import kotlinx.coroutines.launch

/** "Just now", "5 min. ago", "Yesterday"… via the framework's relative formatter. */
private fun relativeDate(millis: Long): String =
    DateUtils.getRelativeTimeSpanString(millis, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
        .toString()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadingListScreen(viewModel: BrowserViewModel, onBack: () -> Unit) {
    val entries by viewModel.readingList.collectAsStateWithLifecycle()
    var showRead by rememberSaveable { mutableStateOf(false) }
    var openEntry by remember { mutableStateOf<ReadingListEntry?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val saved = openEntry
    if (saved != null) {
        SavedArticleReader(viewModel = viewModel, entry = saved, onBack = { openEntry = null })
        return
    }

    val context = LocalContext.current
    val ensureNotificationPermission = rememberNotificationPermissionRequest()
    val scheme = orbit()
    Scaffold(
        topBar = {
            OrbitTopBar(
                title = "Reading list",
                onBack = onBack,
                actions = {
                    // Podcast mode: read every unread article aloud, oldest first.
                    if (entries.any { it.readAt == null }) {
                        IconButton(onClick = {
                            ensureNotificationPermission()
                            ReadAloudService.playAllUnread(context)
                        }) {
                            Icon(
                                Icons.AutoMirrored.Filled.PlaylistPlay,
                                contentDescription = "Play all unread",
                                tint = scheme.text.primary,
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = scheme.surfaces.base,
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(OrbitSpacing.sm),
                modifier = Modifier.padding(horizontal = OrbitSpacing.lg, vertical = OrbitSpacing.sm),
            ) {
                ReadingListFilterChip(label = "Unread", selected = !showRead, onClick = { showRead = false })
                ReadingListFilterChip(label = "Read", selected = showRead, onClick = { showRead = true })
            }
            val visible = entries.filter { (it.readAt != null) == showRead }
            if (visible.isEmpty()) {
                ReadingListEmptyState(
                    showRead = showRead,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(visible, key = { it.id }) { entry ->
                        ReadingListRow(
                            entry = entry,
                            onOpen = {
                                viewModel.onOpenReadingItem(entry)
                                if (entry.filePath != null) openEntry = entry else onBack()
                            },
                            onOpenOriginal = {
                                viewModel.onMarkRead(entry.id, true)
                                viewModel.onOpenUrl(entry.url)
                                onBack()
                            },
                            onToggleRead = { viewModel.onMarkRead(entry.id, entry.readAt == null) },
                            onDelete = { viewModel.onDeleteReadingItem(entry.id) },
                            onSwipeDelete = {
                                viewModel.onDeleteReadingItem(entry.id)
                                scope.launch {
                                    val result = snackbarHostState.showSnackbar(
                                        message = "Removed from reading list",
                                        actionLabel = "Undo",
                                        duration = SnackbarDuration.Short,
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        viewModel.onReopenReadingItem()
                                    }
                                }
                            },
                        )
                        // Flat separation: a whisper-thin low-alpha rule instead of a hairline outline.
                        HorizontalDivider(color = scheme.text.muted.copy(alpha = 0.08f))
                    }
                }
            }
        }
    }
}

/** Flat tonal filter pill — no hairline outline, just a filled/unfilled tonal state. */
@Composable
private fun ReadingListFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val scheme = orbit()
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        border = null,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = scheme.surfaces.elevated,
            labelColor = scheme.text.secondary,
            selectedContainerColor = scheme.accent.solid,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
        ),
    )
}

@Composable
private fun ReadingListEmptyState(showRead: Boolean, modifier: Modifier = Modifier) {
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
                    Icons.AutoMirrored.Filled.MenuBook,
                    contentDescription = null,
                    tint = scheme.text.secondary,
                    modifier = Modifier.size(36.dp),
                )
            }
        }
        Text(
            if (showRead) "Nothing read yet" else "Nothing saved — use \"Save for later\" in the menu",
            style = orbitBody,
            color = scheme.text.muted,
            modifier = Modifier.padding(top = OrbitSpacing.md),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReadingListRow(
    entry: ReadingListEntry,
    onOpen: () -> Unit,
    onOpenOriginal: () -> Unit,
    onToggleRead: () -> Unit,
    onDelete: () -> Unit,
    onSwipeDelete: () -> Unit,
) {
    val scheme = orbit()
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value != SwipeToDismissBoxValue.Settled) {
                onSwipeDelete()
                true
            } else {
                false
            }
        },
    )
    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = OrbitSpacing.xl),
            ) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.align(
                        if (dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd) {
                            Alignment.CenterStart
                        } else {
                            Alignment.CenterEnd
                        }
                    ),
                )
            }
        },
    ) {
        var menuExpanded by remember { mutableStateOf(false) }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(OrbitSpacing.lg),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .background(scheme.surfaces.base)
                .clickable { onOpen() }
                .padding(horizontal = OrbitSpacing.lg, vertical = OrbitSpacing.md),
        ) {
            FaviconOrLetter(url = entry.url, label = entry.title, size = 36.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    entry.title,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = orbitBody,
                    color = scheme.text.primary,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (entry.filePath != null) {
                        Icon(
                            Icons.Filled.OfflinePin,
                            contentDescription = "Available offline",
                            tint = scheme.accent.solid,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(
                        listOfNotNull(UrlHosts.of(entry.url), relativeDate(entry.addedAt))
                            .joinToString(" · "),
                        style = orbitCaption,
                        color = scheme.text.muted,
                        maxLines = 1,
                    )
                }
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = "More options",
                        tint = scheme.text.secondary,
                    )
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text(if (entry.readAt == null) "Mark read" else "Mark unread") },
                        onClick = { menuExpanded = false; onToggleRead() },
                    )
                    DropdownMenuItem(
                        text = { Text("Open original") },
                        onClick = { menuExpanded = false; onOpenOriginal() },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        onClick = { menuExpanded = false; onDelete() },
                    )
                }
            }
        }
    }
}

/**
 * Full-screen reader for an offline copy: loads the stored content HTML and themes it with
 * the current reader prefs, so saved articles honor the same controls as the live reader.
 * Renders in a JS-disabled WebView from a data URL — no network needed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SavedArticleReader(
    viewModel: BrowserViewModel,
    entry: ReadingListEntry,
    onBack: () -> Unit,
) {
    val dark = isSystemInDarkTheme()
    val fontScale by viewModel.readerFontScale.collectAsStateWithLifecycle()
    val theme by viewModel.readerTheme.collectAsStateWithLifecycle()
    val wide by viewModel.readerWide.collectAsStateWithLifecycle()
    val readerFont by viewModel.readerFont.collectAsStateWithLifecycle()

    var loaded by remember(entry.id) { mutableStateOf(false) }
    var content by remember(entry.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(entry.id) {
        content = entry.filePath?.let { viewModel.loadSavedArticle(it) }
        loaded = true
    }

    BackHandler { onBack() }
    Scaffold(
        topBar = { OrbitTopBar(title = entry.title, onBack = onBack) },
        containerColor = orbit().surfaces.base,
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (!loaded) return@Box
            val html = ReaderMode.buildReaderHtml(
                entry.title,
                content ?: "<p>The offline copy of this article is missing.</p>",
                theme,
                systemDark = dark,
                fontScale = fontScale,
                wide = wide,
                font = readerFont,
            )
            val background = MaterialTheme.colorScheme.surface
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
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
            ) {
                if (content != null) {
                    ListenPill(onClick = {
                        ensureNotificationPermission()
                        ReadAloudService.playArticle(context, entry.id)
                    })
                }
                ReaderControls(
                    fontScale = fontScale,
                    theme = theme,
                    wide = wide,
                    font = readerFont,
                    onFontScale = viewModel::onReaderFontScaleChanged,
                    onTheme = viewModel::onReaderThemeSelected,
                    onWide = viewModel::onReaderWideToggled,
                    onFont = viewModel::onReaderFontSelected,
                )
            }
        }
    }
}
