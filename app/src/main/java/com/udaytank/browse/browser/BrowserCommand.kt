package com.udaytank.browse.browser

/** One-shot instructions from the ViewModel to the WebView. */
sealed interface BrowserCommand {
    data class LoadUrl(val url: String) : BrowserCommand
    data object GoBack : BrowserCommand
    data object GoForward : BrowserCommand
    data object Reload : BrowserCommand
}
