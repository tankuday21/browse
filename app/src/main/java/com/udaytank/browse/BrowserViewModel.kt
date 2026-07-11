package com.udaytank.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.udaytank.browse.browser.BrowserCommand
import com.udaytank.browse.browser.UrlInput
import com.udaytank.browse.browser.VisitPolicy
import com.udaytank.browse.data.Bookmark
import com.udaytank.browse.data.BookmarkDao
import com.udaytank.browse.data.HistoryDao
import com.udaytank.browse.data.HistoryEntry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BrowserUiState(
    val addressBarText: String = "",
    val currentUrl: String? = null,
    val isLoading: Boolean = false,
    val progress: Int = 0,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val pendingCommand: BrowserCommand? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class BrowserViewModel(
    private val historyDao: HistoryDao,
    private val bookmarkDao: BookmarkDao,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        BrowserUiState(pendingCommand = BrowserCommand.LoadUrl(HOME_URL))
    )
    val uiState: StateFlow<BrowserUiState> = _uiState.asStateFlow()

    val historyEntries: StateFlow<List<HistoryEntry>> = historyDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val bookmarks: StateFlow<List<Bookmark>> = bookmarkDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val isBookmarked: StateFlow<Boolean> = uiState
        .map { it.currentUrl }
        .distinctUntilChanged()
        .flatMapLatest { url -> if (url == null) flowOf(false) else bookmarkDao.observeIsBookmarked(url) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    // --- events from the UI ---

    fun onAddressBarTextChanged(text: String) =
        _uiState.update { it.copy(addressBarText = text) }

    fun onGoPressed() = _uiState.update {
        it.copy(pendingCommand = BrowserCommand.LoadUrl(UrlInput.toLoadableUrl(it.addressBarText)))
    }

    fun onOpenUrl(url: String) =
        _uiState.update { it.copy(pendingCommand = BrowserCommand.LoadUrl(url)) }

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

    // --- callbacks from the WebView ---

    fun onPageStarted(url: String) = _uiState.update {
        it.copy(currentUrl = url, addressBarText = url, isLoading = true, progress = 0)
    }

    fun onProgressChanged(percent: Int) = _uiState.update { it.copy(progress = percent) }

    fun onPageFinished(url: String, title: String?) {
        _uiState.update { it.copy(isLoading = false) }
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            if (VisitPolicy.shouldRecord(historyDao.mostRecent(), url, now)) {
                historyDao.insert(HistoryEntry(url = url, title = title ?: url, visitedAt = now))
            }
        }
    }

    fun onHistoryChanged(canGoBack: Boolean, canGoForward: Boolean) =
        _uiState.update { it.copy(canGoBack = canGoBack, canGoForward = canGoForward) }

    companion object {
        const val HOME_URL = "https://www.google.com"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as BrowseApplication
                BrowserViewModel(app.database.historyDao(), app.database.bookmarkDao())
            }
        }
    }
}
