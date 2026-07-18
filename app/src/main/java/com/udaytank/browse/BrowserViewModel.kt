package com.udaytank.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.udaytank.browse.browser.BarScrollPolicy
import com.udaytank.browse.browser.BarState
import com.udaytank.browse.browser.BrowserCommand
import com.udaytank.browse.browser.UrlHosts
import com.udaytank.browse.browser.feed.FeedCategory
import com.udaytank.browse.browser.feed.FeedItem
import com.udaytank.browse.browser.feed.QuickDial
import com.udaytank.browse.browser.feed.QuickDialPolicy
import com.udaytank.browse.browser.feed.VisitedUrl
import com.udaytank.browse.browser.feed.Weather
import com.udaytank.browse.browser.feed.WeatherCodec
import com.udaytank.browse.browser.adblock.FilterLists
import com.udaytank.browse.browser.Suggestion
import com.udaytank.browse.browser.SuggestionEngine
import com.udaytank.browse.browser.SuggestionKind
import com.udaytank.browse.browser.TabGroupPolicy
import com.udaytank.browse.browser.TabManager
import com.udaytank.browse.browser.UrlInput
import com.udaytank.browse.browser.googleSuggest
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import com.udaytank.browse.browser.VisitDecision
import com.udaytank.browse.browser.VisitPolicy
import com.udaytank.browse.browser.Backup
import com.udaytank.browse.browser.BackupCodec
import com.udaytank.browse.browser.ReaderExtraction
import com.udaytank.browse.data.Bookmark
import com.udaytank.browse.data.BookmarkDao
import com.udaytank.browse.data.ClosedTabDao
import com.udaytank.browse.data.ClosedTabEntity
import com.udaytank.browse.data.DownloadDao
import com.udaytank.browse.data.DownloadEntry
import com.udaytank.browse.data.HistoryDao
import com.udaytank.browse.data.HistoryEntry
import com.udaytank.browse.data.HomeShortcutDao
import com.udaytank.browse.data.HomeShortcutEntity
import com.udaytank.browse.data.OrbitEntity
import com.udaytank.browse.data.OrbitRepository
import com.udaytank.browse.data.ReadingListDao
import com.udaytank.browse.data.ReadingListEntry
import com.udaytank.browse.data.SearchEngine
import com.udaytank.browse.data.FeedRepository
import com.udaytank.browse.data.SettingsRepository
import com.udaytank.browse.data.WeatherPlace
import com.udaytank.browse.data.WeatherRepository
import com.udaytank.browse.data.ZapRepository
import com.udaytank.browse.data.ZappedElementEntity
import com.udaytank.browse.data.SiteSettingsDao
import com.udaytank.browse.data.SiteSettingsEntity
import com.udaytank.browse.data.TabDao
import com.udaytank.browse.data.TabEntity
import com.udaytank.browse.data.TabGroupDao
import com.udaytank.browse.data.TabGroupEntity
import com.udaytank.browse.data.ReaderTheme
import com.udaytank.browse.data.ShortcutDensity
import com.udaytank.browse.data.ThemeMode
import com.udaytank.browse.reading.ArticleStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LinkContextMenu(val url: String, val isImage: Boolean)

/** A Safe Browsing hit waiting on the user's go-back / proceed decision (D1). */
data class SafeBrowsingWarning(val tabId: Long, val url: String, val threatLabel: String)

/** A download the engine wants user confirmation for before it starts. */
data class DownloadPrompt(
    val url: String,
    val fileName: String,
    val mimeType: String?,
    val userAgent: String?,
)

/** When a requested download should actually run. */
enum class DownloadWhen { NOW, WIFI, LATER_1H }

/**
 * Abstracts starting/controlling the download flow so [BrowserViewModel] (a plain [ViewModel],
 * not an [android.app.Application]-scoped one) never touches Context directly. The production
 * impl (`ServiceDownloadController`, in `download/`) sends intents to [com.udaytank.browse.download.DownloadService];
 * tests use a recording fake.
 */
interface DownloadController {
    fun startDownload(id: Long)
    fun schedule(id: Long, constraint: DownloadWhen)
    fun pause(id: Long)
    fun resume(id: Long)
    fun cancel(id: Long)
}

