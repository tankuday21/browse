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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GppBad
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.udaytank.browse.browser.BarState
import com.udaytank.browse.ui.components.BrowserMenuSheet
import com.udaytank.browse.ui.components.FindBar
import com.udaytank.browse.ui.components.OmniBar
import com.udaytank.browse.ui.components.OmniBarInset
import com.udaytank.browse.ui.components.OmniBarReservedHeight
import com.udaytank.browse.ui.components.SuggestionsPanel
import com.udaytank.browse.ui.theme.OrbitSchemeOverride
import com.udaytank.browse.ui.theme.darkOrbit
import com.udaytank.browse.ui.theme.orbit
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
    val unreadCount by viewModel.unreadCount.collectAsStateWithLifecycle()
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    // In-flight or queued downloads drive the menu's Downloads badge.
    val activeDownloadCount = downloads.count {
        it.state == "RUNNING" || it.state == "PENDING" || it.state == "SCHEDULED"
    }
    val siteOverride by viewModel.siteSettingsForCurrentSite.collectAsStateWithLifecycle()
    val globalForceDark by viewModel.forceDark.collectAsStateWithLifecycle()
    val globalTextScale by viewModel.textScale.collectAsStateWithLifecycle()
    val lifetimeBlocked by viewModel.lifetimeBlocked.collectAsStateWithLifecycle()
    val showGreeting by viewModel.showGreeting.collectAsStateWithLifecycle()
    val showHomeStats by viewModel.showHomeStats.collectAsStateWithLifecycle()
    val shortcutDensity by viewModel.shortcutDensity.collectAsStateWithLifecycle()
    val homeWallpaper by viewModel.homeWallpaper.collectAsStateWithLifecycle()

    // OmniBar shrink-not-hide: the VM's scroll hysteresis says Full/Slim; every state where the
    // bar must never shrink (home, editing, reader, find, bar-anchored menu/sheet open) simply
    // forces it Full here — mirrors the old barVisible-forcing logic, one state richer.
    val vmBarState by viewModel.barState.collectAsStateWithLifecycle()
    // Reader mode is NOT in this list: reader hides the bar entirely (spec §3, same treatment
    // as fullscreen video) instead of forcing it Full — see the omniBar() call site below.
    val forceBarFull = isHome || isEditing ||
        menuOpen || siteSheetOpen || state.findQuery != null
    val effectiveBarState = if (forceBarFull) BarState.Full else vmBarState

    // The WebView's bottom inset is FIXED at the bar's full footprint — it must never change
    // while a page is on screen. An earlier version shrank this inset when the bar collapsed
    // (to fill the gap below the slim pill), but resizing the live WebView mid-scroll disrupted
    // the SwipeRefreshLayout gesture: scrolling up stuttered and pull-to-refresh misfired. A
    // constant inset is correct and jank-free; only the floating bar animates its own size.

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

    // The ONE OmniBar, always bottom-anchored — home and web share this exact call site (no
    // more centered home pill), which is what removes the old home->web layout jump. Edit
    // mode always sits at the bottom, above the keyboard, so the suggestions panel + IME
    // insets keep working unchanged.
    val omniBar: @Composable () -> Unit = {
        OmniBar(
            barState = effectiveBarState,
            onBarTap = viewModel::onBarShouldShow,
            homePill = isHome,
            // Voice search from the home pill's mic: identical to the typed path.
            onVoiceSubmit = { spoken ->
                viewModel.onAddressBarTextChanged(spoken)
                viewModel.onGoPressed()
            },
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
                // Bottom-sheet menu (Orbit v3.1 §5): container + styling change only — every
                // action/condition/badge below is the same one the prior DropdownMenu carried,
                // just wired through BrowserMenuSheet's params instead of DropdownMenuItem.
                if (menuOpen) {
                    BrowserMenuSheet(
                        onDismissRequest = { menuOpen = false },
                        canGoBack = state.canGoBack,
                        canGoForward = state.canGoForward,
                        hasPage = state.currentUrl != null,
                        isBookmarked = isBookmarked,
                        onBack = { viewModel.onBackPressed(); menuOpen = false },
                        onForward = { viewModel.onForwardPressed(); menuOpen = false },
                        onReload = { viewModel.onReloadPressed(); menuOpen = false },
                        onShare = {
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, state.currentUrl)
                            }
                            context.startActivity(Intent.createChooser(send, "Share page"))
                            menuOpen = false
                        },
                        onToggleBookmark = { viewModel.onToggleBookmark(); menuOpen = false },
                        onAddToHome = {
                            viewModel.onAddCurrentPageToHome { message ->
                                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                            }
                            menuOpen = false
                        },
                        onNewTab = { viewModel.onNewTab(); menuOpen = false },
                        onNewIncognitoTab = { viewModel.onNewIncognitoTab(); menuOpen = false },
                        onOpenBookmarks = { onOpenBookmarks(); menuOpen = false },
                        onOpenHistory = { onOpenHistory(); menuOpen = false },
                        onOpenDownloads = { onOpenDownloads(); menuOpen = false },
                        activeDownloadCount = activeDownloadCount,
                        onOpenReadingList = { onOpenReadingList(); menuOpen = false },
                        unreadCount = unreadCount,
                        onSaveForLater = {
                            viewModel.onSaveForLater(holder::extractReaderContent) { message ->
                                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                            }
                            menuOpen = false
                        },
                        isHome = isHome,
                        readerActive = readerActive,
                        onToggleReaderMode = { viewModel.onToggleReaderMode(); menuOpen = false },
                        onFindInPage = { viewModel.onFindOpen(); menuOpen = false },
                        isDesktopSite = activeTabId in desktopTabs,
                        onToggleDesktopSite = {
                            val desktop = viewModel.onToggleDesktopSite()
                            activeTabId?.let { holder.setDesktopMode(it, desktop) }
                            menuOpen = false
                        },
                        currentHost = currentHost,
                        onOpenSiteSettings = { siteSheetOpen = true; menuOpen = false },
                        onPrint = { onPrint(); menuOpen = false },
                        onOpenSettings = { onOpenSettings(); menuOpen = false },
                        blockedOnPage = blockedOnPage,
                        isAdAllowedOnSite = currentHost in adAllowedSites,
                        onToggleAdAllowlist = {
                            viewModel.onToggleAllowAdsOnCurrentSite()
                            viewModel.onReloadPressed()
                            menuOpen = false
                        },
                    )
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

    // Back: close editing first, then page history, then exit.
    BackHandler(enabled = isEditing) { isEditing = false }
    BackHandler(enabled = !isEditing && state.canGoBack) { viewModel.onBackPressed() }

    // v3.2: the home page and any incognito context render always-dark (their own dark Orbit
    // scheme) regardless of the app/system theme; normal web browsing follows the app theme.
    val screenScheme = if (isHome || isIncognito) darkOrbit else orbit()
    OrbitSchemeOverride(screenScheme) {
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
                        // Content-above-bar: the home canvas stops exactly where the shared
                        // OmniBar starts (no centered pill anymore — see the omniBar composable
                        // below, rendered bottom-anchored for home exactly like on web).
                        modifier = Modifier
                            .fillMaxSize()
                            .navigationBarsPadding()
                            .padding(bottom = OmniBarReservedHeight),
                        lifetimeBlocked = lifetimeBlocked,
                        showGreeting = showGreeting,
                        showHomeStats = showHomeStats,
                        shortcutDensity = shortcutDensity,
                        homeWallpaper = homeWallpaper,
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
                        // Content-above-bar with a FIXED inset at the bar's full height — never
                        // resized on scroll (see the note where effectiveBarState is computed).
                        modifier = Modifier
                            .fillMaxSize()
                            .navigationBarsPadding()
                            .padding(bottom = OmniBarReservedHeight),
                    )
                }
                state.pageError?.let { errorDescription ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surface)
                            // Bar-inset: this interstitial shows the (Full) OmniBar per spec,
                            // it just must never be drawn underneath it — reserve the same
                            // bottom footprint so "Try again" / "Tap to play" stay clear.
                            .padding(bottom = OmniBarReservedHeight)
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
                    // Bar-inset: centered within a box that reserves the OmniBar's footprint at
                    // the bottom, so a tall card (long URL, wrapped text) can never grow into the
                    // bar's "OK" dismissal being covered by it.
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = OmniBarReservedHeight),
                        contentAlignment = Alignment.Center,
                    ) {
                        Card(
                            modifier = Modifier.padding(24.dp),
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
                // ── Safe Browsing interstitial (D1) — replaces the web content ──
                state.safeBrowsingWarning?.takeIf { it.tabId == currentTabId }?.let { warning ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surface)
                            // Bar-inset: reserve the OmniBar's footprint so "Go back" / "Proceed
                            // anyway" never render underneath the (Full) bar shown over this.
                            .padding(bottom = OmniBarReservedHeight)
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

        // ── Bottom stack: suggestions panel above the find bar / the shared OmniBar ──
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = OmniBarInset)
                .navigationBarsPadding()
                .imePadding()
                .padding(bottom = OmniBarInset),
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
            } else if (!readerActive) {
                // The ONE shared OmniBar — home and web both render it from this exact
                // bottom-anchored spot; it animates its own Full/Slim/editing states
                // internally (see the omniBar composable above), so nothing here needs to
                // hide or reposition it.
                //
                // Reader mode (spec §3) hides the bar entirely rather than forcing it Full —
                // it would otherwise compete with ReaderOverlay's own Aa/Listen bottom
                // controls. The bar isn't composed at all here; exiting reader mode still
                // works via the system BackHandler and ReaderOverlay's own controls/menu.
                omniBar()
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
    } // OrbitSchemeOverride
}

/**
 * True for connectivity-class page errors (offline, DNS, and other net:: engine failures) —
 * the only error class that offers the asteroid game (K1).
 */
private fun isConnectivityError(description: String): Boolean =
    description.contains("ERR_INTERNET_DISCONNECTED") ||
        description.contains("ERR_NAME_NOT_RESOLVED") ||
        description.contains("net::ERR_")

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
