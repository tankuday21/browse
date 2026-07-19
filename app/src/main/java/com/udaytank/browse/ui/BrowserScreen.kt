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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.udaytank.browse.BrowserViewModel
import com.udaytank.browse.DownloadWhen
import com.udaytank.browse.browser.BarState
import com.udaytank.browse.data.WeatherLocation
import com.udaytank.browse.ui.components.BrowserMenuSheet
import com.udaytank.browse.ui.components.FindBar
import com.udaytank.browse.ui.components.FillPasswordBar
import com.udaytank.browse.ui.components.HomeSearchOverlay
import com.udaytank.browse.ui.components.SavePasswordBar
import com.udaytank.browse.ui.components.LocalFaviconCache
import com.udaytank.browse.ui.components.ManageOrbitsSheet
import com.udaytank.browse.ui.components.OrbitAvatar
import com.udaytank.browse.ui.components.OmniBar
import com.udaytank.browse.ui.components.OmniBarInset
import com.udaytank.browse.ui.components.OmniBarReservedHeight
import com.udaytank.browse.ui.components.OrbitQuickSwitchSheet
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
    onOpenPasswords: () -> Unit,
    onScanQr: () -> Unit,
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
    // v5.4 "Share page as QR": a SNAPSHOT of (url, title) taken when the menu row is tapped —
    // live-binding to activeTab would swap the QR mid-scan on navigation, and a bare boolean
    // could resurrect the sheet for a different tab after the original closed.
    var qrShare by remember { mutableStateOf<Pair<String, String?>?>(null) }
    // v4.2 Orbits (Task 7): the quick-switch sheet, plus a state flag for the Orbit management
    // sheet a later task (Task 8) renders — declared here so this task can set it from "Manage
    // Orbits" without owning that sheet's UI.
    var orbitSwitchOpen by remember { mutableStateOf(false) }
    var manageOrbitsOpen by remember { mutableStateOf(false) }
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
    val hiddenCount by viewModel.hiddenCountForHost(currentHost ?: "").collectAsStateWithLifecycle(0)
    var showHiddenSheet by remember { mutableStateOf(false) }
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
    // v3.2 feed state
    val quickDials by viewModel.quickDials.collectAsStateWithLifecycle()
    val weather by viewModel.weather.collectAsStateWithLifecycle()
    val newsItems by viewModel.newsItems.collectAsStateWithLifecycle()
    val sportsItems by viewModel.sportsItems.collectAsStateWithLifecycle()
    val showFeed by viewModel.showFeed.collectAsStateWithLifecycle()
    val showWeather by viewModel.showWeather.collectAsStateWithLifecycle()
    val showNews by viewModel.showNews.collectAsStateWithLifecycle()
    val weatherCity by viewModel.weatherCity.collectAsStateWithLifecycle()
    val weatherUseLocation by viewModel.weatherUseLocation.collectAsStateWithLifecycle()
    // v4.1 site-icon cache (host → Coil model), captured source-direct as you browse.
    val favicons by viewModel.favicons.collectAsStateWithLifecycle()
    var weatherPlaceLabel by rememberSaveable { mutableStateOf("") }
    // v4.2 Orbits (Task 7): the indicator's color and the quick-switch sheet's list both derive
    // from these two StateFlows rather than viewModel.activeOrbit() (a plain, non-reactive
    // function), so recomposition tracks Orbit changes correctly.
    val orbits by viewModel.orbits.collectAsStateWithLifecycle()
    val activeOrbitId by viewModel.activeOrbitId.collectAsStateWithLifecycle()
    // Hidden while incognito — the Orbit indicator is about normal browsing, not the
    // always-dark private context (which already shows its own VisibilityOff cue).
    val activeOrbitColor = if (isIncognito) {
        null
    } else {
        orbits.find { it.id == activeOrbitId }?.colorArgb
    }
    // v4.2 icon-avatars pass: the same active-Orbit lookup, for the indicator's glyph.
    val activeOrbitIcon = if (isIncognito) {
        null
    } else {
        orbits.find { it.id == activeOrbitId }?.iconKey
    }
    // v4.2 Orbits (I1 fix): resolve the active tab's WebView profile key REACTIVELY from the
    // collected `orbits` state above (rather than viewModel.profileKeyForTab(), a plain,
    // non-reactive function read once at composition). On cold start `orbits` can still be empty
    // for a frame before the Flow populates; this recomposes the moment it does, instead of
    // permanently binding the tab's memoized WebView to the default profile (see the TabWebView
    // gate below, which withholds WebView creation until this resolves for non-incognito tabs).
    val activeProfileKey = orbits.find { it.id == activeTab?.orbitId }?.profileKey

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

    // v3.2: on the (non-incognito) home, recompute quick dials + throttled feed refresh.
    LaunchedEffect(isHome, isIncognito) {
        if (isHome && !isIncognito) viewModel.onHomeShown(isIncognito)
    }
    // Weather: resolve a place (opt-in coarse location, else the set city) then load it.
    LaunchedEffect(isHome, isIncognito, showFeed, showWeather, weatherCity, weatherUseLocation) {
        if (isHome && !isIncognito && showFeed && showWeather) {
            val place = when {
                weatherUseLocation -> WeatherLocation.lastKnownCoarse(context)
                    ?: weatherCity.takeIf { it.isNotBlank() }?.let { viewModel.resolveCity(it) }
                weatherCity.isNotBlank() -> viewModel.resolveCity(weatherCity)
                else -> null
            }
            weatherPlaceLabel = place?.label ?: ""
            if (place != null) viewModel.loadWeather(place)
        }
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
            incognito = isIncognito,
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
            // The overflow menu sheet is hoisted to screen level (see `if (menuOpen)` below the
            // bottom stack) so it can be opened from BOTH the web bottom bar's ⋮ AND the home
            // top bar's ⋮ — the latter is composed even when this OmniBar is hidden on home.
            menu = {},
            activeOrbitColor = activeOrbitColor,
            activeOrbitIcon = activeOrbitIcon,
            activeOrbitId = if (isIncognito) null else activeOrbitId,
            onOpenOrbitSwitch = { orbitSwitchOpen = true },
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

    // The home page now FOLLOWS the app theme (light theme → light home), so a light-theme user
    // no longer lands on a dark screen. Incognito alone stays always-dark — a deliberate private
    // affordance — regardless of the chosen theme. Normal web browsing follows the app theme too.
    val screenScheme = if (isIncognito) darkOrbit else orbit()
    CompositionLocalProvider(LocalFaviconCache provides favicons) {
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
                        // #7: the home's middle search pill opens the address editor (the bottom
                        // bar is hidden on home while not editing — see the omniBar() gate below).
                        onSearchClick = { isEditing = true },
                        onVoiceSearch = { isEditing = true },
                        // Home owns Tabs + overflow-menu at its own top bar (the bottom bar is
                        // hidden here). Same actions the bottom bar wires: capture a thumbnail of
                        // the outgoing tab before switching, and open the hoisted menu sheet.
                        // Count ONLY the current context's tabs so the incognito home starts at 0
                        // and never shows the normal tabs "merged in".
                        tabCount = tabs.count { it.isIncognito == isIncognito },
                        onOpenTabs = {
                            holder.captureThumbnail(activeTabId ?: -999L)
                            onOpenTabs()
                        },
                        onMenuClick = { menuOpen = true },
                        activeOrbitColor = activeOrbitColor,
                        activeOrbitIcon = activeOrbitIcon,
                        activeOrbitId = if (isIncognito) null else activeOrbitId,
                        onOpenOrbitSwitch = { orbitSwitchOpen = true },
                        // Page fills the space: no bottom-bar footprint to reserve on home, so the
                        // canvas runs to the nav bar (no black strip). navigationBarsPadding keeps
                        // the last feed item clear of the system gesture area.
                        modifier = Modifier
                            .fillMaxSize()
                            .navigationBarsPadding(),
                        lifetimeBlocked = lifetimeBlocked,
                        showGreeting = showGreeting,
                        showHomeStats = showHomeStats,
                        shortcutDensity = shortcutDensity,
                        homeWallpaper = homeWallpaper,
                        quickDials = quickDials,
                        weather = weather,
                        weatherPlace = weatherPlaceLabel,
                        newsItems = newsItems,
                        sportsItems = sportsItems,
                        showFeed = showFeed,
                        showWeather = showWeather,
                        showNews = showNews,
                    )
                } else if (readerActive) {
                    ReaderOverlay(
                        viewModel = viewModel,
                        holder = holder,
                        tabId = currentTabId,
                        background = MaterialTheme.colorScheme.surface,
                        onText = MaterialTheme.colorScheme.onSurface,
                    )
                } else if (isIncognito || activeProfileKey != null) {
                    // Gate (I1 fix): a non-incognito tab's WebView is only ever created once its
                    // Orbit's profileKey is known — obtain() binds ProfileStore ONCE at creation
                    // (getOrPut memoizes the WebView), so creating it while `orbits` hasn't loaded
                    // yet would freeze the tab onto the default profile for its whole lifetime,
                    // defeating per-Orbit isolation. Incognito tabs are exempt: their profileKey
                    // is null by design (they use the fixed "incognito" profile instead) and must
                    // still create immediately.
                    TabWebView(
                        holder = holder,
                        tabId = currentTabId,
                        tabUrl = activeTab.url,
                        incognito = isIncognito,
                        isLoading = state.isLoading,
                        pendingCommand = state.pendingCommand,
                        onCommandConsumed = viewModel::onCommandConsumed,
                        profileKey = activeProfileKey,
                        // Content-above-bar with a FIXED inset at the bar's full height — never
                        // resized on scroll (see the note where effectiveBarState is computed).
                        modifier = Modifier
                            .fillMaxSize()
                            .navigationBarsPadding()
                            .padding(bottom = OmniBarReservedHeight),
                    )
                } else {
                    // Orbits haven't resolved yet (cold-start race) — hold off on creating the
                    // WebView rather than binding it to the default profile. Self-heals the
                    // instant `orbits` populates, since `activeProfileKey` above is reactive.
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .navigationBarsPadding()
                            .padding(bottom = OmniBarReservedHeight)
                            .background(orbit().surfaces.base),
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
            // Web-page editing shows suggestions above the bottom bar. HOME editing uses the
            // top-anchored HomeSearchOverlay instead (Chrome-NTP style), so nothing here on home.
            if (isEditing && !isHome && (suggestions.isNotEmpty() || copiedUrlSuggestion != null)) {
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
            // v4.7 Passwords: save/fill prompts sit just above the address bar.
            val savePrompt by viewModel.saveCredentialPrompt.collectAsStateWithLifecycle()
            val fillPrompt by viewModel.fillPrompt.collectAsStateWithLifecycle()
            savePrompt?.let { p ->
                SavePasswordBar(
                    host = p.host,
                    onSave = viewModel::onSaveCredential,
                    onDismiss = viewModel::onDismissSaveCredentialPrompt,
                )
            }
            fillPrompt?.let { fp ->
                FillPasswordBar(
                    usernames = fp.usernames,
                    onFill = { viewModel.onFillCredential(fp.tabId, fp.orbitId, fp.host, it) },
                    onDismiss = viewModel::onDismissFillPrompt,
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
            } else if (!readerActive && !isHome) {
                // The shared OmniBar — WEB pages only now. HOME never shows a bottom bar: its
                // centered pill is the entry point and tapping it opens the top HomeSearchOverlay
                // (Chrome-NTP style), so there's never a competing bottom search bar on home.
                //
                // Reader mode (spec §3) hides the bar entirely (ReaderOverlay has its own bottom
                // controls); exiting still works via BackHandler / ReaderOverlay.
                omniBar()
            }
        }

        // ── Home search (Chrome-NTP): full-screen top-anchored search over the home canvas ──
        // Only on home; tapping the centered pill sets isEditing=true, which animates this in.
        HomeSearchOverlay(
            visible = isHome && isEditing,
            addressText = state.addressBarText,
            suggestions = suggestions,
            copiedUrl = copiedUrlSuggestion,
            onTextChange = viewModel::onAddressBarTextChanged,
            onSubmit = { viewModel.onGoPressed(); isEditing = false },
            onPick = { viewModel.onSuggestionPicked(it); isEditing = false },
            onPickCopied = { url -> viewModel.onOpenUrl(url); isEditing = false },
            onDismiss = { isEditing = false },
        )

        // ── Overflow menu sheet (hoisted) ───────────────────
        // Rendered at screen level (not inside the OmniBar's `menu` slot) so it opens from the
        // web bottom bar's ⋮ AND the home top bar's ⋮ alike. As a ModalBottomSheet it overlays
        // the window regardless of where it sits in the tree. Every action/condition/badge is
        // exactly what the bottom bar carried before the hoist.
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
                onOpenPasswords = { onOpenPasswords(); menuOpen = false },
                onScanQr = { onScanQr(); menuOpen = false },
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
                onShareAsQr = {
                    activeTab?.let { tab -> qrShare = tab.url to tab.title.takeIf { it != tab.url } }
                    menuOpen = false
                },
                isDesktopSite = activeTabId in desktopTabs,
                onToggleDesktopSite = {
                    val desktop = viewModel.onToggleDesktopSite()
                    activeTabId?.let { holder.setDesktopMode(it, desktop) }
                    menuOpen = false
                },
                currentHost = currentHost,
                onOpenSiteSettings = { siteSheetOpen = true; menuOpen = false },
                onPrint = { onPrint(); menuOpen = false },
                onZapElement = {
                    menuOpen = false
                    activeTabId?.let { holder.enterZapMode(it) }
                },
                onOpenHiddenElements = { menuOpen = false; showHiddenSheet = true },
                hiddenCount = hiddenCount,
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

        // ── Share page as QR (v5.4) ─────────────────────────
        qrShare?.let { (shareUrl, shareTitle) ->
            com.udaytank.browse.ui.components.QrShareSheet(
                url = shareUrl,
                title = shareTitle,
                onDismiss = { qrShare = null },
            )
        }

        // ── Orbit quick-switch sheet (v4.2 Task 7) ──────────
        // Hoisted at screen level, same pattern as the overflow menu sheet above, so it can be
        // opened from the indicator on either bar (web CommandBar or the home top bar).
        if (orbitSwitchOpen) {
            OrbitQuickSwitchSheet(
                orbits = orbits,
                activeOrbitId = activeOrbitId,
                tabCountFor = { orbitId -> tabs.count { it.orbitId == orbitId } },
                onSwitch = {
                    viewModel.onSwitchOrbit(it)
                    orbitSwitchOpen = false
                },
                onManage = {
                    orbitSwitchOpen = false
                    manageOrbitsOpen = true
                },
                onDismiss = { orbitSwitchOpen = false },
            )
        }
        // ── Orbit management sheet (v4.2 Task 8) ────────────
        if (manageOrbitsOpen) {
            ManageOrbitsSheet(
                orbits = orbits,
                onCreate = viewModel::onCreateOrbit,
                onRename = viewModel::onRenameOrbit,
                onSetIcon = viewModel::onSetOrbitIcon,
                onSetColor = viewModel::onSetOrbitColor,
                onDelete = viewModel::onDeleteOrbit,
                onDismiss = { manageOrbitsOpen = false },
            )
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
                // "Open in <Orbit>" (Task 9) — one row per OTHER Orbit; nothing shown when only
                // one Orbit exists, since there's nowhere else to send the link.
                if (orbits.size > 1) {
                    orbits.filter { it.id != activeOrbitId }.forEach { target ->
                        ListItem(
                            leadingContent = {
                                OrbitAvatar(colorArgb = target.colorArgb, iconKey = target.iconKey, size = 24.dp)
                            },
                            headlineContent = { Text("Open in ${target.name}") },
                            modifier = Modifier.clickable {
                                viewModel.onOpenLinkInOrbit(menu.url, target.id)
                                viewModel.onContextMenuDismissed()
                            },
                        )
                    }
                }
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

        // ── v4.0 Element Zapper: manage this site's hidden elements ──
        if (showHiddenSheet) {
            ModalBottomSheet(
                onDismissRequest = { showHiddenSheet = false },
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                val hidden by viewModel.hiddenForHost(currentHost ?: "")
                    .collectAsStateWithLifecycle(emptyList())
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(bottom = 16.dp),
                ) {
                    Text(
                        "Hidden on ${currentHost ?: "this site"}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(16.dp),
                    )
                    if (hidden.isEmpty()) {
                        Text(
                            "Nothing hidden here yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    } else {
                        hidden.forEach { z ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                            ) {
                                Text(
                                    z.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(onClick = { viewModel.removeZap(z.id) }) { Text("Remove") }
                            }
                        }
                        TextButton(
                            onClick = { viewModel.clearZapsForHost(currentHost ?: ""); showHiddenSheet = false },
                            modifier = Modifier.padding(horizontal = 8.dp),
                        ) { Text("Clear all") }
                        Text(
                            "Removing takes effect on the next page load.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }
                }
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
    } // CompositionLocalProvider (site-icon cache)
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
