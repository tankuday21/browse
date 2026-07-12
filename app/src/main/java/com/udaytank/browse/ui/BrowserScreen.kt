package com.udaytank.browse.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddHome
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.GppBad
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VisibilityOff
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
import androidx.compose.material3.IconButton
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
import com.udaytank.browse.ui.game.AsteroidGame
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
    onPrint: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isBookmarked by viewModel.isBookmarked.collectAsStateWithLifecycle()
    val homeShortcuts by viewModel.homeShortcuts.collectAsStateWithLifecycle()
    val tabs by viewModel.tabs.collectAsStateWithLifecycle()
    val activeTabId by viewModel.activeTabId.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    var isEditing by remember { mutableStateOf(false) }
    var siteSheetOpen by remember { mutableStateOf(false) }
    // K1: the asteroid game over the connectivity-error page. Closes itself if the error
    // clears underneath it (e.g. a background retry succeeded).
    var gameOpen by remember { mutableStateOf(false) }
    LaunchedEffect(state.pageError) {
        if (state.pageError == null) gameOpen = false
    }
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
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    // In-flight or queued downloads drive the menu's Downloads badge.
    val activeDownloadCount = downloads.count {
        it.state == "RUNNING" || it.state == "PENDING" || it.state == "SCHEDULED"
    }
    val siteOverride by viewModel.siteSettingsForCurrentSite.collectAsStateWithLifecycle()
    val globalForceDark by viewModel.forceDark.collectAsStateWithLifecycle()
    val globalTextScale by viewModel.textScale.collectAsStateWithLifecycle()
    val currentSiteBackgroundAllowed by viewModel.currentSiteBackgroundAllowed.collectAsStateWithLifecycle()
    val currentUrlIsHttp = state.currentUrl?.let { it.startsWith("http://") || it.startsWith("https://") } == true
    val lifetimeBlocked by viewModel.lifetimeBlocked.collectAsStateWithLifecycle()

    // Auto-hiding command bar: the VM's scroll hysteresis says hidden/shown; every state where
    // the bar must never hide (home, editing, reader, find, bar-anchored menu/sheet open)
    // simply forces it visible here.
    val barHidden by viewModel.barHidden.collectAsStateWithLifecycle()
    val barVisible = !barHidden || isHome || isEditing || readerActive ||
        menuOpen || siteSheetOpen || state.findQuery != null

    LaunchedEffect(isEditing) {
        if (isEditing) viewModel.onBarShouldShow() else viewModel.onSuggestionsDismissed()
    }

    // A6 clipboard chip: the ONE clipboard read per bar-focus event (never polled). A copied
    // URL that isn't the page already showing becomes a "Go to copied link" suggestion row.
    var copiedUrlSuggestion by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(isEditing) {
        copiedUrlSuggestion = if (isEditing) {
            clipboard.getText()?.text?.trim()
                ?.takeIf { com.udaytank.browse.browser.UrlInput.isUrlLike(it) }
                ?.let { com.udaytank.browse.browser.UrlInput.toLoadableUrl(it) }
                ?.takeIf { it != state.currentUrl }
        } else {
            null
        }
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
                        shortcuts = homeShortcuts,
                        isIncognito = isIncognito,
                        onOpenUrl = viewModel::onOpenUrl,
                        onAddShortcut = { url, title ->
                            viewModel.onAddShortcut(url, title) { message ->
                                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                            }
                        },
                        onRemoveShortcut = viewModel::onRemoveShortcut,
                        onMoveShortcutToFront = viewModel::onMoveShortcutToFront,
                        modifier = Modifier.fillMaxSize(),
                        lifetimeBlocked = lifetimeBlocked,
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
                        if (isConnectivityError(errorDescription)) {
                            TextButton(
                                onClick = { gameOpen = true },
                                modifier = Modifier.padding(top = 8.dp),
                            ) {
                                Text(
                                    "🚀 Lost in space? Tap to play",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
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
                // ── Safe Browsing interstitial (D1) — replaces the web content ──
                state.safeBrowsingWarning?.takeIf { it.tabId == currentTabId }?.let { warning ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            Icons.Filled.GppBad,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .padding(top = 64.dp, bottom = 16.dp)
                                .size(64.dp),
                        )
                        Text("This site may be dangerous", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            warning.threatLabel,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                        Text(
                            "Google Safe Browsing flagged this page. Attackers here may trick you " +
                                "into installing harmful software or revealing passwords and card numbers.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 12.dp),
                        )
                        Text(
                            warning.url,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                            maxLines = 2,
                        )
                        Button(
                            onClick = {
                                holder.resolveSafeBrowsing(warning.tabId, proceed = false)
                                viewModel.onSafeBrowsingResolved()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 24.dp),
                        ) {
                            Text("Go back")
                        }
                        TextButton(
                            onClick = {
                                holder.resolveSafeBrowsing(warning.tabId, proceed = true)
                                viewModel.onSafeBrowsingResolved()
                            },
                            modifier = Modifier.padding(top = 4.dp),
                        ) {
                            Text(
                                "Proceed anyway (not recommended)",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
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
            if (isEditing && (suggestions.isNotEmpty() || copiedUrlSuggestion != null)) {
                SuggestionsPanel(
                    suggestions = suggestions,
                    onPick = {
                        viewModel.onSuggestionPicked(it)
                        isEditing = false
                    },
                    copiedUrl = copiedUrlSuggestion,
                    onPickCopied = { url ->
                        viewModel.onOpenUrl(url)
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
                // Slides away on downward page scroll, back on any upward nudge (Chrome-style).
                // The page content is already full-bleed behind this overlay, so a hidden bar
                // means the site simply uses the whole height — no blank strip.
                AnimatedVisibility(
                    visible = barVisible,
                    enter = slideInVertically(tween(180)) { it } + fadeIn(tween(180)),
                    exit = slideOutVertically(tween(180)) { it } + fadeOut(tween(180)),
                ) {
                    CommandBar(
            pageUrl = if (isHome) null else state.currentUrl,
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
                    // ── Icon-only action row: the five most-reached page actions plus the
                    // bookmark star (the star toggle predates the reorg; nothing is lost). ──
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(
                            enabled = state.canGoBack,
                            onClick = { viewModel.onBackPressed(); menuOpen = false },
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                        IconButton(
                            enabled = state.canGoForward,
                            onClick = { viewModel.onForwardPressed(); menuOpen = false },
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Forward")
                        }
                        IconButton(
                            onClick = { viewModel.onReloadPressed(); menuOpen = false },
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Reload")
                        }
                        IconButton(
                            enabled = state.currentUrl != null,
                            onClick = {
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, state.currentUrl)
                                }
                                context.startActivity(Intent.createChooser(send, "Share page"))
                                menuOpen = false
                            },
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = "Share page")
                        }
                        IconButton(
                            enabled = state.currentUrl != null,
                            onClick = { viewModel.onToggleBookmark(); menuOpen = false },
                        ) {
                            Icon(
                                if (isBookmarked) Icons.Filled.Star else Icons.Filled.StarBorder,
                                contentDescription = if (isBookmarked) "Remove bookmark" else "Add bookmark",
                                tint = if (isBookmarked) MaterialTheme.colorScheme.primary
                                else androidx.compose.material3.LocalContentColor.current,
                            )
                        }
                        IconButton(
                            enabled = state.currentUrl != null,
                            onClick = {
                                viewModel.onAddCurrentPageToHome { message ->
                                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                }
                                menuOpen = false
                            },
                        ) {
                            Icon(Icons.Filled.AddHome, contentDescription = "Add to home")
                        }
                    }
                    HorizontalDivider()
                    // ── New tabs ────────────────────────────────
                    DropdownMenuItem(
                        text = { Text("New tab") },
                        leadingIcon = { MenuIcon(Icons.Filled.Add) },
                        onClick = { viewModel.onNewTab(); menuOpen = false },
                    )
                    DropdownMenuItem(
                        text = { Text("New incognito tab") },
                        leadingIcon = { MenuIcon(Icons.Filled.VisibilityOff) },
                        onClick = { viewModel.onNewIncognitoTab(); menuOpen = false },
                    )
                    HorizontalDivider()
                    // ── Library ─────────────────────────────────
                    DropdownMenuItem(
                        text = { Text("Bookmarks") },
                        leadingIcon = { MenuIcon(Icons.Filled.Bookmarks) },
                        onClick = { onOpenBookmarks(); menuOpen = false },
                    )
                    DropdownMenuItem(
                        text = { Text("History") },
                        leadingIcon = { MenuIcon(Icons.Filled.History) },
                        onClick = { onOpenHistory(); menuOpen = false },
                    )
                    DropdownMenuItem(
                        text = { Text("Downloads") },
                        leadingIcon = { MenuIcon(Icons.Filled.Download) },
                        trailingIcon = {
                            if (activeDownloadCount > 0) Badge { Text("$activeDownloadCount") }
                        },
                        onClick = { onOpenDownloads(); menuOpen = false },
                    )
                    DropdownMenuItem(
                        text = { Text("Reading list") },
                        leadingIcon = { MenuIcon(Icons.AutoMirrored.Filled.MenuBook) },
                        trailingIcon = {
                            if (unreadCount > 0) Badge { Text("$unreadCount") }
                        },
                        onClick = { onOpenReadingList(); menuOpen = false },
                    )
                    HorizontalDivider()
                    // ── This page ───────────────────────────────
                    DropdownMenuItem(
                        text = { Text("Save for later") },
                        leadingIcon = { MenuIcon(Icons.Filled.BookmarkAdd) },
                        enabled = state.currentUrl != null,
                        onClick = {
                            viewModel.onSaveForLater(holder::extractReaderContent) { message ->
                                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                            }
                            menuOpen = false
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(if (readerActive) "Exit reader" else "Reader mode") },
                        leadingIcon = { MenuIcon(Icons.AutoMirrored.Filled.Article) },
                        enabled = !isHome,
                        onClick = { viewModel.onToggleReaderMode(); menuOpen = false },
                    )
                    DropdownMenuItem(
                        text = { Text("Find in page") },
                        leadingIcon = { MenuIcon(Icons.Filled.FindInPage) },
                        enabled = !isHome,
                        onClick = { viewModel.onFindOpen(); menuOpen = false },
                    )
                    HorizontalDivider()
                    // ── Site controls ───────────────────────────
                    DropdownMenuItem(
                        text = { Text(if (activeTabId in desktopTabs) "Mobile site" else "Desktop site") },
                        leadingIcon = { MenuIcon(Icons.Filled.Computer) },
                        enabled = !isHome,
                        onClick = {
                            val desktop = viewModel.onToggleDesktopSite()
                            activeTabId?.let { holder.setDesktopMode(it, desktop) }
                            menuOpen = false
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Site settings") },
                        leadingIcon = { MenuIcon(Icons.Filled.Tune) },
                        enabled = currentHost != null,
                        onClick = { siteSheetOpen = true; menuOpen = false },
                    )
                    DropdownMenuItem(
                        text = { Text("Print / Save as PDF") },
                        leadingIcon = { MenuIcon(Icons.Filled.Print) },
                        enabled = state.currentUrl != null,
                        onClick = { onPrint(); menuOpen = false },
                    )
                    HorizontalDivider()
                    // ── App ─────────────────────────────────────
                    DropdownMenuItem(
                        text = { Text("Settings") },
                        leadingIcon = { MenuIcon(Icons.Filled.Settings) },
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
                            leadingIcon = { MenuIcon(Icons.Filled.MusicNote) },
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
                            leadingIcon = { MenuIcon(Icons.Filled.Block) },
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
                    value = (if (draftZoom > 0) draftZoom else globalTextScale).toFloat(),
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
                        holder.applyTextZoom(sheetTabId, globalTextScale)
                        holder.applyForceDark(sheetTabId, globalForceDark)
                        holder.applyDesktopMode(sheetTabId, tabDesktopBaseline)
                    },
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                ) { Text("Clear for this site") }
            }
        }

        // ── K1: asteroid game, full-screen over the connectivity-error page ──
        // Composed last so its BackHandler wins and it draws above everything in this Box.
        if (gameOpen) {
            val asteroidHighScore by viewModel.asteroidHighScore.collectAsStateWithLifecycle()
            AsteroidGame(
                highScore = asteroidHighScore,
                onScore = viewModel::onAsteroidScore,
                onExit = { gameOpen = false },
            )
        }
    }
}

/**
 * True for connectivity-class page errors (offline, DNS, and other net:: engine failures) —
 * the only error class that offers the asteroid game (K1).
 */
private fun isConnectivityError(description: String): Boolean =
    description.contains("ERR_INTERNET_DISCONNECTED") ||
        description.contains("ERR_NAME_NOT_RESOLVED") ||
        description.contains("net::ERR_")

/** Primary-tinted leading icon shared by every dropdown menu item (consistent icon set). */
@Composable
private fun MenuIcon(icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
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
