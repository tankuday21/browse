package com.udaytank.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.udaytank.browse.browser.BrowserCommand
import com.udaytank.browse.browser.TabManager
import com.udaytank.browse.browser.UrlInput
import com.udaytank.browse.browser.VisitDecision
import com.udaytank.browse.browser.VisitPolicy
import com.udaytank.browse.data.Bookmark
import com.udaytank.browse.data.BookmarkDao
import com.udaytank.browse.data.HistoryDao
import com.udaytank.browse.data.HistoryEntry
import com.udaytank.browse.data.SearchEngine
import com.udaytank.browse.data.SettingsRepository
import com.udaytank.browse.data.TabDao
import com.udaytank.browse.data.TabEntity
import com.udaytank.browse.data.ThemeMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LinkContextMenu(val url: String, val isImage: Boolean)

data class BrowserUiState(
    val addressBarText: String = "",
    val currentUrl: String? = null,
    val isLoading: Boolean = false,
    val progress: Int = 0,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val pendingCommand: BrowserCommand? = null,
    val sslWarningUrl: String? = null,
    val contextMenu: LinkContextMenu? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class BrowserViewModel(
    private val historyDao: HistoryDao,
    private val bookmarkDao: BookmarkDao,
    tabDao: TabDao,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val tabManager = TabManager(tabDao)
    val tabs: StateFlow<List<TabEntity>> = tabManager.tabs
    val activeTabId: StateFlow<Long?> = tabManager.activeTabId

    private val _uiState = MutableStateFlow(BrowserUiState())
    val uiState: StateFlow<BrowserUiState> = _uiState.asStateFlow()

    val historyEntries: StateFlow<List<HistoryEntry>> = historyDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val bookmarks: StateFlow<List<Bookmark>> = bookmarkDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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

    /** Per-tab count of requests blocked on the current page load. */
    private val _blockedCounts = MutableStateFlow<Map<Long, Int>>(emptyMap())
    val blockedCounts: StateFlow<Map<Long, Int>> = _blockedCounts.asStateFlow()

    val isBookmarked: StateFlow<Boolean> = uiState
        .map { it.currentUrl }
        .distinctUntilChanged()
        .flatMapLatest { url -> if (url == null) flowOf(false) else bookmarkDao.observeIsBookmarked(url) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    init {
        viewModelScope.launch {
            tabManager.initialize(HOME_URL)
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
        viewModelScope.launch { tabManager.newTab(HOME_URL) }
    }

    fun onNewIncognitoTab() {
        viewModelScope.launch { tabManager.newTab(HOME_URL, incognito = true) }
    }

    fun onCloseTab(id: Long) {
        viewModelScope.launch { tabManager.closeTab(id, HOME_URL) }
    }

    fun onSwitchTab(id: Long) {
        viewModelScope.launch { tabManager.switchTo(id) }
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
    fun currentHost(): String? =
        _uiState.value.currentUrl?.let { runCatching { java.net.URI(it).host }.getOrNull() }

    fun onToggleAllowAdsOnCurrentSite() {
        val host = currentHost() ?: return
        viewModelScope.launch { settings.toggleAdAllowedSite(host) }
    }

    /** Called from WebView background threads — StateFlow.update is atomic. */
    fun onRequestBlocked(tabId: Long) {
        _blockedCounts.update { it + (tabId to (it[tabId] ?: 0) + 1) }
    }

    // --- events from the UI ---

    fun onAddressBarTextChanged(text: String) =
        _uiState.update { it.copy(addressBarText = text) }

    /**
     * A home tab has no WebView mounted, so commands can't reach it —
     * rewriting the tab's url mounts the WebView, which then loads it.
     */
    private fun loadInActiveTab(url: String) {
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
        viewModelScope.launch {
            if (isBookmarked.value) {
                bookmarkDao.deleteByUrl(url)
            } else {
                bookmarkDao.insert(
                    Bookmark(url = url, title = state.addressBarText, createdAt = System.currentTimeMillis())
                )
            }
        }
    }

    fun onDeleteHistoryEntry(id: Long) {
        viewModelScope.launch { historyDao.deleteById(id) }
    }

    fun onClearHistory() {
        viewModelScope.launch { historyDao.clearAll() }
    }

    fun onDeleteBookmark(url: String) {
        viewModelScope.launch { bookmarkDao.deleteByUrl(url) }
    }

    // --- callbacks from the WebViews (any tab) ---

    fun onPageStarted(tabId: Long, url: String) {
        viewModelScope.launch { tabManager.onContentChanged(tabId, url, url) }
        _blockedCounts.update { it + (tabId to 0) } // fresh page, fresh counter
        if (tabId == activeTabId.value) {
            _uiState.update { it.copy(currentUrl = url, addressBarText = url, isLoading = true, progress = 0) }
        }
    }

    fun onProgressChanged(tabId: Long, percent: Int) {
        if (tabId == activeTabId.value) _uiState.update { it.copy(progress = percent) }
    }

    fun onPageFinished(tabId: Long, url: String, title: String?) {
        viewModelScope.launch { tabManager.onContentChanged(tabId, url, title ?: url) }
        if (tabId == activeTabId.value) _uiState.update { it.copy(isLoading = false) }

        // Incognito visits leave no trace in history.
        val isIncognito = tabs.value.find { it.id == tabId }?.isIncognito == true
        if (isIncognito) return

        viewModelScope.launch {
            val now = System.currentTimeMillis()
            when (val decision = VisitPolicy.decide(historyDao.mostRecent(), url)) {
                VisitDecision.Skip -> Unit
                VisitDecision.RecordNew ->
                    historyDao.insert(HistoryEntry(url = url, title = title ?: url, visitedAt = now))
                is VisitDecision.RefreshExisting ->
                    historyDao.updateVisitedAt(decision.existingId, now)
            }
        }
    }

    fun onSslError(tabId: Long, url: String) {
        if (tabId == activeTabId.value) {
            _uiState.update { it.copy(sslWarningUrl = url, isLoading = false) }
        }
    }

    fun onSslWarningDismissed() = _uiState.update { it.copy(sslWarningUrl = null) }

    fun onLongPress(tabId: Long, url: String, isImage: Boolean) {
        if (tabId == activeTabId.value) {
            _uiState.update { it.copy(contextMenu = LinkContextMenu(url, isImage)) }
        }
    }

    fun onContextMenuDismissed() = _uiState.update { it.copy(contextMenu = null) }

    /** New tabs opened from a page inherit that page's incognito mode. */
    fun onOpenInNewTab(url: String) {
        val incognito = tabs.value.find { it.id == activeTabId.value }?.isIncognito == true
        viewModelScope.launch { tabManager.newTab(url, incognito) }
        onContextMenuDismissed()
    }

    fun onHistoryChanged(tabId: Long, canGoBack: Boolean, canGoForward: Boolean) {
        if (tabId == activeTabId.value) {
            _uiState.update { it.copy(canGoBack = canGoBack, canGoForward = canGoForward) }
        }
    }

    companion object {
        const val HOME_URL = "browse://home"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as BrowseApplication
                BrowserViewModel(
                    app.database.historyDao(),
                    app.database.bookmarkDao(),
                    app.database.tabDao(),
                    app.settingsRepository,
                )
            }
        }
    }
}
