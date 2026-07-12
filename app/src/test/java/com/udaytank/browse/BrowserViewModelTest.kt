package com.udaytank.browse

import com.udaytank.browse.browser.BrowserCommand
import com.udaytank.browse.data.SearchEngine
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
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
        closedTabDao: FakeClosedTabDao = FakeClosedTabDao(),
        tabGroupDao: FakeTabGroupDao = FakeTabGroupDao(),
        downloadController: RecordingDownloadController = RecordingDownloadController(),
    ) = BrowserViewModel(
        historyDao, bookmarkDao, tabDao, settings, downloadDao, closedTabDao, tabGroupDao, downloadController,
    )

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
    fun `adjacent swipe moves through the tab list and stops at the edges`() {
        val vm = vm()
        val first = vm.activeTabId.value!!
        vm.onNewTab()
        val second = vm.activeTabId.value!!
        vm.onSwitchAdjacentTab(next = false)
        assertEquals(first, vm.activeTabId.value)
        vm.onSwitchAdjacentTab(next = false) // already at the left edge
        assertEquals(first, vm.activeTabId.value)
        vm.onSwitchAdjacentTab(next = true)
        assertEquals(second, vm.activeTabId.value)
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

    @Test
    fun `closing a locked tab asks for confirmation first`() = runTest {
        val vm = vm()
        advanceUntilIdle()
        val id = vm.tabs.value.first().id
        vm.onToggleLocked(id); advanceUntilIdle()
        vm.onCloseTab(id); advanceUntilIdle()
        assertEquals(id, vm.uiState.value.confirmCloseTabId)
        assertTrue(vm.tabs.value.any { it.id == id }) // still open
        vm.onConfirmClose(); advanceUntilIdle()
        assertNull(vm.uiState.value.confirmCloseTabId)
        assertTrue(vm.tabs.value.none { it.id == id })
    }

    @Test
    fun `create group with tabs assigns and colors it`() = runTest {
        val vm = vm(); advanceUntilIdle()
        vm.onNewTab(); advanceUntilIdle()
        val ids = vm.tabs.value.map { it.id }
        vm.onCreateGroupWithTabs("Research", ids); advanceUntilIdle()
        val group = vm.tabGroups.value.single()
        assertEquals("Research", group.name)
        assertTrue(vm.tabs.value.all { it.groupId == group.id })
    }

    @Test
    fun `open-in-new-tab joins parent group when auto-islands on`() = runTest {
        val vm = vm(); advanceUntilIdle()
        val parentId = vm.tabs.value.first().id
        vm.onCreateGroupWithTabs("Island", listOf(parentId)); advanceUntilIdle()
        vm.onOpenInNewTab("https://child.com"); advanceUntilIdle()
        val group = vm.tabGroups.value.single()
        val child = vm.tabs.value.first { it.url == "https://child.com" }
        assertEquals(group.id, child.groupId)
    }

    @Test
    fun `reopen closed tab restores url and removes ring entry`() = runTest {
        val vm = vm(); advanceUntilIdle()
        vm.onOpenInNewTab("https://gone.com"); advanceUntilIdle()
        val id = vm.tabs.value.first { it.url == "https://gone.com" }.id
        vm.onCloseTab(id); advanceUntilIdle()
        val entry = vm.recentlyClosed.value.first()
        vm.onReopenClosed(entry); advanceUntilIdle()
        assertTrue(vm.tabs.value.any { it.url == "https://gone.com" })
        assertTrue(vm.recentlyClosed.value.none { it.id == entry.id })
    }

    // --- downloads ---

    @Test
    fun `onDownloadRequested sets prompt`() {
        val vm = vm()
        vm.onDownloadRequested("https://a.com/file.zip", "file.zip", "application/zip", "ua")
        val prompt = vm.uiState.value.downloadPrompt
        assertEquals("https://a.com/file.zip", prompt?.url)
        assertEquals("file.zip", prompt?.fileName)
        assertEquals("application/zip", prompt?.mimeType)
        assertEquals("ua", prompt?.userAgent)
    }

    @Test
    fun `dismiss clears prompt`() {
        val vm = vm()
        vm.onDownloadRequested("https://a.com/file.zip", "file.zip", null, null)
        vm.onDownloadPromptDismissed()
        assertNull(vm.uiState.value.downloadPrompt)
    }

    @Test
    fun `onStartDownload NOW inserts PENDING row and starts controller`() = runTest {
        val downloadDao = FakeDownloadDao()
        val controller = RecordingDownloadController()
        val vm = vm(downloadDao = downloadDao, downloadController = controller)
        advanceUntilIdle()

        vm.onStartDownload(
            url = "https://a.com/file.zip",
            suggestedName = "file.zip",
            mimeType = "application/zip",
            userAgent = "ua",
            constraint = DownloadWhen.NOW,
        )
        advanceUntilIdle()

        val row = downloadDao.entries.value.single()
        assertEquals("PENDING", row.state)
        assertEquals("file.zip", row.fileName)
        assertEquals("https://a.com/file.zip", row.url)
        assertEquals(listOf(row.id), controller.started)
        assertTrue(controller.scheduled.isEmpty())
    }

    @Test
    fun `onStartDownload WIFI inserts SCHEDULED and schedules`() = runTest {
        val downloadDao = FakeDownloadDao()
        val controller = RecordingDownloadController()
        val vm = vm(downloadDao = downloadDao, downloadController = controller)
        advanceUntilIdle()

        vm.onStartDownload(
            url = "https://a.com/file.zip",
            suggestedName = "file.zip",
            mimeType = null,
            userAgent = null,
            constraint = DownloadWhen.WIFI,
        )
        advanceUntilIdle()

        val row = downloadDao.entries.value.single()
        assertEquals("SCHEDULED", row.state)
        assertEquals(listOf(row.id to DownloadWhen.WIFI), controller.scheduled)
        assertTrue(controller.started.isEmpty())
    }

    @Test
    fun `onRetryDownload flips FAILED to PENDING and starts`() = runTest {
        val downloadDao = FakeDownloadDao()
        val controller = RecordingDownloadController()
        val vm = vm(downloadDao = downloadDao, downloadController = controller)
        advanceUntilIdle()

        val id = downloadDao.insertReturning(
            com.udaytank.browse.data.DownloadEntry(
                fileName = "file.zip",
                url = "https://a.com/file.zip",
                createdAt = 0L,
                state = "FAILED",
            )
        )

        vm.onRetryDownload(id)
        advanceUntilIdle()

        assertEquals("PENDING", downloadDao.entries.value.first { it.id == id }.state)
        assertEquals(listOf(id), controller.started)
    }

    @Test
    fun `onCancelDownload delegates to controller`() = runTest {
        val controller = RecordingDownloadController()
        val vm = vm(downloadController = controller)
        advanceUntilIdle()

        vm.onCancelDownload(7L)

        assertEquals(listOf(7L), controller.cancelled)
    }
}