data class BrowserUiState(
    val addressBarText: String = "",
    val currentUrl: String? = null,
    val isLoading: Boolean = false,
    val progress: Int = 0,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val pendingCommand: BrowserCommand? = null,
    val sslWarningUrl: String? = null,
    val findQuery: String? = null,
    val findActive: Int = 0,
    val findTotal: Int = 0,
    val contextMenu: LinkContextMenu? = null,
    val pageError: String? = null,
    val confirmCloseTabId: Long? = null,
    val downloadPrompt: DownloadPrompt? = null,
    val safeBrowsingWarning: SafeBrowsingWarning? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class BrowserViewModel(
    private val historyDao: HistoryDao,
    private val bookmarkDao: BookmarkDao,
    private val tabDao: TabDao,
    private val settings: SettingsRepository,
    private val downloadDao: DownloadDao,
    private val closedTabDao: ClosedTabDao,
    private val tabGroupDao: TabGroupDao,
    private val readingListDao: ReadingListDao,
    private val articleStore: ArticleStore,
    private val siteSettingsDao: SiteSettingsDao,
    private val homeShortcutDao: HomeShortcutDao,
    private val downloadController: DownloadController,
    /**
     * Removes a system-DownloadManager-owned download by its DM id. Only ever used for the
     * hidden `useSystemDownloader` (legacy) rows, which have no [DownloadEntry.filePath] of our
     * own to delete. Defaults to a no-op so most call sites (and most tests) don't need to care.
     */
    private val downloadManagerRemover: (Long) -> Unit = {},
    suggestionFetcher: suspend (String) -> List<String> = ::googleSuggest,
    /** File work (article HTML writes/deletes) runs here; tests swap in Unconfined. */
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    /**
     * Rebuilds the ad-block engines from the currently enabled filter lists
     * ([com.udaytank.browse.BrowseApplication.reloadAdblock] in production). A lambda so this
     * plain ViewModel never touches the Application; tests keep the no-op default.
     */
    private val reloadAdblock: suspend () -> Unit = {},
    /** v3.2 home feed (null in tests → empty feed, no network). */
    private val feedRepository: FeedRepository? = null,
    private val weatherRepository: WeatherRepository? = null,
    private val zapRepository: ZapRepository? = null,
    /** v4.1 site-icon cache (null in tests → letters only). */
    private val faviconRepository: com.udaytank.browse.data.FaviconRepository? = null,
    /** v4.2 Orbits (browsing profiles); null in tests that don't exercise Orbits. */
    private val orbitRepository: OrbitRepository? = null,
    /** v4.7 Passwords (per-Orbit encrypted vault); null in tests that don't exercise it. */
    private val credentialRepository: com.udaytank.browse.data.CredentialRepository? = null,
) : ViewModel() {

    private val tabManager = TabManager(tabDao, closedTabDao)
    private val suggestionEngine = SuggestionEngine(historyDao, bookmarkDao, suggestionFetcher)

    private val _suggestions = MutableStateFlow<List<Suggestion>>(emptyList())
    val suggestions: StateFlow<List<Suggestion>> = _suggestions.asStateFlow()
    private var suggestionJob: Job? = null

    /** Tabs currently requesting the desktop site (menu label state). */
    private val _desktopTabs = MutableStateFlow<Set<Long>>(emptySet())
    val desktopTabs: StateFlow<Set<Long>> = _desktopTabs.asStateFlow()

    /** Active tab is showing reader mode. */
    private val _readerActive = MutableStateFlow(false)
    val readerActive: StateFlow<Boolean> = _readerActive.asStateFlow()

    fun onToggleReaderMode(): Boolean {
        _readerActive.value = !_readerActive.value
        onBarShouldShow()
        return _readerActive.value
    }

    fun onExitReaderMode() { _readerActive.value = false }

    // ── OmniBar shrink-not-hide (v3.1 Task 3) ────────────────────────────────────
    // Scroll hysteresis lives in the pure BarScrollPolicy; this layer only forwards
    // events for the ACTIVE tab and pushes a StateFlow update when the answer actually
    // changes (scroll events are high-frequency — recompositions must not be).

    private var barScrollPolicy = BarScrollPolicy()

    /** Full (56dp floating pill) or Slim (30dp grab-pill) — drives the OmniBar's shrink. */
    private val _barState = MutableStateFlow(BarState.Full)
    val barState: StateFlow<BarState> = _barState.asStateFlow()

    /** Density-corrected shrink/expand thresholds; set once by the UI layer (MainActivity). */
    fun setBarScrollThresholds(shrinkPx: Int, expandPx: Int) {
        if (shrinkPx > 0 && expandPx > 0) {
            barScrollPolicy = BarScrollPolicy(shrinkThresholdPx = shrinkPx, expandThresholdPx = expandPx)
        }
    }

    /** High-frequency WebView scroll callback; cheap by contract (see holder Listener docs). */
    fun onPageScrolled(tabId: Long, scrollY: Int, dy: Int) {
        if (tabId != activeTabId.value) return
        val next = barScrollPolicy.onScroll(scrollY, dy)
        if (_barState.value != next) _barState.value = next
    }

    /**
     * Any bar-restoring event: tab switch, navigation start, edit focus, reader toggle,
     * find bar, landing on the home tab, or a tap on the Slim grab-pill. Resets the
     * hysteresis so the bar doesn't instantly re-shrink from stale accumulated scroll.
     */
    fun onBarShouldShow() {
        barScrollPolicy.reset()
        if (_barState.value != BarState.Full) _barState.value = BarState.Full
    }

    val forceDark: StateFlow<Boolean> = settings.forceDarkWebsites
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun onForceDarkToggled(enabled: Boolean) {
        viewModelScope.launch { settings.setForceDarkWebsites(enabled) }
    }

    val httpsOnly: StateFlow<Boolean> = settings.httpsOnly
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun onHttpsOnlyToggled(enabled: Boolean) {
        viewModelScope.launch { settings.setHttpsOnly(enabled) }
    }

    val useSystemDownloader: StateFlow<Boolean> = settings.useSystemDownloader
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun onUseSystemDownloaderToggled(enabled: Boolean) {
        viewModelScope.launch { settings.setUseSystemDownloader(enabled) }
    }

    /** Global opt-in for the experimental background-media-playback feature. */
    val backgroundMedia: StateFlow<Boolean> = settings.backgroundMedia
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun onBackgroundMediaToggled(enabled: Boolean) {
        viewModelScope.launch { settings.setBackgroundMedia(enabled) }
    }

    /** Hosts the user has explicitly allowed to keep playing media in the background. */
    val backgroundMediaSites: StateFlow<Set<String>> = settings.backgroundMediaSites
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    /** Toggles the active tab's host in the background-media allowlist. */
    fun onToggleBackgroundMediaForCurrentSite() {
        // Incognito hosts must never enter the persisted allowlist - that would leak which
        // private sites the user visited across sessions.
        val isIncognito = tabs.value.find { it.id == activeTabId.value }?.isIncognito == true
        if (isIncognito) return
        val host = currentHost() ?: return
        viewModelScope.launch {
            val current = backgroundMediaSites.value
            settings.setBackgroundMediaSites(if (host in current) current - host else current + host)
        }
    }

    val lockIncognito: StateFlow<Boolean> = settings.lockIncognito
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun onLockIncognitoToggled(enabled: Boolean) {
        viewModelScope.launch { settings.setLockIncognito(enabled) }
    }

    val switcherListLayout: StateFlow<Boolean> = settings.switcherListLayout
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun onSwitcherLayoutToggled() {
        viewModelScope.launch { settings.setSwitcherListLayout(!switcherListLayout.value) }
    }

    val autoIslands: StateFlow<Boolean> = settings.autoIslands
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun onAutoIslandsToggled(enabled: Boolean) {
        viewModelScope.launch { settings.setAutoIslands(enabled) }
    }

    /** Whether incognito content is currently hidden behind the biometric gate. */
    private val _incognitoLocked = MutableStateFlow(false)
    val incognitoLocked: StateFlow<Boolean> = _incognitoLocked.asStateFlow()
    fun onIncognitoLocked() { _incognitoLocked.value = true }
    fun onIncognitoUnlocked() { _incognitoLocked.value = false }

    /** "Require screen lock for passwords" pref (v5.1, default ON). */
    val lockPasswords: StateFlow<Boolean> = settings.lockPasswords
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun onLockPasswordsToggled(enabled: Boolean) {
        viewModelScope.launch { settings.setLockPasswords(enabled) }
    }

    /**
     * Whether the Passwords screen is currently behind the biometric gate (v5.1). Starts
     * locked; one successful auth unlocks it for the foreground session — MainActivity.onStop
     * re-locks, same lifecycle hook as the incognito lock.
     */
    private val _passwordsLocked = MutableStateFlow(true)
    val passwordsLocked: StateFlow<Boolean> = _passwordsLocked.asStateFlow()
    fun onPasswordsLocked() { _passwordsLocked.value = true }
    fun onPasswordsUnlocked() { _passwordsLocked.value = false }

    /** The pending site-permission prompt, if any (camera/mic/location). */
    private val _permissionPrompt = MutableStateFlow<com.udaytank.browse.ui.PermissionRequestInfo?>(null)
    val permissionPrompt: StateFlow<com.udaytank.browse.ui.PermissionRequestInfo?> = _permissionPrompt.asStateFlow()

    fun onPermissionRequested(info: com.udaytank.browse.ui.PermissionRequestInfo) {
        _permissionPrompt.value = info
    }

    fun onPermissionResolved(grant: Boolean) {
        val info = _permissionPrompt.value ?: return
        if (grant) info.grant() else info.deny()
        _permissionPrompt.value = null
    }
    val tabs: StateFlow<List<TabEntity>> = tabManager.tabs
    val activeTabId: StateFlow<Long?> = tabManager.activeTabId

    // ── v4.2 Orbits (browsing profiles) ─────────────────────────────────────
    /** Every Orbit, ordered by position. Empty (never null) when Orbits aren't wired (tests). */
    val orbits: StateFlow<List<OrbitEntity>> =
        (orbitRepository?.observeAll() ?: flowOf(emptyList()))
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** A persisted-but-unset (0) id resolves to the first Orbit's id once one exists. */
    private fun resolveActiveOrbitId(stored: Long): Long =
        if (stored != 0L) stored else orbits.value.firstOrNull()?.id ?: 0L

    val activeOrbitId: StateFlow<Long> = settings.activeOrbitId
        .map { resolveActiveOrbitId(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0L)

    /** The currently active Orbit's row, or null before Orbits have loaded. */
    fun activeOrbit(): OrbitEntity? = orbits.value.find { it.id == activeOrbitId.value }

    /** Resolves a tab's WebView profile key via its Orbit; null for incognito/unrecognized tabs. */
    fun profileKeyForTab(tabId: Long): String? {
        val orbitId = tabs.value.find { it.id == tabId }?.orbitId ?: return null
        return orbits.value.firstOrNull { it.id == orbitId }?.profileKey
    }

    /**
     * A just-deleted Orbit's ProfileStore key, plus the ids of the tabs that were closed out of
     * it. MainActivity (which owns the WebViewHolder — this plain ViewModel never touches it
     * directly) must call `holder.close(tabId)` for every id here BEFORE deleting the profile:
     * closing a tab in [tabManager] only mutates the tab StateFlow/DB, it does NOT destroy the
     * native WebView, and ProfileStore.deleteProfile only succeeds once no live WebView is still
     * using that profile.
     */
    data class OrbitDeletion(val profileKey: String, val tabIds: List<Long>)

    /**
     * Emits a just-deleted Orbit's ProfileStore key (and its closed tab ids) once its tabs are
     * gone from the tab list, so MainActivity can finish tearing down their WebViews and then
     * delete the underlying profile. A profile can only be removed once nothing is still using
     * it, hence the emit happens after tab close-out in [onDeleteOrbit].
     */
    // replay = 1 (I2 fix): the sole collector (MainActivity's composition-scoped LaunchedEffect)
    // can momentarily have no subscriber, e.g. mid Activity-recreation. With replay = 0 an
    // emission during that window is dropped forever and the Orbit's cookies never get wiped
    // (deleteProfile never runs). Replaying the last deletion to a re-subscribing collector is
    // safe: WebViewHolder.deleteProfile is runCatching-guarded and idempotent — replaying it
    // against an already-deleted (or never-existent) profile is a harmless no-op.
    private val _orbitProfileToDelete = MutableSharedFlow<OrbitDeletion>(replay = 1, extraBufferCapacity = 1)
    val orbitProfileToDelete: SharedFlow<OrbitDeletion> = _orbitProfileToDelete.asSharedFlow()

    /**
     * Black Hole panic-wipe: emitted once the data wipe is done, carrying every WebView profile
     * key to tear down (all Orbits' profiles + the fixed "incognito" profile). MainActivity
     * destroys all live WebViews, deletes each profile, clears cookies/storage/thumbnails, then
     * restarts the process — a cold start rebuilds a clean default Orbit + one home tab.
     * replay = 1 for the same re-subscribe safety as [orbitProfileToDelete].
     */
    private val _blackHoleReady = MutableSharedFlow<List<String>>(replay = 1, extraBufferCapacity = 1)
    val blackHoleReady: SharedFlow<List<String>> = _blackHoleReady.asSharedFlow()

    // ── v4.7 Passwords ──────────────────────────────────────
    /** A submitted login awaiting the user's "Save password?" decision. */
    data class SaveCredentialPrompt(val orbitId: Long, val host: String, val username: String, val password: String)

    /** Saved logins are available for the current page; the UI offers to fill one of [usernames]. */
    data class FillPromptState(val tabId: Long, val orbitId: Long, val host: String, val usernames: List<String>)

    /** Instruction to MainActivity to inject a chosen login into a tab's page (user-initiated). */
    data class FillCredentialAction(val tabId: Long, val username: String, val password: String)

    private val _saveCredentialPrompt = MutableStateFlow<SaveCredentialPrompt?>(null)
    val saveCredentialPrompt: StateFlow<SaveCredentialPrompt?> = _saveCredentialPrompt.asStateFlow()

    private val _fillPrompt = MutableStateFlow<FillPromptState?>(null)
    val fillPrompt: StateFlow<FillPromptState?> = _fillPrompt.asStateFlow()

    private val _fillCredentialRequest = MutableSharedFlow<FillCredentialAction>(extraBufferCapacity = 1)
    val fillCredentialRequest: SharedFlow<FillCredentialAction> = _fillCredentialRequest.asSharedFlow()

    /**
     * A login form was submitted. Never save from incognito (defense-in-depth — the capture bridge
     * is already withheld from incognito tabs). Offers a save/update prompt for the active Orbit.
     */
    fun onLoginSubmitted(tabId: Long, host: String, username: String, password: String) {
        if (credentialRepository == null || password.isEmpty()) return
        val tab = tabs.value.find { it.id == tabId }
        if (tab?.isIncognito == true) return
        // Only capture from an HTTPS page — a login typed into a cleartext (MITM-able) page must
        // not be persisted; and never fill it back later either (see onPageFinished).
        val url = tab?.url
        if (url?.startsWith("https://", ignoreCase = true) != true) return
        // Normalize the host through the SAME parser the fill path uses (UrlHosts.of on the tab's
        // committed URL, not the JS-supplied host), so a mixed-case/parser-divergent host can't
        // split save vs. fill and hide the saved login.
        val normHost = UrlHosts.of(url) ?: host.lowercase().ifBlank { return }
        val orbitId = tab.orbitId ?: activeOrbitId.value
        _saveCredentialPrompt.value = SaveCredentialPrompt(orbitId, normHost, username, password)
    }

    /** User accepted the save prompt → encrypt + store under the Orbit the prompt was built for. */
    fun onSaveCredential() {
        val prompt = _saveCredentialPrompt.value ?: return
        val repo = credentialRepository ?: return
        _saveCredentialPrompt.value = null
        viewModelScope.launch {
            repo.save(prompt.orbitId, prompt.host, prompt.username, prompt.password, System.currentTimeMillis())
        }
    }

    fun onDismissSaveCredentialPrompt() { _saveCredentialPrompt.value = null }

    /**
     * User tapped a saved login to fill: re-decrypt (kept out of long-lived state) and inject it.
     * Scoped to the Orbit the fill offer was built for ([orbitId]), not whatever is active at tap
     * time, so a mid-flight Orbit switch can't cross vaults.
     */
    fun onFillCredential(tabId: Long, orbitId: Long, host: String, username: String) {
        val repo = credentialRepository ?: return
        _fillPrompt.value = null
        viewModelScope.launch {
            val match = repo.credentialsForHost(orbitId, host).find { it.username == username }
            if (match != null) {
                _fillCredentialRequest.emit(FillCredentialAction(tabId, match.username, match.password))
            }
        }
    }

    fun onDismissFillPrompt() { _fillPrompt.value = null }

    /** The active Orbit's saved logins (management screen). */
    val credentials: StateFlow<List<com.udaytank.browse.data.CredentialEntity>> = activeOrbitId
        .flatMapLatest { orbitId ->
            credentialRepository?.observeForOrbit(orbitId) ?: flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Reveal one saved login's password (management screen); null if it can't be decrypted. */
    suspend fun revealCredential(entity: com.udaytank.browse.data.CredentialEntity): String? =
        credentialRepository?.reveal(entity)

    fun onDeleteCredential(id: Long) {
        viewModelScope.launch { credentialRepository?.delete(id) }
    }

    /**
     * Normalizes a user-typed site into the same lowercase host the capture/fill paths use
     * (UrlHosts) so a manual entry fills on the matching page. Accepts a full URL or a bare
     * host; null when nothing host-like remains.
     */
    private fun normalizeCredentialHost(raw: String): String? {
        val input = raw.trim()
        if (input.isEmpty()) return null
        return UrlHosts.of(input) ?: UrlHosts.of("https://$input")
    }

    /**
     * Pure preview of how a typed site will be stored — null means it won't save. The editor
     * sheet uses this to validate inline BEFORE enabling Save, so a refused host can never
     * look saved (the sheet would otherwise close silently).
     */
    fun credentialHostPreview(raw: String): String? = normalizeCredentialHost(raw)

    /** Manually add a login into the active Orbit (v5.1). Blank host/password is refused. */
    fun onAddCredential(host: String, username: String, password: String) {
        val normHost = normalizeCredentialHost(host) ?: return
        if (password.isEmpty()) return
        viewModelScope.launch {
            credentialRepository?.save(
                activeOrbitId.value, normHost, username.trim(), password, System.currentTimeMillis()
            )
        }
    }

    /**
     * Edit an existing login (v5.1). Same (host, username) upserts in place. A changed key
     * SAVES FIRST and deletes the old row only after the save succeeded — encryption can fail
     * (Keystore hiccup), and delete-then-save would destroy the only copy of the credential.
     * An edit landing on ANOTHER credential's key is refused outright: the REPLACE upsert
     * would silently destroy that credential's password (the editor disables Save for this
     * case too; this is the defense-in-depth layer).
     */
    fun onEditCredential(id: Long, host: String, username: String, password: String) {
        val original = credentials.value.find { it.id == id } ?: return
        val normHost = normalizeCredentialHost(host) ?: return
        if (password.isEmpty()) return
        val normUser = username.trim()
        val keyChanged = original.host != normHost || original.username != normUser
        if (keyChanged && credentials.value.any { it.id != id && it.host == normHost && it.username == normUser }) {
            return
        }
        viewModelScope.launch {
            val saved = credentialRepository?.save(
                original.orbitId, normHost, normUser, password, System.currentTimeMillis()
            ) == true
            if (saved && keyChanged) credentialRepository?.delete(id)
        }
    }

    /** Switches the active Orbit: resumes its most recently positioned tab, or opens a fresh home tab in it. */
    fun onSwitchOrbit(id: Long) {
        viewModelScope.launch {
            settings.setActiveOrbitId(id)
            val mostRecentTab = tabs.value.filter { it.orbitId == id }.maxByOrNull { it.position }
            if (mostRecentTab != null) {
                tabManager.switchTo(mostRecentTab.id)
            } else {
                tabManager.newTab(HOME_URL, orbitId = id)
            }
        }
    }

    fun onCreateOrbit(name: String, colorArgb: Int, iconKey: String = "person") {
        viewModelScope.launch {
            orbitRepository?.create(name, colorArgb, System.currentTimeMillis(), iconKey)
        }
    }

    fun onRenameOrbit(id: Long, name: String) {
        viewModelScope.launch { orbitRepository?.rename(id, name) }
    }

    fun onSetOrbitIcon(id: Long, iconKey: String) {
        viewModelScope.launch { orbitRepository?.setIcon(id, iconKey) }
    }

    fun onSetOrbitColor(id: Long, colorArgb: Int) {
        viewModelScope.launch { orbitRepository?.setColor(id, colorArgb) }
    }

    /**
     * Deletes an Orbit (the repo itself refuses — and this is then a no-op — if it's the last
     * one). Order matters here: the new active Orbit is guaranteed a tab of its own AND made the
     * active tab (mirroring [onSwitchOrbit]'s switchTo-or-create logic) BEFORE the deleted
     * Orbit's tabs are closed. Doing this unconditionally — not only when a tab had to be
     * created — matters because [TabClosePolicy.nextActiveId] is position-based and
     * Orbit-unaware: if the active tab were left behind in the Orbit being deleted, closing that
     * Orbit's tabs could hand the active tab to some OTHER surviving Orbit, leaving
     * `activeOrbitId` and the active tab's `orbitId` mismatched. Switching first also means the
     * global tab list is never empty mid-close, which would otherwise trip
     * [TabManager.closeTab]'s empty-list fallback and create an orphaned tab with
     * `orbitId = null` (invisible to every Orbit's filter). The new active Orbit is computed by
     * excluding the just-deleted id from [orbits]'s current value rather than re-querying the
     * repo, since that Flow may not have caught up with the delete yet. The deleted Orbit's
     * profileKey and closed tab ids are only emitted on [orbitProfileToDelete] after the closes;
     * MainActivity still owes a `holder.close(tabId)` per id before the profile is actually
     * deletable, since closing a tab here never touches the native WebView.
     */
    fun onDeleteOrbit(id: Long) {
        val repo = orbitRepository ?: return
        viewModelScope.launch {
            val orbit = repo.get(id) ?: return@launch
            val wasActive = activeOrbitId.value == id
            if (!repo.delete(id)) return@launch

            val newActiveId = if (wasActive) {
                orbits.value.firstOrNull { it.id != id }?.id
            } else {
                activeOrbitId.value
            }

            if (newActiveId != null) {
                val mostRecentTab = tabs.value.filter { it.orbitId == newActiveId }.maxByOrNull { it.position }
                if (mostRecentTab != null) {
                    tabManager.switchTo(mostRecentTab.id)
                } else {
                    tabManager.newTab(HOME_URL, orbitId = newActiveId)
                }
                settings.setActiveOrbitId(newActiveId)
            }

            val deletedOrbitTabIds = tabs.value.filter { it.orbitId == id }.map { it.id }
            deletedOrbitTabIds.forEach { tabManager.closeTab(it, HOME_URL) }

            // Isolation: a deleted Orbit's saved data must not survive it (mirrors the
            // cookie/profile + tab purge). Irreversible, like deleteProfile.
            historyDao.deleteForOrbit(id)
            bookmarkDao.deleteForOrbit(id)
            homeShortcutDao.deleteForOrbit(id)
            credentialRepository?.deleteForOrbit(id)

            _orbitProfileToDelete.emit(OrbitDeletion(orbit.profileKey, deletedOrbitTabIds))
        }
    }

    /** Opens [url] in a fresh tab under [orbitId] and switches the active Orbit to it. */
    fun onOpenLinkInOrbit(url: String, orbitId: Long) {
        viewModelScope.launch {
            settings.setActiveOrbitId(orbitId)
            tabManager.newTab(url, orbitId = orbitId)
        }
    }

    /**
     * Whether the user has dismissed the one-time note that this device's WebView predates true
     * per-Orbit cookie isolation (shown only when [WebViewFeature.MULTI_PROFILE] is unsupported
     * and more than one Orbit exists).
     */
    val seenOrbitProfileNote: StateFlow<Boolean> = settings.seenOrbitProfileNote
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun onOrbitProfileNoteSeen() {
        viewModelScope.launch { settings.setSeenOrbitProfileNote(true) }
    }

    val tabGroups: StateFlow<List<TabGroupEntity>> = tabGroupDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val recentlyClosed: StateFlow<List<ClosedTabEntity>> = closedTabDao.observeRecent(100)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _uiState = MutableStateFlow(BrowserUiState())
    val uiState: StateFlow<BrowserUiState> = _uiState.asStateFlow()

    /** Whether the active tab's current host is in [backgroundMediaSites]. */
    val currentSiteBackgroundAllowed: StateFlow<Boolean> = combine(
        uiState, backgroundMediaSites,
    ) { state, sites ->
        val host = UrlHosts.of(state.currentUrl)
        host != null && host in sites
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // Orbit-scoped (v4.3): the History screen shows only the active Orbit's visits, and
    // re-subscribes whenever the active Orbit changes.
    val historyEntries: StateFlow<List<HistoryEntry>> = activeOrbitId
        .flatMapLatest { orbitId -> historyDao.observeForOrbit(orbitId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Orbit-scoped (v4.4): each Orbit has its own bookmarks; re-subscribes on Orbit switch.
    val bookmarks: StateFlow<List<Bookmark>> = activeOrbitId
        .flatMapLatest { orbitId -> bookmarkDao.observeForOrbit(orbitId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Eagerly: the home page is the first thing rendered, before any subscriber settles.
    // Orbit-scoped (v4.4): each Orbit has its own home grid.
    val homeShortcuts: StateFlow<List<HomeShortcutEntity>> = activeOrbitId
        .flatMapLatest { orbitId -> homeShortcutDao.observeForOrbit(orbitId) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /**
     * Adds a shortcut tile at the end of the home grid. Dedupe by url — the grid is small
     * and duplicate tiles would just waste slots. [onFeedback] gets a toastable message,
     * following the [importBookmarksHtml] / [onSaveForLater] callback pattern.
     */
    fun onAddShortcut(url: String, title: String, onFeedback: (String) -> Unit = {}) {
        val cleanUrl = url.trim()
        if (cleanUrl.isBlank()) {
            onFeedback("Enter a link to add")
            return
        }
        viewModelScope.launch {
            val orbitId = activeOrbitId.value
            val existing = homeShortcutDao.getAllForOrbit(orbitId)
            if (existing.any { it.url == cleanUrl }) {
                onFeedback("Already on your home screen")
                return@launch
            }
            val nextPosition = (existing.maxOfOrNull { it.position } ?: -1) + 1
            homeShortcutDao.insert(
                HomeShortcutEntity(
                    url = cleanUrl,
                    title = title.trim().ifBlank { UrlHosts.of(cleanUrl) ?: cleanUrl },
                    position = nextPosition,
                    orbitId = orbitId,
                )
            )
            onFeedback("Added to home")
        }
    }

    /**
     * Menu action: puts the current page on the home grid. Allowed from incognito too —
     * it's an explicit user action, same policy as downloads and save-for-later.
     */
    fun onAddCurrentPageToHome(onFeedback: (String) -> Unit) {
        val url = _uiState.value.currentUrl
        if (url.isNullOrBlank() || url == HOME_URL) {
            onFeedback("Nothing to add on this page")
            return
        }
        val tabTitle = tabs.value.find { it.id == activeTabId.value }?.title
        onAddShortcut(url, tabTitle ?: "", onFeedback)
    }

    fun onRemoveShortcut(id: Long) {
        viewModelScope.launch { homeShortcutDao.deleteById(id) }
    }

    /** Moves a tile to the first slot; the Orbit's list is rewritten with fresh 0..n positions. */
    fun onMoveShortcutToFront(id: Long) {
        viewModelScope.launch {
            val orbitId = activeOrbitId.value
            val current = homeShortcutDao.getAllForOrbit(orbitId)
            val target = current.find { it.id == id } ?: return@launch
            val reordered = listOf(target) + current.filterNot { it.id == id }
            homeShortcutDao.replaceAllForOrbit(
                orbitId,
                reordered.mapIndexed { index, shortcut -> shortcut.copy(position = index) }
            )
        }
    }

    val downloads: StateFlow<List<DownloadEntry>> = downloadDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Eagerly: the menu's unread badge must be right the first time the menu opens.
    val readingList: StateFlow<List<ReadingListEntry>> = readingListDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val unreadCount: StateFlow<Int> = readingList
        .map { list -> list.count { it.readAt == null } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    // Reader display prefs — shared by the live reader overlay and the saved-article reader.
    val readerFontScale: StateFlow<Int> = settings.readerFontScale
        .stateIn(viewModelScope, SharingStarted.Eagerly, 100)

    val readerTheme: StateFlow<ReaderTheme> = settings.readerTheme
        .stateIn(viewModelScope, SharingStarted.Eagerly, ReaderTheme.SYSTEM)

    val readerWide: StateFlow<Boolean> = settings.readerWide
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun onReaderFontScaleChanged(percent: Int) {
        viewModelScope.launch { settings.setReaderFontScale(percent) }
    }

    fun onReaderThemeSelected(theme: ReaderTheme) {
        viewModelScope.launch { settings.setReaderTheme(theme) }
    }

    fun onReaderWideToggled(wide: Boolean) {
        viewModelScope.launch { settings.setReaderWide(wide) }
    }

    /**
     * First-run gate (J2), tri-state on purpose: null while DataStore hasn't answered yet —
     * MainActivity then renders a plain background-colored frame (never the browser, so the
     * real UI can't flash before onboarding), false shows onboarding, true shows the browser.
     */
    val onboardingDone: StateFlow<Boolean?> = settings.onboardingDone
        .map<Boolean, Boolean?> { it }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Every onboarding exit path (Skip / Get started / Maybe later) lands here — permanent. */
    fun onOnboardingFinished() {
        viewModelScope.launch { settings.setOnboardingDone(true) }
    }

    /** Global page text scale in percent (I3) — the base textZoom for every tab; a positive
     *  per-site override still wins (SiteSettingsResolver). */
    val textScale: StateFlow<Int> = settings.textScale
        .stateIn(viewModelScope, SharingStarted.Eagerly, 100)

    fun onTextScaleChanged(percent: Int) {
        viewModelScope.launch { settings.setTextScale(percent) }
    }

    /** Best asteroid-game score (K1), shown on the game-over overlay. */
    val asteroidHighScore: StateFlow<Int> = settings.asteroidHighScore
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    /** End-of-run report from the asteroid game: persists only a new personal best. */
    fun onAsteroidScore(score: Int) {
        if (score <= asteroidHighScore.value) return
        viewModelScope.launch { settings.setAsteroidHighScore(score) }
    }

    // --- per-site display memory (H6) ---

    /**
     * host -> stored override row. Eager + in-memory so the WebViewHolder's page-start provider
     * lambda is a plain map lookup — it runs inside onPageStarted and must never block.
     */
    val siteSettingsByHost: StateFlow<Map<String, SiteSettingsEntity>> = siteSettingsDao.observeAll()
        .map { list -> list.associateBy { it.host } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /** The stored override row for the active tab's host, or null (drives the site-settings sheet). */
    val siteSettingsForCurrentSite: StateFlow<SiteSettingsEntity?> = combine(
        uiState, siteSettingsByHost,
    ) { state, byHost -> UrlHosts.of(state.currentUrl)?.let { byHost[it] } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** True when the active tab is incognito — site settings must then never touch the DAO. */
    fun isActiveTabIncognito(): Boolean =
        tabs.value.find { it.id == activeTabId.value }?.isIncognito == true

    /**
     * Merges the given fields into the current host's override row (null = leave that field as
     * stored). A row where every field ends up -1 says nothing, so it's deleted rather than kept.
     *
     * Incognito guard (critical): a persisted row would leak which sites were visited privately,
     * so incognito tabs never write — the sheet applies changes to the live WebView only.
     */
    fun onSetSiteOverride(textZoom: Int? = null, forceDark: Int? = null, desktopMode: Int? = null) {
        if (isActiveTabIncognito()) return
        val host = currentHost() ?: return
        viewModelScope.launch {
            val existing = siteSettingsDao.getByHost(host) ?: SiteSettingsEntity(host)
            val merged = existing.copy(
                textZoom = textZoom ?: existing.textZoom,
                forceDark = forceDark ?: existing.forceDark,
                desktopMode = desktopMode ?: existing.desktopMode,
            )
            if (merged.textZoom == -1 && merged.forceDark == -1 && merged.desktopMode == -1) {
                siteSettingsDao.deleteByHost(host)
            } else {
                siteSettingsDao.upsert(merged)
            }
        }
    }

    /** Removes every stored override for the current host. Same incognito guard as [onSetSiteOverride]. */
    fun onClearSiteOverrides() {
        if (isActiveTabIncognito()) return
        val host = currentHost() ?: return
        viewModelScope.launch { siteSettingsDao.deleteByHost(host) }
    }

    // Eagerly: searchEngine.value must be fresh the moment Go is pressed.
    val searchEngine: StateFlow<SearchEngine> = settings.searchEngine
        .stateIn(viewModelScope, SharingStarted.Eagerly, SearchEngine.GOOGLE)

    val themeMode: StateFlow<ThemeMode> = settings.themeMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)

    val javaScriptEnabled: StateFlow<Boolean> = settings.javaScriptEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val cookiesEnabled: StateFlow<Boolean> = settings.cookiesEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val adBlockEnabled: StateFlow<Boolean> = settings.adBlockEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val adAllowedSites: StateFlow<Set<String>> = settings.adAllowedSites
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    /** Ids of the enabled ad/tracker filter lists (Settings > per-list toggles). */
    val adBlockLists: StateFlow<Set<String>> = settings.adBlockLists
        .stateIn(viewModelScope, SharingStarted.Eagerly, FilterLists.DEFAULT_ENABLED_IDS)

    /** Epoch millis of the last successful filter-list update; 0 = never. */
    val adBlockLastUpdated: StateFlow<Long> = settings.adBlockLastUpdated
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0L)

    /** Persists the per-list toggle, then rebuilds the engines from what's now enabled. */
    fun onAdBlockListToggled(id: String) {
        viewModelScope.launch {
            settings.toggleAdBlockList(id)
            reloadAdblock()
        }
    }

    val safeBrowsing: StateFlow<Boolean> = settings.safeBrowsing
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun onSafeBrowsingToggled(enabled: Boolean) {
        viewModelScope.launch { settings.setSafeBrowsing(enabled) }
    }

    val dismissCookieBanners: StateFlow<Boolean> = settings.dismissCookieBanners
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun onDismissCookieBannersToggled(enabled: Boolean) {
        viewModelScope.launch { settings.setDismissCookieBanners(enabled) }
    }

    val gpcEnabled: StateFlow<Boolean> = settings.gpcEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun onGpcToggled(enabled: Boolean) {
        viewModelScope.launch { settings.setGpcEnabled(enabled) }
    }

    /** Persisted lifetime blocked-request count, for the home page stats block (C3). */
    val lifetimeBlocked: StateFlow<Long> = settings.lifetimeBlocked
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0L)

    // ── Home canvas customization (v3.1 Focused home) ──────────────────────
    /** Greeting line ("Good morning" etc.) under the wordmark. Off by default. */
    val showGreeting: StateFlow<Boolean> = settings.showGreeting
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Privacy stats card on home. Off by default; never shown on incognito regardless. */
    val showHomeStats: StateFlow<Boolean> = settings.showHomeStats
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** FEW (one calm row) or MORE (full grid) of home shortcuts. Defaults to FEW. */
    val shortcutDensity: StateFlow<ShortcutDensity> = settings.shortcutDensity
        .stateIn(viewModelScope, SharingStarted.Eagerly, ShortcutDensity.FEW)

    /** Id of a bundled home backdrop ("aurora"/"nebula"), or "" for none. */
    val homeWallpaper: StateFlow<String> = settings.homeWallpaper
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    /** v3.2 feed prefs. */
    val showFeed: StateFlow<Boolean> = settings.showFeed
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val showWeather: StateFlow<Boolean> = settings.showWeather
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val showNews: StateFlow<Boolean> = settings.showNews
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val weatherCity: StateFlow<String> = settings.weatherCity
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val weatherUseLocation: StateFlow<Boolean> = settings.weatherUseLocation
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun onShowFeedToggled(enabled: Boolean) {
        viewModelScope.launch { settings.setShowFeed(enabled) }
    }

    fun onShowWeatherToggled(enabled: Boolean) {
        viewModelScope.launch { settings.setShowWeather(enabled) }
    }

    fun onShowNewsToggled(enabled: Boolean) {
        viewModelScope.launch { settings.setShowNews(enabled) }
    }

    fun onWeatherCityChanged(city: String) {
        viewModelScope.launch { settings.setWeatherCity(city) }
    }

    fun onWeatherUseLocationToggled(enabled: Boolean) {
        viewModelScope.launch { settings.setWeatherUseLocation(enabled) }
    }

    fun onShowGreetingToggled(enabled: Boolean) {
        viewModelScope.launch { settings.setShowGreeting(enabled) }
    }

    fun onShowHomeStatsToggled(enabled: Boolean) {
        viewModelScope.launch { settings.setShowHomeStats(enabled) }
    }

    fun onShortcutDensitySelected(density: ShortcutDensity) {
        viewModelScope.launch { settings.setShortcutDensity(density) }
    }

    fun onHomeWallpaperSelected(id: String) {
        viewModelScope.launch { settings.setHomeWallpaper(id) }
    }

    /**
     * Blocks not yet flushed to DataStore. Atomic because [onRequestBlocked] arrives on
     * WebView's background threads. Flushed every [LIFETIME_FLUSH_BATCH] events (a write
     * per blocked request would be a storm on ad-heavy pages) and in [onCleared].
     */
    private val pendingLifetimeBlocked = java.util.concurrent.atomic.AtomicLong(0)

    /** Per-tab count of requests blocked on the current page load. */
    private val _blockedCounts = MutableStateFlow<Map<Long, Int>>(emptyMap())
    val blockedCounts: StateFlow<Map<Long, Int>> = _blockedCounts.asStateFlow()

    // Orbit-scoped (v4.4): the star reflects whether THIS Orbit has the current page bookmarked.
    val isBookmarked: StateFlow<Boolean> = combine(
        uiState.map { it.currentUrl }.distinctUntilChanged(),
        activeOrbitId,
    ) { url, orbitId -> url to orbitId }
        .flatMapLatest { (url, orbitId) ->
            if (url == null) flowOf(false) else bookmarkDao.observeIsBookmarked(orbitId, url)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    init {
        // Orbits must be resolved BEFORE the first tab is created — otherwise, on a fresh
        // install, tabManager.initialize can create the very first tab with orbitId = null
        // (racing ensureDefault/activeOrbitId in a separate coroutine), leaving it orphaned
        // once the active-Orbit filter ships. So this all runs sequentially in one coroutine:
        // seed the default "Personal" Orbit, resolve (and if unset, persist) activeOrbitId,
        // THEN initialize tabs with that resolved id threaded through.
        viewModelScope.launch {
            val orbit = orbitRepository?.ensureDefault(System.currentTimeMillis())
            val resolvedActiveOrbitId = if (orbit != null) {
                val stored = settings.activeOrbitId.first()
                if (stored == 0L) {
                    settings.setActiveOrbitId(orbit.id)
                    orbit.id
                } else {
                    stored
                }
            } else {
                null
            }
            tabManager.defaultOrbitId = resolvedActiveOrbitId
            tabManager.initialize(HOME_URL, orbitId = resolvedActiveOrbitId)
        }
        // Keeps TabManager's fallback-tab hint in sync with whichever Orbit is active, so any
        // fallback/initial home tab it auto-creates on its own initiative (closeTab's empty-list
        // fallback, initialize's empty-DB tab) lands in the current Orbit instead of orbitId =
        // null — see TabManager.defaultOrbitId's doc for why a null Orbit is a bug, not a no-op.
        viewModelScope.launch {
            activeOrbitId.collect { tabManager.defaultOrbitId = it }
        }
        // A tab switch always brings the command bar back (auto-hide state is per-moment,
        // not per-tab; the freshly shown tab starts with a visible bar, like Chrome).
        viewModelScope.launch {
            activeTabId.collect { onBarShouldShow() }
        }
        // Keep the address bar in sync with whichever tab is active.
        viewModelScope.launch {
            combine(tabManager.tabs, tabManager.activeTabId) { tabs, active ->
                tabs.find { it.id == active }
            }.distinctUntilChanged().collect { tab ->
                if (tab != null) {
                    if (tab.url == HOME_URL) {
                        _uiState.update {
                            it.copy(
                                currentUrl = null, addressBarText = "",
                                isLoading = false, canGoBack = false, canGoForward = false,
                            )
                        }
                    } else {
                        _uiState.update { it.copy(currentUrl = tab.url, addressBarText = tab.url) }
                    }
                }
            }
        }
    }

    // --- tab events ---

    fun onNewTab() {
        viewModelScope.launch { tabManager.newTab(HOME_URL, orbitId = activeOrbitId.value) }
    }

    fun onNewIncognitoTab() {
        viewModelScope.launch { tabManager.newTab(HOME_URL, incognito = true) }
    }

    fun onCloseTab(id: Long) {
        val tab = tabs.value.find { it.id == id }
        if (tab?.locked == true) {
            _uiState.update { it.copy(confirmCloseTabId = id) }
            return
        }
        clearSafeBrowsingWarningFor(id)
        viewModelScope.launch { tabManager.closeTab(id, HOME_URL) }
    }

    fun onConfirmClose() {
        val id = _uiState.value.confirmCloseTabId ?: return
        _uiState.update { it.copy(confirmCloseTabId = null) }
        clearSafeBrowsingWarningFor(id)
        viewModelScope.launch { tabManager.closeTab(id, HOME_URL) }
    }

    fun onCloseCancelled() = _uiState.update { it.copy(confirmCloseTabId = null) }

    /** Closes multiple tabs at once (e.g. "close group"); locked tabs are skipped. */
    fun onCloseTabs(ids: List<Long>) {
        val closable = ids.filter { id -> tabs.value.find { it.id == id }?.locked != true }
        closable.forEach(::clearSafeBrowsingWarningFor)
        viewModelScope.launch {
            closable.forEach { tabManager.closeTab(it, HOME_URL) }
        }
    }

    /** A closing tab takes its Safe Browsing interstitial down with it. */
    private fun clearSafeBrowsingWarningFor(tabId: Long) {
        if (_uiState.value.safeBrowsingWarning?.tabId == tabId) {
            _uiState.update { it.copy(safeBrowsingWarning = null) }
        }
    }

    fun onReopenClosed(entry: ClosedTabEntity) {
        viewModelScope.launch {
            tabManager.newTab(entry.url, orbitId = activeOrbitId.value)
            closedTabDao.deleteById(entry.id)
        }
    }

    // --- tab groups ---

    /**
     * Creates a group and assigns [tabIds] to it. Incognito tabs join in-memory only
     * ([TabManager] never persists negative ids), so a selection with NO regular tab must not
     * create the group at all — it would persist an empty group row that outlives the
     * incognito session. [onFeedback] gets a toastable message on that no-op.
     */
    fun onCreateGroupWithTabs(name: String, tabIds: List<Long>, onFeedback: (String) -> Unit = {}) {
        val hasPersistableTab = tabIds.any { id -> tabs.value.find { it.id == id }?.isIncognito != true }
        if (!hasPersistableTab) {
            onFeedback("Incognito tabs can't form a group")
            return
        }
        viewModelScope.launch {
            val group = TabGroupEntity(
                name = name,
                color = tabGroups.value.size % 6,
                position = tabGroups.value.size,
            )
            val newGroupId = tabGroupDao.insert(group)
            tabIds.forEach { tabManager.setGroup(it, newGroupId) }
        }
    }

    fun onRenameGroup(id: Long, name: String) {
        viewModelScope.launch { tabGroupDao.rename(id, name) }
    }

    fun onDeleteGroup(id: Long) {
        viewModelScope.launch {
            tabDao.clearGroup(id)
            tabGroupDao.deleteById(id)
            tabs.value.filter { it.groupId == id }.forEach { tabManager.setGroup(it.id, null) }
        }
    }

    fun onAssignTabToGroup(tabId: Long, groupId: Long?) {
        viewModelScope.launch { tabManager.setGroup(tabId, groupId) }
    }

    fun onTogglePinned(tabId: Long) {
        val pinned = tabs.value.find { it.id == tabId }?.pinned == true
        viewModelScope.launch { tabManager.setPinned(tabId, !pinned) }
    }

    fun onToggleLocked(tabId: Long) {
        val locked = tabs.value.find { it.id == tabId }?.locked == true
        viewModelScope.launch { tabManager.setLocked(tabId, !locked) }
    }

    fun onSwitchTab(id: Long) {
        viewModelScope.launch { tabManager.switchTo(id) }
    }

    /** Swipe on the Command Bar: move to the neighbor tab in list order. */
    fun onSwitchAdjacentTab(next: Boolean) {
        val list = tabs.value
        val index = list.indexOfFirst { it.id == activeTabId.value }
        if (index == -1) return
        val target = if (next) list.getOrNull(index + 1) else list.getOrNull(index - 1)
        target?.let { onSwitchTab(it.id) }
    }

    // --- settings events ---

    fun onSearchEngineSelected(engine: SearchEngine) {
        viewModelScope.launch { settings.setSearchEngine(engine) }
    }

    fun onThemeSelected(mode: ThemeMode) {
        viewModelScope.launch { settings.setThemeMode(mode) }
    }

    fun onJavaScriptToggled(enabled: Boolean) {
        viewModelScope.launch { settings.setJavaScriptEnabled(enabled) }
    }

    fun onCookiesToggled(enabled: Boolean) {
        viewModelScope.launch { settings.setCookiesEnabled(enabled) }
    }

    fun onAdBlockToggled(enabled: Boolean) {
        viewModelScope.launch { settings.setAdBlockEnabled(enabled) }
    }

    /** Host of the page in the active tab, or null (home tab / malformed url). */
    fun currentHost(): String? = UrlHosts.of(_uiState.value.currentUrl)

    fun onToggleAllowAdsOnCurrentSite() {
        val host = currentHost() ?: return
        viewModelScope.launch { settings.toggleAdAllowedSite(host) }
    }

    /** Called from WebView background threads — StateFlow.update is atomic. */
    fun onRequestBlocked(tabId: Long) {
        _blockedCounts.update { it + (tabId to (it[tabId] ?: 0) + 1) }
        // Lifetime aggregate (C3). Incognito blocks count too: the total is a single number
        // with no per-site breakdown, so it leaks nothing about what was browsed privately.
        if (pendingLifetimeBlocked.incrementAndGet() >= LIFETIME_FLUSH_BATCH) {
            val delta = pendingLifetimeBlocked.getAndSet(0)
            if (delta > 0) viewModelScope.launch { settings.addBlockedCount(delta) }
        }
    }

    override fun onCleared() {
        // Last flush of the not-yet-persisted remainder. viewModelScope may already be
        // cancelled at this point, so run the write on an independent, non-cancellable job.
        val delta = pendingLifetimeBlocked.getAndSet(0)
        if (delta > 0) {
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.NonCancellable + ioDispatcher).launch {
                settings.addBlockedCount(delta)
            }
        }
        super.onCleared()
    }

    // --- events from the UI ---

    fun onAddressBarTextChanged(text: String) {
        _uiState.update { it.copy(addressBarText = text) }
        suggestionJob?.cancel()
        if (text.isBlank()) {
            _suggestions.value = emptyList()
            return
        }
        suggestionJob = viewModelScope.launch {
            delay(200) // debounce typing
            _suggestions.value = suggestionEngine.suggest(text, activeOrbitId.value)
        }
    }

    fun onSuggestionsDismissed() {
        suggestionJob?.cancel()
        _suggestions.value = emptyList()
    }

    fun onSuggestionPicked(suggestion: Suggestion) {
        when (suggestion.kind) {
            SuggestionKind.SEARCH ->
                onOpenUrl(UrlInput.toLoadableUrl(suggestion.title, searchEngine.value.queryUrl))
            else -> onOpenUrl(suggestion.url)
        }
        onSuggestionsDismissed()
    }

    /** A link arriving from another app always opens in a fresh normal tab. */
    fun onExternalUrl(url: String) {
        viewModelScope.launch { tabManager.newTab(url, incognito = false, orbitId = activeOrbitId.value) }
    }

    // --- find in page ---

    fun onFindOpen() {
        onBarShouldShow()
        _uiState.update { it.copy(findQuery = "") }
    }

    fun onFindQueryChanged(query: String) = _uiState.update { it.copy(findQuery = query) }

    fun onFindClose() = _uiState.update { it.copy(findQuery = null, findActive = 0, findTotal = 0) }

    fun onFindResult(tabId: Long, ordinal: Int, total: Int) {
        if (tabId == activeTabId.value) {
            _uiState.update { it.copy(findActive = ordinal, findTotal = total) }
        }
    }

    /** Flips desktop-site mode for the active tab; returns the new state. */
    fun onToggleDesktopSite(): Boolean {
        val id = activeTabId.value ?: return false
        val enabled = id !in _desktopTabs.value
        _desktopTabs.update { if (enabled) it + id else it - id }
        return enabled
    }

    /**
     * A home tab has no WebView mounted, so commands can't reach it —
     * rewriting the tab's url mounts the WebView, which then loads it.
     */
    private fun loadInActiveTab(url: String) {
        _readerActive.value = false
        onBarShouldShow()
        val tab = tabs.value.find { it.id == activeTabId.value }
        if (tab != null && tab.url == HOME_URL) {
            viewModelScope.launch { tabManager.onContentChanged(tab.id, url, url) }
        } else {
            _uiState.update { it.copy(pendingCommand = BrowserCommand.LoadUrl(url)) }
        }
    }

    fun onGoPressed() =
        loadInActiveTab(UrlInput.toLoadableUrl(_uiState.value.addressBarText, searchEngine.value.queryUrl))

    fun onOpenUrl(url: String) = loadInActiveTab(url)

    fun onBackPressed() = _uiState.update { it.copy(pendingCommand = BrowserCommand.GoBack) }
    fun onForwardPressed() = _uiState.update { it.copy(pendingCommand = BrowserCommand.GoForward) }
    fun onReloadPressed() = _uiState.update { it.copy(pendingCommand = BrowserCommand.Reload) }

    fun onCommandConsumed() = _uiState.update { it.copy(pendingCommand = null) }

    fun onToggleBookmark() {
        val state = _uiState.value
        val url = state.currentUrl ?: return
        val orbitId = activeOrbitId.value
        viewModelScope.launch {
            if (isBookmarked.value) {
                bookmarkDao.deleteByUrl(orbitId, url)
            } else {
                bookmarkDao.insert(
                    Bookmark(
                        url = url,
                        title = state.addressBarText,
                        createdAt = System.currentTimeMillis(),
                        orbitId = orbitId,
                    )
                )
            }
        }
    }

    fun onDeleteHistoryEntry(id: Long) {
        viewModelScope.launch { historyDao.deleteById(id) }
    }

    /** Clears the active Orbit's history only (the History screen is Orbit-scoped in v4.3). */
    fun onClearHistory() {
        viewModelScope.launch { historyDao.clearForOrbit(activeOrbitId.value) }
    }

    /** Clears history across every Orbit — the Settings "Clear browsing data" (all-Orbits) path. */
    fun onClearAllHistory() {
        viewModelScope.launch { historyDao.clearAll() }
    }

    /**
     * Black Hole — the panic wipe. Erases EVERY browsing trace: all Room tables (across every
     * Orbit + incognito), on-disk files (downloads, saved articles, thumbnails), and the
     * browsing-trace preference keys. Then emits [blackHoleReady] so MainActivity tears down the
     * native WebViews, deletes every profile (cookies/DOM storage), and restarts the process —
     * a cold start re-seeds a clean default Orbit and a single home tab. Irreversible; no undo.
     */
    fun onBlackHole() {
        viewModelScope.launch {
            // Capture profile keys BEFORE the orbits table is wiped; add the fixed incognito one.
            val profileKeys = orbits.value.map { it.profileKey } + INCOGNITO_PROFILE_KEY

            // Stop any in-flight downloads so nothing rewrites a file we're about to delete.
            runCatching { downloadDao.getActive().forEach { downloadController.cancel(it.id) } }

            // Delete on-disk trace files first (their paths live in rows we're about to drop).
            withContext(ioDispatcher) {
                runCatching {
                    downloadDao.observeAll().first().forEach { entry ->
                        entry.filePath?.let { path -> java.io.File(path).delete() }
                    }
                }
                runCatching { articleStore.clearAll() }
            }

            // Wipe every table. rss_sources (seeded presets) are intentionally kept.
            historyDao.clearAll()
            bookmarkDao.clearAll()
            homeShortcutDao.clearAll()
            downloadDao.clearAll()
            readingListDao.clearAll()
            closedTabDao.clear()
            tabGroupDao.clearAll()
            siteSettingsDao.clearAll()
            zapRepository?.clearAll()
            faviconRepository?.clearAll()
            feedRepository?.clearItems()
            // User-added RSS subscriptions are an interest trace; seeded presets are kept.
            feedRepository?.clearCustomSources()
            credentialRepository?.clearAll()
            tabDao.clearAll()
            orbitRepository?.clearAll()

            // Reset browsing-trace preferences (active orbit re-resolves on cold start).
            settings.setActiveOrbitId(0L)
            settings.clearAdAllowedSites()
            settings.setBackgroundMediaSites(emptySet())
            // Location + aggregate stats are traces too on a "clean slate".
            settings.setWeatherCity("")
            settings.setWeatherUseLocation(false)
            settings.setWeatherCache("")
            settings.resetLifetimeBlocked()

            // Hand the native teardown + process restart to MainActivity.
            _blackHoleReady.emit(profileKeys)
        }
    }

    fun onDeleteBookmark(url: String) {
        val orbitId = activeOrbitId.value
        viewModelScope.launch { bookmarkDao.deleteByUrl(orbitId, url) }
    }

    /** Returns Netscape-format HTML of the active Orbit's bookmarks for export. */
    suspend fun exportBookmarksHtml(): String =
        com.udaytank.browse.browser.BookmarkIO.export(bookmarkDao.getAllForOrbit(activeOrbitId.value))

    /** Imports bookmarks from a Netscape HTML file into the active Orbit; returns how many were added. */
    fun importBookmarksHtml(html: String, onDone: (Int) -> Unit) {
        val orbitId = activeOrbitId.value
        viewModelScope.launch {
            val parsed = com.udaytank.browse.browser.BookmarkIO.parse(html, System.currentTimeMillis())
            parsed.forEach { bookmarkDao.insert(it.copy(orbitId = orbitId)) }
            onDone(parsed.size)
        }
    }

    // --- backup & restore (J1) ---

    /**
     * The explicit list of settings a backup carries. Values are their string forms (enum
     * names / toString), decoded leniently on restore so unknown values are skipped, not
     * fatal. onboardingDone is deliberately absent — first-run state is device-local.
     */
    private suspend fun settingsSnapshot(): Map<String, String> = mapOf(
        "searchEngine" to settings.searchEngine.first().name,
        "themeMode" to settings.themeMode.first().name,
        "javaScriptEnabled" to settings.javaScriptEnabled.first().toString(),
        "cookiesEnabled" to settings.cookiesEnabled.first().toString(),
        "adBlockEnabled" to settings.adBlockEnabled.first().toString(),
        "forceDarkWebsites" to settings.forceDarkWebsites.first().toString(),
        "httpsOnly" to settings.httpsOnly.first().toString(),
        "lockIncognito" to settings.lockIncognito.first().toString(),
        "lockPasswords" to settings.lockPasswords.first().toString(),
        "autoIslands" to settings.autoIslands.first().toString(),
        "switcherListLayout" to settings.switcherListLayout.first().toString(),
        "backgroundMedia" to settings.backgroundMedia.first().toString(),
        "readerFontScale" to settings.readerFontScale.first().toString(),
        "textScale" to settings.textScale.first().toString(),
        "readerTheme" to settings.readerTheme.first().name,
        "readerWide" to settings.readerWide.first().toString(),
        "safeBrowsing" to settings.safeBrowsing.first().toString(),
        "dismissCookieBanners" to settings.dismissCookieBanners.first().toString(),
        "gpcEnabled" to settings.gpcEnabled.first().toString(),
    )

    /**
     * Serializes everything backup-worthy to the versioned JSON format. Pure data out — the
     * caller (Settings screen) owns the SAF stream plumbing, keeping this unit-testable.
     */
    suspend fun buildBackupJson(): String = BackupCodec.encode(
        Backup(
            settings = settingsSnapshot(),
            bookmarks = bookmarkDao.getAll(),
            homeShortcuts = homeShortcutDao.getAll(),
            readingList = readingListDao.observeAll().first(),
            tabGroups = tabGroupDao.getAll(),
        )
    )

    /**
     * Merges a decoded backup into the live data (never wipes): bookmarks / shortcuts /
     * reading-list rows dedupe by url, tab groups by name, settings overwrite. Restored
     * shortcuts and groups append after the existing ones; reading-list rows come back as
     * metadata only (no offline copy). [onFeedback] gets a toastable per-section count line.
     */
    fun onRestoreBackup(backup: Backup, onFeedback: (String) -> Unit) {
        viewModelScope.launch {
            applyRestoredSettings(backup.settings)

            // Orbit ids are device-local and can't be assumed to match across installs, so a
            // restore imports bookmarks/shortcuts into the CURRENT Orbit, deduping against its
            // own rows only (v4.4).
            val orbitId = activeOrbitId.value

            // Whole-DB backups can contain the same URL from several Orbits; since restore folds
            // everything into the active Orbit, dedupe against a set that grows AS we insert (not
            // a snapshot taken once) so the count is honest and no duplicate tile/row is written.
            val seenBookmarkUrls = bookmarkDao.getAllForOrbit(orbitId).map { it.url }.toMutableSet()
            var bookmarksAdded = 0
            backup.bookmarks.forEach { bookmark ->
                if (seenBookmarkUrls.add(bookmark.url)) {
                    bookmarkDao.insert(bookmark.copy(id = 0, orbitId = orbitId))
                    bookmarksAdded++
                }
            }

            val currentShortcuts = homeShortcutDao.getAllForOrbit(orbitId)
            val seenShortcutUrls = currentShortcuts.map { it.url }.toMutableSet()
            var nextPosition = (currentShortcuts.maxOfOrNull { it.position } ?: -1) + 1
            var shortcutsAdded = 0
            backup.homeShortcuts.sortedBy { it.position }.forEach { shortcut ->
                if (seenShortcutUrls.add(shortcut.url)) {
                    homeShortcutDao.insert(shortcut.copy(id = 0, position = nextPosition++, orbitId = orbitId))
                    shortcutsAdded++
                }
            }

            var readingAdded = 0
            backup.readingList.forEach { entry ->
                if (!readingListDao.existsByUrl(entry.url)) {
                    readingListDao.insert(entry.copy(id = 0, filePath = null))
                    readingAdded++
                }
            }

            val existingGroupNames = tabGroupDao.getAll().map { it.name }.toSet()
            var groupPosition = tabGroupDao.getAll().size
            var groupsAdded = 0
            backup.tabGroups.sortedBy { it.position }.forEach { group ->
                if (group.name !in existingGroupNames) {
                    tabGroupDao.insert(group.copy(id = 0, position = groupPosition++))
                    groupsAdded++
                }
            }

            onFeedback(
                "Restored $bookmarksAdded bookmarks, $shortcutsAdded shortcuts, " +
                    "$readingAdded reading list items, $groupsAdded tab groups"
            )
        }
    }

    /** Lenient per-key apply: unknown or unparseable values are skipped, never fatal. */
    private suspend fun applyRestoredSettings(map: Map<String, String>) {
        map["searchEngine"]?.let { v -> SearchEngine.entries.find { it.name == v } }
            ?.let { settings.setSearchEngine(it) }
        map["themeMode"]?.let { v -> ThemeMode.entries.find { it.name == v } }
            ?.let { settings.setThemeMode(it) }
        map["javaScriptEnabled"]?.toBooleanStrictOrNull()?.let { settings.setJavaScriptEnabled(it) }
        map["cookiesEnabled"]?.toBooleanStrictOrNull()?.let { settings.setCookiesEnabled(it) }
        map["adBlockEnabled"]?.toBooleanStrictOrNull()?.let { settings.setAdBlockEnabled(it) }
        map["forceDarkWebsites"]?.toBooleanStrictOrNull()?.let { settings.setForceDarkWebsites(it) }
        map["httpsOnly"]?.toBooleanStrictOrNull()?.let { settings.setHttpsOnly(it) }
        map["lockIncognito"]?.toBooleanStrictOrNull()?.let { settings.setLockIncognito(it) }
        map["lockPasswords"]?.toBooleanStrictOrNull()?.let { settings.setLockPasswords(it) }
        map["autoIslands"]?.toBooleanStrictOrNull()?.let { settings.setAutoIslands(it) }
        map["switcherListLayout"]?.toBooleanStrictOrNull()?.let { settings.setSwitcherListLayout(it) }
        map["backgroundMedia"]?.toBooleanStrictOrNull()?.let { settings.setBackgroundMedia(it) }
        map["readerFontScale"]?.toIntOrNull()?.let { settings.setReaderFontScale(it) }
        map["textScale"]?.toIntOrNull()?.let { settings.setTextScale(it) }
        map["readerTheme"]?.let { v -> ReaderTheme.entries.find { it.name == v } }
            ?.let { settings.setReaderTheme(it) }
        map["readerWide"]?.toBooleanStrictOrNull()?.let { settings.setReaderWide(it) }
        map["safeBrowsing"]?.toBooleanStrictOrNull()?.let { settings.setSafeBrowsing(it) }
        map["dismissCookieBanners"]?.toBooleanStrictOrNull()?.let { settings.setDismissCookieBanners(it) }
        map["gpcEnabled"]?.toBooleanStrictOrNull()?.let { settings.setGpcEnabled(it) }
    }

    // --- reading list (save for later) ---

    /**
     * Saves the active page to the reading list and tries to capture an offline copy.
     *
     * [extract] runs [com.udaytank.browse.browser.ReaderMode.EXTRACT_SCRIPT] in the tab's live
     * WebView and hands back the raw evaluateJavascript payload (the UI passes
     * `holder::extractReaderContent`; tests pass a fake). It must be invoked on the main
     * thread — which this is, since the VM's scope runs on Main — while file and DB work hops
     * to [ioDispatcher]. Extraction failure still saves the row, just without an offline copy.
     *
     * Deliberately no incognito guard: saving is an explicit user action (same policy as
     * downloads), and reading-list rows carry no browsing trace beyond the page the user chose.
     *
     * [onFeedback] receives a user-facing message (toast), following [importBookmarksHtml].
     */
    fun onSaveForLater(
        extract: (Long, (String) -> Unit) -> Unit,
        onFeedback: (String) -> Unit,
    ) {
        val tabId = activeTabId.value
        val url = _uiState.value.currentUrl
        if (tabId == null || url.isNullOrBlank() || url == HOME_URL) {
            onFeedback("Nothing to save on this page")
            return
        }
        val tabTitle = tabs.value.find { it.id == tabId }?.title
        val title = tabTitle?.takeIf { it.isNotBlank() } ?: UrlHosts.of(url) ?: url
        viewModelScope.launch {
            if (readingListDao.existsByUrl(url)) {
                onFeedback("Already in your reading list")
                return@launch
            }
            val rowId = readingListDao.insert(
                ReadingListEntry(url = url, title = title, addedAt = System.currentTimeMillis())
            )
            extract(tabId) { json ->
                val result = ReaderExtraction.parse(json)
                viewModelScope.launch(ioDispatcher) {
                    val message = if (result != null) {
                        val path = articleStore.save(rowId, result.content)
                        readingListDao.setFilePath(rowId, path)
                        "Saved for offline reading"
                    } else {
                        "Saved (couldn't make offline copy)"
                    }
                    withContext(Dispatchers.Main) { onFeedback(message) }
                }
            }
        }
    }

    /** The last deleted row plus its offline HTML, held so the undo snackbar can restore both. */
    private var lastDeletedReadingItem: Pair<ReadingListEntry, String?>? = null

    fun onDeleteReadingItem(id: Long) {
        viewModelScope.launch(ioDispatcher) {
            val entry = readingListDao.getById(id) ?: return@launch
            lastDeletedReadingItem = entry to entry.filePath?.let { articleStore.load(it) }
            articleStore.delete(entry.filePath)
            readingListDao.deleteById(id)
        }
    }

    /**
     * Undo for [onDeleteReadingItem]: re-inserts the last deleted row with its original fields
     * (addedAt, readAt) and re-saves the offline copy captured before the delete, so an undone
     * article stays readable offline.
     */
    fun onReopenReadingItem() {
        val (entry, offlineHtml) = lastDeletedReadingItem ?: return
        lastDeletedReadingItem = null
        viewModelScope.launch(ioDispatcher) {
            val rowId = readingListDao.insert(entry.copy(id = 0, filePath = null))
            if (offlineHtml != null) {
                readingListDao.setFilePath(rowId, articleStore.save(rowId, offlineHtml))
            }
        }
    }

    /**
     * Opening an item marks it read. Online-only rows (no offline copy) also navigate the
     * active tab to the original url; offline rows render in the saved-article reader, which
     * the reading-list screen drives itself.
     */
    fun onOpenReadingItem(entry: ReadingListEntry) {
        onMarkRead(entry.id, true)
        if (entry.filePath == null) onOpenUrl(entry.url)
    }

    /** Loads a saved article's content HTML off the main thread; null when the file is gone. */
    suspend fun loadSavedArticle(path: String): String? =
        withContext(ioDispatcher) { articleStore.load(path) }

    fun onMarkRead(id: Long, read: Boolean) {
        viewModelScope.launch {
            readingListDao.setReadAt(id, if (read) System.currentTimeMillis() else null)
        }
    }

    // --- callbacks from the WebViews (any tab) ---

    fun onPageStarted(tabId: Long, url: String) {
        viewModelScope.launch { tabManager.onContentChanged(tabId, url, url) }
        _blockedCounts.update { it + (tabId to 0) } // fresh page, fresh counter
        // Any navigation in the flagged tab (typed url, back/forward, reload) supersedes its
        // Safe Browsing interstitial — mirrors the holder dropping the stored callback.
        if (_uiState.value.safeBrowsingWarning?.tabId == tabId) {
            _uiState.update { it.copy(safeBrowsingWarning = null) }
        }
        if (tabId == activeTabId.value) {
            onBarShouldShow() // navigation start re-reveals the auto-hidden bar
            _fillPrompt.value = null // a new page supersedes any pending fill offer
            _uiState.update {
                it.copy(currentUrl = url, addressBarText = url, isLoading = true, progress = 0, pageError = null)
            }
        }
    }

    fun onProgressChanged(tabId: Long, percent: Int) {
        if (tabId == activeTabId.value) _uiState.update { it.copy(progress = percent) }
    }

    fun onPageFinished(tabId: Long, url: String, title: String?) {
        viewModelScope.launch { tabManager.onContentChanged(tabId, url, title ?: url) }
        if (tabId == activeTabId.value) _uiState.update { it.copy(isLoading = false) }

        // Incognito visits leave no trace in history.
        val tab = tabs.value.find { it.id == tabId }
        if (tab?.isIncognito == true) return
        // Record against the owning tab's Orbit (not the global active id): correct even for a
        // late/background onPageFinished after the user has switched Orbits. Fall back to the
        // active Orbit only if the tab has somehow no Orbit (should not happen for non-incognito).
        val orbitId = tab?.orbitId ?: activeOrbitId.value

        viewModelScope.launch {
            val now = System.currentTimeMillis()
            when (val decision = VisitPolicy.decide(historyDao.mostRecent(orbitId), url)) {
                VisitDecision.Skip -> Unit
                VisitDecision.RecordNew ->
                    historyDao.insert(
                        HistoryEntry(url = url, title = title ?: url, visitedAt = now, orbitId = orbitId)
                    )
                is VisitDecision.RefreshExisting ->
                    historyDao.updateVisitedAt(decision.existingId, now)
            }
        }

        // v4.7 Passwords: if the active tab's HTTPS page has saved logins for its host, offer to
        // fill. HTTPS-only — never inject a credential into a cleartext (MITM-able) page.
        val repo = credentialRepository
        if (repo != null && tabId == activeTabId.value && url.startsWith("https://", ignoreCase = true)) {
            viewModelScope.launch {
                val host = UrlHosts.of(url)
                val usernames = if (host.isNullOrBlank()) emptyList()
                else repo.credentialsForHost(orbitId, host).map { it.username }
                _fillPrompt.value =
                    if (usernames.isNotEmpty() && host != null) FillPromptState(tabId, orbitId, host, usernames)
                    else null
            }
        }
    }

    fun onSslError(tabId: Long, url: String) {
        if (tabId == activeTabId.value) {
            _uiState.update { it.copy(sslWarningUrl = url, isLoading = false) }
        }
    }

    fun onSslWarningDismissed() = _uiState.update { it.copy(sslWarningUrl = null) }

    /** Safe Browsing flagged a main-frame load; the interstitial replaces the content area (D1). */
    fun onSafeBrowsingHit(tabId: Long, url: String, threatLabel: String) {
        _uiState.update {
            it.copy(safeBrowsingWarning = SafeBrowsingWarning(tabId, url, threatLabel), isLoading = false)
        }
    }

    /**
     * The interstitial was resolved (either button) — the UI has already handed the decision to
     * the WebViewHolder's stored callback; here we just take the warning down.
     */
    fun onSafeBrowsingResolved() = _uiState.update { it.copy(safeBrowsingWarning = null) }

    fun onPageError(tabId: Long, description: String) {
        if (tabId == activeTabId.value) {
            _uiState.update { it.copy(pageError = description, isLoading = false) }
        }
    }

    fun onRetryPressed() {
        _uiState.update { it.copy(pageError = null) }
        onReloadPressed()
    }

    fun onLongPress(tabId: Long, url: String, isImage: Boolean) {
        if (tabId == activeTabId.value) {
            _uiState.update { it.copy(contextMenu = LinkContextMenu(url, isImage)) }
        }
    }

    fun onContextMenuDismissed() = _uiState.update { it.copy(contextMenu = null) }

    /**
     * Called from the download listener when the system DownloadManager accepts a file. This
     * hidden legacy path has no progress polling, so the row can't be driven through the normal
     * RUNNING -> DONE lifecycle; it's inserted RUNNING and simply stays that way (the Downloads
     * screen renders legacy rows, identified by [DownloadEntry.downloadId] > 0, with a "Managed
     * by system downloader" line and DONE-style actions instead of progress UI).
     */
    fun onDownloadStarted(downloadId: Long, fileName: String, url: String) {
        viewModelScope.launch {
            downloadDao.insert(
                DownloadEntry(
                    downloadId = downloadId,
                    fileName = fileName,
                    url = url,
                    createdAt = System.currentTimeMillis(),
                    state = "RUNNING",
                )
            )
        }
    }

    /** Called from the download listener when the engine (non-system-downloader path) wants to start a download. */
    fun onDownloadRequested(url: String, fileName: String, mimeType: String?, userAgent: String?) {
        _uiState.update { it.copy(downloadPrompt = DownloadPrompt(url, fileName, mimeType, userAgent)) }
    }

    fun onDownloadPromptDismissed() = _uiState.update { it.copy(downloadPrompt = null) }

    fun onDeleteDownload(id: Long) {
        downloadController.cancel(id)
        viewModelScope.launch {
            val entry = downloadDao.getById(id)
            val path = entry?.filePath
            if (path != null) {
                runCatching { java.io.File(path).delete() }
            } else if (entry != null && entry.downloadId > 0) {
                // Legacy system-downloader row: we never got a filePath, so the only way to
                // clean up is to ask the system DownloadManager to remove it by its own id.
                runCatching { downloadManagerRemover(entry.downloadId) }
            }
            downloadDao.deleteById(id)
        }
    }

    /**
     * Requests a new engine-backed download. [userAgent] isn't persisted yet — [DownloadEntry]
     * has no column for it — so a fresh service-driven start currently probes without one; wiring
     * it through is left for whenever that column lands.
     */
    fun onStartDownload(
        url: String,
        suggestedName: String,
        mimeType: String?,
        userAgent: String?,
        constraint: DownloadWhen,
    ) {
        viewModelScope.launch {
            val state = if (constraint == DownloadWhen.NOW) "PENDING" else "SCHEDULED"
            val id = downloadDao.insertReturning(
                DownloadEntry(
                    fileName = suggestedName,
                    url = url,
                    createdAt = System.currentTimeMillis(),
                    mimeType = mimeType,
                    state = state,
                )
            )
            if (constraint == DownloadWhen.NOW) {
                downloadController.startDownload(id)
            } else {
                downloadController.schedule(id, constraint)
            }
        }
    }

    fun onPauseDownload(id: Long) = downloadController.pause(id)

    fun onResumeDownload(id: Long) = downloadController.resume(id)

    fun onCancelDownload(id: Long) = downloadController.cancel(id)

    fun onRetryDownload(id: Long) {
        viewModelScope.launch {
            downloadDao.resetAttempts(id)
            downloadDao.setState(id, "PENDING")
            downloadController.startDownload(id)
        }
    }

    /** v5.2 QR: search scanned plain text with the user's engine, in a new tab. */
    fun onSearchFromQr(text: String) {
        onOpenInNewTab(UrlInput.toLoadableUrl(text, searchEngine.value.queryUrl))
    }

    /** New tabs opened from a page inherit that page's incognito mode and, when auto-islands is on, its group. */
    fun onOpenInNewTab(url: String) {
        val parent = tabs.value.find { it.id == activeTabId.value }
        val incognito = parent?.isIncognito == true
        val groupId = TabGroupPolicy.groupForNewTab(parent, autoIslands.value)
        viewModelScope.launch { tabManager.newTab(url, incognito, groupId, orbitId = activeOrbitId.value) }
        onContextMenuDismissed()
    }

    /**
     * A page opened a popup — target="_blank" or gesture-backed window.open — and the holder
     * captured its first URL (v5.0). Open it as a real tab inheriting the PARENT tab's context:
     * its incognito mode (an incognito page's popup must stay incognito), its Orbit, and its
     * island. Keyed on [parentTabId], not the active tab — the capture is asynchronous and the
     * user could have switched tabs; if the parent is already gone, fall back to the active tab.
     */
    fun onPopupWindow(parentTabId: Long, url: String) {
        val parent = tabs.value.find { it.id == parentTabId }
            ?: tabs.value.find { it.id == activeTabId.value }
        val incognito = parent?.isIncognito == true
        val groupId = TabGroupPolicy.groupForNewTab(parent, autoIslands.value)
        val orbitId = if (incognito) null else (parent?.orbitId ?: activeOrbitId.value)
        // Foreground only while the popup's parent is still the tab the user is looking at.
        // Activating a tab from another Orbit/mode would break the active-tab ↔ active-Orbit
        // invariant (the switcher filters by activeOrbitId — the active tab would vanish).
        val foreground = parent?.id == activeTabId.value
        viewModelScope.launch {
            tabManager.newTab(url, incognito, groupId, orbitId = orbitId, foreground = foreground)
        }
    }

    fun onTitleUpdated(tabId: Long, url: String, title: String) {
        viewModelScope.launch {
            tabManager.onContentChanged(tabId, url, title)
            val tab = tabs.value.find { it.id == tabId }
            if (tab?.isIncognito != true) historyDao.updateTitleForUrl(url, title)
        }
    }

    fun onHistoryChanged(tabId: Long, canGoBack: Boolean, canGoForward: Boolean) {
        if (tabId == activeTabId.value) {
            _uiState.update { it.copy(canGoBack = canGoBack, canGoForward = canGoForward) }
        }
    }

    // ── v3.2 home feed ─────────────────────────────────────
    /** Cached feed items per section (empty when no feed repo — e.g. tests). Cache-only reads. */
    val newsItems: StateFlow<List<FeedItem>> = feedItemsFlow(FeedCategory.NEWS)
    val sportsItems: StateFlow<List<FeedItem>> = feedItemsFlow(FeedCategory.SPORTS)

    private fun feedItemsFlow(category: FeedCategory): StateFlow<List<FeedItem>> =
        (feedRepository?.observe(category) ?: flowOf(emptyList()))
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _quickDials = MutableStateFlow<List<QuickDial>>(emptyList())
    val quickDials: StateFlow<List<QuickDial>> = _quickDials.asStateFlow()

    private val _weather = MutableStateFlow<Weather?>(null)
    val weather: StateFlow<Weather?> = _weather.asStateFlow()

    init {
        // Offline cache (v4.6): show the last-known weather immediately at launch, before (or
        // instead of) a successful network refresh. A live forecast overwrites it when it arrives.
        viewModelScope.launch {
            val cached = settings.weatherCache.first().takeIf { it.isNotBlank() }?.let(WeatherCodec::decode)
            if (cached != null && _weather.value == null) _weather.value = cached
        }
    }

    private var lastFeedRefreshAt = 0L

    /**
     * Home shown (non-incognito): recompute quick dials from history and kick a throttled feed
     * refresh. Privacy: a no-op in incognito — no history read, no network, nothing rendered.
     */
    fun onHomeShown(isIncognito: Boolean) {
        if (isIncognito) return
        viewModelScope.launch {
            val excludeHosts = homeShortcuts.value.mapNotNull { UrlHosts.of(it.url) }.toSet()
            val visited = historyDao.topVisited(activeOrbitId.value, 60)
                .map { VisitedUrl(it.url, it.title, it.visits) }
            _quickDials.value = QuickDialPolicy.rank(visited, excludeHosts)

            val now = System.currentTimeMillis()
            if (feedRepository != null && now - lastFeedRefreshAt > FEED_REFRESH_THROTTLE_MS) {
                lastFeedRefreshAt = now
                runCatching { feedRepository.refresh() }
            }
        }
    }

    private var lastWeatherAt = 0L
    private var lastWeatherKey: String? = null
    private var geocodeCache: Pair<String, WeatherPlace?>? = null

    /**
     * Loads weather for a resolved place. Throttled: skips a network forecast if the same place
     * was fetched within [FEED_REFRESH_THROTTLE_MS] (so bouncing in and out of home doesn't spam
     * Open-Meteo); always refetches when the place changes.
     */
    fun loadWeather(place: WeatherPlace?) {
        val repo = weatherRepository ?: return
        if (place == null) return
        val key = "${place.lat},${place.lon}"
        val now = System.currentTimeMillis()
        if (key == lastWeatherKey && _weather.value != null &&
            now - lastWeatherAt < FEED_REFRESH_THROTTLE_MS
        ) {
            return
        }
        lastWeatherAt = now
        lastWeatherKey = key
        viewModelScope.launch {
            repo.forecast(place.lat, place.lon)?.let { fresh ->
                _weather.value = fresh
                // Persist for the offline cache (v4.6): shown on next launch / when a refresh fails.
                settings.setWeatherCache(WeatherCodec.encode(fresh))
            }
        }
    }

    /** Geocode a typed city → place, cached per city string (avoids a lookup on every home entry). */
    suspend fun resolveCity(name: String): WeatherPlace? {
        geocodeCache?.let { if (it.first == name) return it.second }
        val place = weatherRepository?.geocodeCity(name)
        geocodeCache = name to place
        return place
    }

    // ── v4.0 Element Zapper ────────────────────────────────
    fun hiddenForHost(host: String): kotlinx.coroutines.flow.Flow<List<ZappedElementEntity>> =
        zapRepository?.observeForHost(host) ?: flowOf(emptyList())

    fun hiddenCountForHost(host: String): kotlinx.coroutines.flow.Flow<Int> =
        zapRepository?.countForHost(host) ?: flowOf(0)

    /** The picker chose an element. Persists it (repo refuses in incognito — derived from the tab). */
    fun onZapPicked(tabId: Long, host: String, selector: String, label: String) {
        val repo = zapRepository ?: return
        val incognito = tabs.value.find { it.id == tabId }?.isIncognito == true
        viewModelScope.launch { repo.add(host, selector, label, incognito, System.currentTimeMillis()) }
    }

    fun removeZap(id: Long) {
        viewModelScope.launch { zapRepository?.remove(id) }
    }

    fun clearZapsForHost(host: String) {
        viewModelScope.launch { zapRepository?.clearForHost(host) }
    }

    /** Saved selectors for a host, for re-applying hidden elements on page load. */
    suspend fun zapSelectorsForHost(host: String): List<String> =
        zapRepository?.selectorsForHost(host) ?: emptyList()

    // ── v4.1 site icons (captured source-direct as you browse) ──────────
    /**
     * host → Coil-loadable model for that site's cached icon: a high-res touch-icon URL (String)
     * when the site declared one, else the decoded favicon bytes wrapped in a ByteBuffer. Home
     * shortcuts / quick dials render this first, falling back to network-guessed icons then a
     * letter. Empty until you visit sites (never populated from incognito).
     */
    val favicons: StateFlow<Map<String, Any>> =
        (faviconRepository?.observeAll() ?: flowOf(emptyList()))
            .map { list ->
                list.mapNotNull { e ->
                    val model: Any? = e.iconUrl ?: e.iconBytes?.let { java.nio.ByteBuffer.wrap(it) }
                    if (model != null) e.host to model else null
                }.toMap()
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /** WebView reported a declared touch-icon URL (already incognito-filtered at the source). */
    fun onTouchIconUrl(host: String, url: String) {
        val repo = faviconRepository ?: return
        viewModelScope.launch { repo.saveTouchIcon(host, url, System.currentTimeMillis()) }
    }

    /** WebView decoded the site favicon (already incognito-filtered at the source). */
    fun onFaviconBitmap(host: String, bitmap: android.graphics.Bitmap) {
        val repo = faviconRepository ?: return
        viewModelScope.launch { repo.saveBitmap(host, bitmap, System.currentTimeMillis()) }
    }

    companion object {
        const val HOME_URL = "browse://home"

        /** The fixed WebView profile name every incognito tab shares (see WebViewHolder). */
        const val INCOGNITO_PROFILE_KEY = "incognito"

        /** Min gap between background feed refreshes triggered by opening the home page. */
        const val FEED_REFRESH_THROTTLE_MS = 30 * 60 * 1000L

        /** How many blocked requests accumulate before the lifetime counter is persisted. */
        const val LIFETIME_FLUSH_BATCH = 25

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as BrowseApplication
                BrowserViewModel(
                    app.database.historyDao(),
                    app.database.bookmarkDao(),
                    app.database.tabDao(),
                    app.settingsRepository,
                    app.database.downloadDao(),
                    app.database.closedTabDao(),
                    app.database.tabGroupDao(),
                    app.database.readingListDao(),
                    ArticleStore(java.io.File(app.filesDir, "reading_list")),
                    app.database.siteSettingsDao(),
                    app.database.homeShortcutDao(),
                    com.udaytank.browse.download.ServiceDownloadController(app),
                    downloadManagerRemover = { id ->
                        (app.getSystemService(android.content.Context.DOWNLOAD_SERVICE) as android.app.DownloadManager)
                            .remove(id)
                    },
                    reloadAdblock = { app.reloadAdblock() },
                    feedRepository = app.feedRepository,
                    weatherRepository = app.weatherRepository,
                    zapRepository = app.zapRepository,
                    faviconRepository = app.faviconRepository,
                    orbitRepository = app.orbitRepository,
                    credentialRepository = app.credentialRepository,
                )
            }
        }
    }
}
