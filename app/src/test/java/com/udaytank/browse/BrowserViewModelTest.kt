package com.udaytank.browse

import com.udaytank.browse.browser.BrowserCommand
import com.udaytank.browse.data.SearchEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class BrowserViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun vm(
        historyDao: FakeHistoryDao = FakeHistoryDao(),
        bookmarkDao: FakeBookmarkDao = FakeBookmarkDao(),
        tabDao: FakeTabDao = FakeTabDao(),
        settings: FakeSettingsRepository = FakeSettingsRepository(),
        downloadDao: FakeDownloadDao = FakeDownloadDao(),
    ) = BrowserViewModel(historyDao, bookmarkDao, tabDao, settings, downloadDao)

    @Test
    fun `started downloads are recorded`() {
        val downloads = FakeDownloadDao()
        val vm = vm(downloadDao = downloads)
        vm.onDownloadStarted(42, "file.pdf", "https://a.com/file.pdf")
        assertEquals(1, downloads.entries.value.size)
        assertEquals(42L, downloads.entries.value.first().downloadId)
    }

    @Test
    fun `typing updates address bar text`() {
        val vm = vm()
        vm.onAddressBarTextChanged("bbc.com")
        assertEquals("bbc.com", vm.uiState.value.addressBarText)
    }

    @Test
    fun `go on a home tab loads by rewriting the tab url`() {
        val vm = vm()
        vm.onAddressBarTextChanged("bbc.com")
        vm.onGoPressed()
        assertNull(vm.uiState.value.pendingCommand)
        assertEquals("https://bbc.com", vm.tabs.value.first().url)
    }

    @Test
    fun `go on a web tab issues a load command`() {
        val vm = vm()
        val tabId = vm.activeTabId.value!!
        vm.onPageStarted(tabId, "https://bbc.com")
        vm.onAddressBarTextChanged("cnn.com")
        vm.onGoPressed()
        assertEquals(BrowserCommand.LoadUrl("https://cnn.com"), vm.uiState.value.pendingCommand)
    }

    @Test
    fun `selected search engine drives searches`() {
        val settings = FakeSettingsRepository()
        val vm = vm(settings = settings)
        vm.onSearchEngineSelected(SearchEngine.DUCKDUCKGO)
        vm.onAddressBarTextChanged("pizza")
        vm.onGoPressed()
        assertEquals("https://duckduckgo.com/?q=pizza", vm.tabs.value.first().url)
    }

    @Test
    fun `consuming a command clears it`() {
        val vm = vm()
        vm.onReloadPressed()
        vm.onCommandConsumed()
        assertNull(vm.uiState.value.pendingCommand)
    }

    @Test
    fun `startup creates an active home tab with empty address bar`() {
        val vm = vm()
        assertEquals(1, vm.tabs.value.size)
        assertEquals(vm.tabs.value.first().id, vm.activeTabId.value)
        assertEquals("", vm.uiState.value.addressBarText)
        assertNull(vm.uiState.value.currentUrl)
    }

    @Test
    fun `page start sets loading and syncs address bar`() {
        val vm = vm()
        val tabId = vm.activeTabId.value!!
        vm.onPageStarted(tabId, "https://bbc.com/news")
        assertTrue(vm.uiState.value.isLoading)
        assertEquals("https://bbc.com/news", vm.uiState.value.addressBarText)
        assertEquals("https://bbc.com/news", vm.uiState.value.currentUrl)
    }

    @Test
    fun `page finish clears loading and records history`() {
        val history = FakeHistoryDao()
        val vm = vm(historyDao = history)
        val tabId = vm.activeTabId.value!!
        vm.onPageStarted(tabId, "https://bbc.com")
        vm.onPageFinished(tabId, "https://bbc.com", "BBC")
        assertFalse(vm.uiState.value.isLoading)
        assertEquals(1, history.entries.value.size)
        assertEquals("BBC", history.entries.value.first().title)
    }

    @Test
    fun `reloading the same page never duplicates - it refreshes the entry`() {
        val history = FakeHistoryDao()
        val vm = vm(historyDao = history)
        val tabId = vm.activeTabId.value!!
        vm.onPageFinished(tabId, "https://bbc.com", "BBC")
        vm.onPageFinished(tabId, "https://bbc.com", "BBC")
        vm.onPageFinished(tabId, "https://bbc.com", "BBC")
        assertEquals(1, history.entries.value.size)
    }

    @Test
    fun `revisiting a page after going elsewhere records it again`() {
        val history = FakeHistoryDao()
        val vm = vm(historyDao = history)
        val tabId = vm.activeTabId.value!!
        vm.onPageFinished(tabId, "https://a.com", "A")
        vm.onPageFinished(tabId, "https://b.com", "B")
        vm.onPageFinished(tabId, "https://a.com", "A")
        assertEquals(3, history.entries.value.size)
    }

    @Test
    fun `toggle bookmark adds bookmark for current page`() {
        val bookmarks = FakeBookmarkDao()
        val vm = vm(bookmarkDao = bookmarks)
        val tabId = vm.activeTabId.value!!
        vm.onPageStarted(tabId, "https://bbc.com")
        vm.onToggleBookmark()
        assertEquals(1, bookmarks.bookmarks.value.size)
    }

    @Test
    fun `incognito page visits are never recorded in history`() {
        val history = FakeHistoryDao()
        val vm = vm(historyDao = history)
        vm.onNewIncognitoTab()
        val incognitoId = vm.activeTabId.value!!
        vm.onPageStarted(incognitoId, "https://secret.com")
        vm.onPageFinished(incognitoId, "https://secret.com", "Secret")
        assertTrue(incognitoId < 0)
        assertTrue(history.entries.value.isEmpty())
    }

    @Test
    fun `ssl error on active tab raises the warning and dismiss clears it`() {
        val vm = vm()
        val tabId = vm.activeTabId.value!!
        vm.onSslError(tabId, "https://expired.badssl.com/")
        assertEquals("https://expired.badssl.com/", vm.uiState.value.sslWarningUrl)
        vm.onSslWarningDismissed()
        assertNull(vm.uiState.value.sslWarningUrl)
    }

    @Test
    fun `long press on active tab opens context menu - background tab does not`() {
        val vm = vm()
        val first = vm.activeTabId.value!!
        vm.onNewTab()
        vm.onLongPress(first, "https://a.com/img.png", isImage = true)
        assertNull(vm.uiState.value.contextMenu)
        vm.onLongPress(vm.activeTabId.value!!, "https://a.com/link", isImage = false)
        assertEquals(LinkContextMenu("https://a.com/link", false), vm.uiState.value.contextMenu)
    }

    @Test
    fun `open in new tab from incognito inherits incognito`() {
        val vm = vm()
        vm.onNewIncognitoTab()
        vm.onOpenInNewTab("https://a.com")
        val newActive = vm.tabs.value.find { it.id == vm.activeTabId.value }!!
        assertTrue(newActive.isIncognito)
        assertEquals("https://a.com", newActive.url)
        assertNull(vm.uiState.value.contextMenu)
    }

    @Test
    fun `page error shows on active tab and clears on retry and next load`() {
        val vm = vm()
        val tabId = vm.activeTabId.value!!
        vm.onPageError(tabId, "net::ERR_NAME_NOT_RESOLVED")
        assertEquals("net::ERR_NAME_NOT_RESOLVED", vm.uiState.value.pageError)
        vm.onRetryPressed()
        assertNull(vm.uiState.value.pageError)
        assertEquals(BrowserCommand.Reload, vm.uiState.value.pendingCommand)
        vm.onPageError(tabId, "boom")
        vm.onPageStarted(tabId, "https://works.com")
        assertNull(vm.uiState.value.pageError)
    }

    @Test
    fun `history change updates nav button state`() {
        val vm = vm()
        val tabId = vm.activeTabId.value!!
        vm.onHistoryChanged(tabId, canGoBack = true, canGoForward = false)
        assertTrue(vm.uiState.value.canGoBack)
        assertFalse(vm.uiState.value.canGoForward)
    }

    @Test
    fun `events from a background tab do not disturb the active ui`() {
        val vm = vm()
        val first = vm.activeTabId.value!!
        vm.onNewTab()
        vm.onPageStarted(first, "https://background.com")
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test
    fun `switching tabs syncs the address bar to that tab`() {
        val vm = vm()
        val first = vm.activeTabId.value!!
        vm.onPageFinished(first, "https://a.com", "A")
        vm.onNewTab()
        vm.onSwitchTab(first)
        assertEquals("https://a.com", vm.uiState.value.addressBarText)
    }
}
