package com.udaytank.browse

import androidx.lifecycle.ViewModel
import com.udaytank.browse.browser.BrowserCommand
import com.udaytank.browse.browser.UrlInput
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class BrowserUiState(
    val addressBarText: String = "",
    val currentUrl: String? = null,
    val isLoading: Boolean = false,
    val progress: Int = 0,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val pendingCommand: BrowserCommand? = null,
)

class BrowserViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(
        BrowserUiState(pendingCommand = BrowserCommand.LoadUrl(HOME_URL))
    )
    val uiState: StateFlow<BrowserUiState> = _uiState.asStateFlow()

    // --- events from the UI ---

    fun onAddressBarTextChanged(text: String) =
        _uiState.update { it.copy(addressBarText = text) }

    fun onGoPressed() = _uiState.update {
        it.copy(pendingCommand = BrowserCommand.LoadUrl(UrlInput.toLoadableUrl(it.addressBarText)))
    }

    fun onBackPressed() = _uiState.update { it.copy(pendingCommand = BrowserCommand.GoBack) }
    fun onForwardPressed() = _uiState.update { it.copy(pendingCommand = BrowserCommand.GoForward) }
    fun onReloadPressed() = _uiState.update { it.copy(pendingCommand = BrowserCommand.Reload) }

    fun onCommandConsumed() = _uiState.update { it.copy(pendingCommand = null) }

    // --- callbacks from the WebView ---

    fun onPageStarted(url: String) = _uiState.update {
        it.copy(currentUrl = url, addressBarText = url, isLoading = true, progress = 0)
    }

    fun onProgressChanged(percent: Int) = _uiState.update { it.copy(progress = percent) }

    fun onPageFinished() = _uiState.update { it.copy(isLoading = false) }

    fun onHistoryChanged(canGoBack: Boolean, canGoForward: Boolean) =
        _uiState.update { it.copy(canGoBack = canGoBack, canGoForward = canGoForward) }

    companion object {
        const val HOME_URL = "https://www.google.com"
    }
}
