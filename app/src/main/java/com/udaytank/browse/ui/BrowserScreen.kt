package com.udaytank.browse.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.udaytank.browse.BrowserViewModel
import com.udaytank.browse.DownloadWhen
import com.udaytank.browse.ui.components.CommandBar
import com.udaytank.browse.ui.components.FindBar
import com.udaytank.browse.ui.components.SuggestionsPanel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    viewModel: BrowserViewModel,
    holder: WebViewHolder,
    onOpenHistory: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenTabs: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenReadingList: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isBookmarked by viewModel.isBookmarked.collectAsStateWithLifecycle()
    val bookmarksList by viewModel.bookmarks.collectAsStateWithLifecycle()
    val tabs by viewModel.tabs.collectAsStateWithLifecycle()
    val activeTabId by viewModel.activeTabId.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    var isEditing by remember { mutableStateOf(false) }
    var siteSheetOpen by remember { mutableStateOf(false) }
    var notificationPermissionAsked by rememberSaveable { mutableStateOf(false) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* proceed regardless of grant result */ }
    val activeTab = tabs.find { it.id == activeTabId }
    val isIncognito = activeTab?.isIncognito == true
    val isHome = activeTab == null || activeTab.url == BrowserViewModel.HOME_URL
    val blockedCounts by viewModel.blockedCounts.collectAsStateWithLifecycle()
    val adAllowedSites by viewModel.adAllowedSites.collectAsStateWithLifecycle()
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()
    val desktopTabs by viewModel.desktopTabs.collectAsStateWithLifecycle()
    val readerActive by viewModel.readerActive.collectAsStateWithLifecycle()
    val permissionPrompt by viewModel.permissionPrompt.collectAsStateWithLifecycle()
    val blockedOnPage = blockedCounts[activeTabId] ?: 0
    val currentHost = viewModel.currentHost()
    val backgroundMedia by viewModel.backgroundMedia.collectAsStateWithLifecycle()
    val unreadCount by viewModel.unreadCount.collectAsStateWithLifecycle()
    val siteOverride by viewModel.siteSettingsForCurrentSite.collectAsStateWithLifecycle()
    val globalForceDark by viewModel.forceDark.collectAsStateWithLifecycle()
    val currentSiteBackgroundAllowed by viewModel.currentSiteBackgroundAllowed.collectAsStateWithLifecycle()
    val currentUrlIsHttp = state.currentUrl?.let { it.startsWith("http://") || it.startsWith("https://") } == true

    LaunchedEffect(isEditing) {
        if (!isEditing) viewModel.onSuggestionsDismissed()
    }

    // Back: close editing first, then page history, then exit.
    BackHandler(enabled = isEditing) { isEditing = false }
    BackHandler(enabled = !isEditing && state.canGoBack) { viewModel.onBackPressed() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // ── Full-bleed content ──────────────────────────────
        val currentTabId = activeTabId
        if (currentTabId != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
            ) {
                if (isHome) {
                    HomePage(
                        bookmarks = bookmarksList,
                        isIncognito = isIncognito,
                        onOpenUrl = viewModel::onOpenUrl,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else if (readerActive) {
                    ReaderOverlay(
                        viewModel = viewModel,
                        holder = holder,
                        tabId = currentTabId,
                        background = MaterialTheme.colorScheme.surface,
                        onText = MaterialTheme.colorScheme.onSurface,
                    )
                } else {
                    TabWebView(
                        holder = holder,
                        tabId = currentTabId,
                        tabUrl = activeTab.url,
                        incognito = isIncognito,
                        isLoading = state.isLoading,
                        pendingCommand = state.pendingCommand,
                        onCommandConsumed = viewModel::onCommandConsumed,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                state.pageError?.let { errorDescription ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            "🛰️",
                            style = MaterialTheme.typography.displayMedium,
                            modifier = Modifier.padding(top = 64.dp, bottom = 16.dp),
                        )
                        Text("Lost in space", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "This page couldn't be reached. Check your connection and try again.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 12.dp),
                        )
                        Text(
                            errorDescription,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                        Button(
                            onClick = viewModel::onRetryPressed,
                            modifier = Modifier.padding(top = 24.dp),
                        ) {
                            Text("Try again")
                        }
                    }
                }
                state.sslWarningUrl?.let { blockedUrl ->
                    Card(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(
                                "Connection not secure",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                            Text(
                                "Andromeda blocked $blockedUrl because its security certificate is not trustworthy.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                            TextButton(onClick = viewModel::onSslWarningDismissed) {
                                Text("OK")
                            }
                        }
                    }
                }
            }
        }

        // ── Bottom stack: suggestions above the find bar / Command Bar ──
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
                .imePadding()
                .padding(bottom = 12.dp),
        ) {
            if (isEditing && suggestions.isNotEmpty()) {
                SuggestionsPanel(
                    suggestions = suggestions,
                    onPick = {
                        viewModel.onSuggestionPicked(it)
                        isEditing = false
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                )
            }
            if (state.findQuery != null) {
                FindBar(
                    query = state.findQuery ?: "",
                    active = state.findActive,
                    total = state.findTotal,
                    onQueryChange = { query ->
                        viewModel.onFindQueryChanged(query)
                        activeTabId?.let { holder.findInPage(it, query) }
                    },
                    onPrev = { activeTabId?.let { holder.findNext(it, false) } },
                    onNext = { activeTabId?.let { holder.findNext(it, true) } },
                    onClose = {
                        activeTabId?.let { holder.clearFind(it) }
                        viewModel.onFindClose()
                    },
                )
            } else {
                CommandBar(
            displayHost = if (isHome) null else currentHost ?: state.currentUrl,
            addressBarText = state.addressBarText,
            isSecure = state.currentUrl?.startsWith("https://") == true,
            isLoading = state.isLoading,
            progress = state.progress,
            canGoBack = state.canGoBack,
            tabCount = tabs.size,
            isEditing = isEditing,
            onEditingChanged = { isEditing = it },
            onAddressChange = viewModel::onAddressBarTextChanged,
            onGo = viewModel::onGoPressed,
            onBack = viewModel::onBackPressed,
            onOpenTabs = {
                holder.captureThumbnail(activeTabId ?: -999L)
                onOpenTabs()
            },
            onMenuClick = { menuOpen = true },
            menu = {
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Forward") },
                        enabled = state.canGoForward,
                        onClick = { viewModel.onForwardPressed(); menuOpen = false },
                    )
                    DropdownMenuItem(
                        text = { Text("Reload") },
                        onClick = { viewModel.onReloadPressed(); menuOpen = false },
                    )
                    DropdownMenuItem(
                        text = { Text(if (readerActive) "Exit reader" else "Reader mode") },
                        enabled = !isHome,
                        onClick = { viewModel.onToggleReaderMode(); menuOpen = false },
                    )
                    DropdownMenuItem(
                        text = { Text("Save for later") },
                        leadingIcon = {
                            Icon(
                                Icons.Filled.BookmarkAdd,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        },
                        enabled = state.currentUrl != null,
                        onClick = {
                            viewModel.onSaveForLater(holder::extractReaderContent) { message ->
                                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                            }
                            menuOpen = false
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Find in page") },
                        enabled = !isHome,
                        onClick = { viewModel.onFindOpen(); menuOpen = false },
                    )
                    DropdownMenuItem(
                        text = { Text(if (activeTabId in desktopTabs) "Mobile site" else "Desktop site") },
                        enabled = !isHome,
                        onClick = {
                            val desktop = viewModel.onToggleDesktopSite()
                            activeTabId?.let { holder.setDesktopMode(it, desktop) }
                            menuOpen = false
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Site settings") },
                        leadingIcon = {
                            Icon(
                                Icons.Filled.Tune,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        },
                        enabled = currentHost != null,
                        onClick = { siteSheetOpen = true; menuOpen = false },
                    )
                    DropdownMenuItem(
                        text = { Text(if (isBookmarked) "Remove bookmark" else "Add bookmark") },
                        leadingIcon = {
                            Icon(
                                if (isBookmarked) Icons.Filled.Star else Icons.Filled.StarBorder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        },
                        enabled = state.currentUrl != null,
                        onClick = { viewModel.onToggleBookmark(); menuOpen = false },
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("New incognito tab") },
                        onClick = { viewModel.onNewIncognitoTab(); menuOpen = false },
                    )
                    DropdownMenuItem(
                        text = { Text("Bookmarks") },
                        onClick = { onOpenBookmarks(); menuOpen = false },
                    )
                    DropdownMenuItem(
                        text = { Text("History") },
                        onClick = { onOpenHistory(); menuOpen = false },
                    )
                    DropdownMenuItem(
                        text = { Text("Downloads") },
                        onClick = { onOpenDownloads(); menuOpen = false },
                    )
                    DropdownMenuItem(
                        text = { Text("Reading list") },
                        leadingIcon = {
                            Icon(
                                Icons.AutoMirrored.Filled.MenuBook,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        },
                        trailingIcon = {
                            if (unreadCount > 0) Badge { Text("$unreadCount") }
                        },
                        onClick = { onOpenReadingList(); menuOpen = false },
                    )
                    DropdownMenuItem(
                        text = { Text("Settings") },
                        onClick = { onOpenSettings(); menuOpen = false },
                    )
                    if (backgroundMedia && currentUrlIsHttp && !isIncognito) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (currentSiteBackgroundAllowed) "Stop background play for this site"
                                    else "Play in background on this site"
                                )
                            },
                            onClick = {
                                viewModel.onToggleBackgroundMediaForCurrentSite()
                                menuOpen = false
                            },
                        )
                    }
                    if (currentHost != null) {
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "$blockedOnPage ads blocked on this page",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            },
                            onClick = { menuOpen = false },
                            enabled = false,
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (currentHost in adAllowedSites) "Block ads on this site"
                                    else "Allow ads on this site"
                                )
                            },
                            onClick = {
                                viewModel.onToggleAllowAdsOnCurrentSite()
                                viewModel.onReloadPressed()
                                menuOpen = false
                            },
                        )
                    }
                }
            },
            modifier = Modifier
                .pointerInput(isEditing) {
                    if (isEditing) return@pointerInput
                    var dragX = 0f
                    var dragY = 0f
                    detectDragGestures(
                        onDragStart = { dragX = 0f; dragY = 0f },
                        onDrag = { change, amount ->
                            change.consume()
                            dragX += amount.x
                            dragY += amount.y
                        },
                        onDragEnd = {
                            val threshold = 56.dp.toPx()
                            when {
                                dragY < -threshold && kotlin.math.abs(dragY) > kotlin.math.abs(dragX) -> {
                                    holder.captureThumbnail(activeTabId ?: -999L)
                                    onOpenTabs()
                                }
                                dragX > threshold -> viewModel.onSwitchAdjacentTab(next = false)
                                dragX < -threshold -> viewModel.onSwitchAdjacentTab(next = true)
                            }
                        },
                    )
                },
                )
            }
        }

        // ── Site permission prompt ──────────────────────────
        permissionPrompt?.let { prompt ->
            AlertDialog(
                onDismissRequest = { viewModel.onPermissionResolved(false) },
                title = { Text("Permission request") },
                text = { Text("${prompt.host} wants to ${prompt.label}.") },
                confirmButton = {
                    TextButton(onClick = { viewModel.onPermissionResolved(true) }) { Text("Allow") }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.onPermissionResolved(false) }) { Text("Block") }
                },
            )
        }

        // ── Long-press context sheet ────────────────────────
        state.contextMenu?.let { menu ->
            ModalBottomSheet(onDismissRequest = viewModel::onContextMenuDismissed) {
                Text(
                    menu.url,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                ListItem(
                    headlineContent = { Text("Open in new tab") },
                    modifier = Modifier.clickable { viewModel.onOpenInNewTab(menu.url) },
                )
                if (menu.isImage) {
                    ListItem(
                        headlineContent = { Text("Download image") },
                        modifier = Modifier.clickable {
                            holder.downloadFile(menu.url)
                            viewModel.onContextMenuDismissed()
                        },
                    )
                }
                ListItem(
                    headlineContent = { Text("Copy link") },
                    modifier = Modifier.clickable {
                        clipboard.setText(AnnotatedString(menu.url))
                        viewModel.onContextMenuDismissed()
                    },
                )
                ListItem(
                    headlineContent = { Text("Share") },
                    modifier = Modifier.clickable {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, menu.url)
                        }
                        context.startActivity(Intent.createChooser(send, "Share link"))
                        viewModel.onContextMenuDismissed()
                    },
                )
            }
        }

        // ── Download prompt sheet ───────────────────────────
        state.downloadPrompt?.let { prompt ->
            ModalBottomSheet(onDismissRequest = viewModel::onDownloadPromptDismissed) {
                Text(
                    prompt.fileName,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                TextButton(
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationPermissionAsked) {
                            notificationPermissionAsked = true
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        viewModel.onStartDownload(
                            prompt.url, prompt.fileName, prompt.mimeType, prompt.userAgent, DownloadWhen.NOW,
                        )
                        viewModel.onDownloadPromptDismissed()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Download now") }
                TextButton(
                    onClick = {
                        viewModel.onStartDownload(
                            prompt.url, prompt.fileName, prompt.mimeType, prompt.userAgent, DownloadWhen.WIFI,
                        )
                        viewModel.onDownloadPromptDismissed()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("On Wi-Fi") }
                TextButton(
                    onClick = {
                        viewModel.onStartDownload(
                            prompt.url, prompt.fileName, prompt.mimeType, prompt.userAgent, DownloadWhen.LATER_1H,
                        )
                        viewModel.onDownloadPromptDismissed()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("In 1 hour") }
            }
        }

        // ── Site settings sheet (H6) ────────────────────────
        val sheetHost = currentHost
        val sheetTabId = activeTabId
        if (siteSheetOpen && sheetHost != null && sheetTabId != null) {
            // Drafts mirror the stored override; for incognito (never persisted) they are the
            // only state, so the sheet still reflects what was applied to the live tab.
            var draftZoom by remember(siteOverride?.textZoom) {
                mutableStateOf(siteOverride?.textZoom ?: -1)
            }
            var draftForceDark by remember(siteOverride?.forceDark) {
                mutableStateOf(siteOverride?.forceDark ?: -1)
            }
            var draftDesktop by remember(siteOverride?.desktopMode) {
                mutableStateOf(siteOverride?.desktopMode ?: -1)
            }
            val tabDesktopBaseline = sheetTabId in desktopTabs
            ModalBottomSheet(onDismissRequest = { siteSheetOpen = false }) {
                Text(
                    sheetHost,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                if (isIncognito) {
                    Text(
                        "Not remembered in incognito",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                Text(
                    "Text size — ${if (draftZoom > 0) "$draftZoom%" else "Default"}",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
                )
                Slider(
                    value = (if (draftZoom > 0) draftZoom else 100).toFloat(),
                    onValueChange = { raw ->
                        val snapped = (raw / 10f).roundToInt() * 10
                        draftZoom = snapped
                        holder.applyTextZoom(sheetTabId, snapped)
                    },
                    onValueChangeFinished = {
                        if (draftZoom > 0) viewModel.onSetSiteOverride(textZoom = draftZoom)
                    },
                    valueRange = 50f..200f,
                    steps = 14, // (200 - 50) / 10 - 1: snap points every 10%
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                TriStateChipRow(
                    label = "Force dark",
                    value = draftForceDark,
                    onSelect = { value ->
                        draftForceDark = value
                        viewModel.onSetSiteOverride(forceDark = value)
                        holder.applyForceDark(
                            sheetTabId,
                            if (value == -1) globalForceDark else value == 1,
                        )
                    },
                )
                TriStateChipRow(
                    label = "Desktop site",
                    value = draftDesktop,
                    onSelect = { value ->
                        draftDesktop = value
                        viewModel.onSetSiteOverride(desktopMode = value)
                        holder.applyDesktopMode(
                            sheetTabId,
                            if (value == -1) tabDesktopBaseline else value == 1,
                        )
                    },
                )
                TextButton(
                    onClick = {
                        viewModel.onClearSiteOverrides()
                        draftZoom = -1
                        draftForceDark = -1
                        draftDesktop = -1
                        holder.applyTextZoom(sheetTabId, 100)
                        holder.applyForceDark(sheetTabId, globalForceDark)
                        holder.applyDesktopMode(sheetTabId, tabDesktopBaseline)
                    },
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                ) { Text("Clear for this site") }
            }
        }
    }
}

/** Default / On / Off chip row for one tri-state site override (-1 / 1 / 0). */
@Composable
private fun TriStateChipRow(label: String, value: Int, onSelect: (Int) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(-1 to "Default", 1 to "On", 0 to "Off").forEach { (chipValue, chipLabel) ->
                FilterChip(
                    selected = value == chipValue,
                    onClick = { onSelect(chipValue) },
                    label = { Text(chipLabel) },
                )
            }
        }
    }
}
