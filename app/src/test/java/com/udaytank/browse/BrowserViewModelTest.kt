package com.udaytank.browse

import com.udaytank.browse.browser.BrowserCommand
import com.udaytank.browse.data.ReadingListEntry
import com.udaytank.browse.data.SearchEngine
import com.udaytank.browse.reading.ArticleStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

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
        readingListDao: FakeReadingListDao = FakeReadingListDao(),
        articleStore: ArticleStore = ArticleStore(createTempDirectory("reading").toFile()),
        downloadController: RecordingDownloadController = RecordingDownloadController(),
        downloadManagerRemover: (Long) -> Unit = {},
    ) = BrowserViewModel(
        historyDao, bookmarkDao, tabDao, settings, downloadDao, closedTabDao, tabGroupDao,
        readingListDao, articleStore, downloadController, downloadManagerRemover,
        ioDispatcher = Dispatchers.Unconfined,
    )

    @Test
    fun `started downloads are recorded`() {
        val downloads = FakeDownloadDao()
        val vm = vm(downloadDao = downloads)
        vm.onDownloadStarted(42, "file.pdf", "https://a.com/file.pdf")
        assertEquals(1, downloads.entries.value.size)
        assertEquals(42L, downloads.entries.value.first().downloadId)
        assertEquals("RUNNING", downloads.entries.value.first().state)
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

    @Test
    fun `onDeleteDownload cancels scheduled work before deleting the row`() = runTest {
        val downloadDao = FakeDownloadDao()
        val controller = RecordingDownloadController()
        val vm = vm(downloadDao = downloadDao, downloadController = controller)
        advanceUntilIdle()

        val id = downloadDao.insertReturning(
            com.udaytank.browse.data.DownloadEntry(
                fileName = "file.zip",
                url = "https://a.com/file.zip",
                createdAt = 0L,
                state = "SCHEDULED",
            )
        )

        vm.onDeleteDownload(id)
        advanceUntilIdle()

        assertEquals(listOf(id), controller.cancelled)
        assertTrue(downloadDao.entries.value.none { it.id == id })
    }

    @Test
    fun `onRetryDownload resets attempts before starting`() = runTest {
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
                attempts = 2,
            )
        )

        vm.onRetryDownload(id)
        advanceUntilIdle()

        assertEquals(0, downloadDao.entries.value.first { it.id == id }.attempts)
        assertEquals(listOf(id), controller.started)
    }

    @Test
    fun `delete of legacy system row removes via download manager`() = runTest {
        val downloadDao = FakeDownloadDao()
        val controller = RecordingDownloadController()
        val removedIds = mutableListOf<Long>()
        val vm = vm(downloadDao = downloadDao, downloadController = controller, downloadManagerRemover = { id -> removedIds += id })
        advanceUntilIdle()

        vm.onDownloadStarted(99L, "legacy.pdf", "https://a.com/legacy.pdf")
        advanceUntilIdle()
        val id = downloadDao.entries.value.single { it.downloadId == 99L }.id

        vm.onDeleteDownload(id)
        advanceUntilIdle()

        assertEquals(listOf(99L), removedIds)
        assertTrue(downloadDao.entries.value.none { it.id == id })
    }

    @Test
    fun `onToggleBackgroundMediaForCurrentSite adds then removes host`() = runTest {
        val settings = FakeSettingsRepository()
        val vm = vm(settings = settings)
        val tabId = vm.activeTabId.value!!
        vm.onPageStarted(tabId, "https://bbc.com/news")

        vm.onToggleBackgroundMediaForCurrentSite()
        advanceUntilIdle()
        assertTrue("bbc.com" in vm.backgroundMediaSites.value)
        assertTrue(vm.currentSiteBackgroundAllowed.value)

        vm.onToggleBackgroundMediaForCurrentSite()
        advanceUntilIdle()
        assertFalse("bbc.com" in vm.backgroundMediaSites.value)
        assertFalse(vm.currentSiteBackgroundAllowed.value)
    }

    // --- reading list (save for later) ---

    /** Re-encodes [inner] the way evaluateJavascript hands script results back: as a JSON string literal. */
    private fun jsPayload(inner: String): String =
        "\"" + inner.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    private val okPayload =
        jsPayload("""{"ok":true,"title":"A Story","content":"<p>Hello — “world” 🚀</p>"}""")

    @Test
    fun `save for later stores offline copy when extraction succeeds`() = runTest {
        val readingDao = FakeReadingListDao()
        val vm = vm(readingListDao = readingDao)
        advanceUntilIdle()
        val tabId = vm.activeTabId.value!!
        vm.onPageStarted(tabId, "https://example.com/story")
        vm.onTitleUpdated(tabId, "https://example.com/story", "A Story")
        advanceUntilIdle()

        val messages = mutableListOf<String>()
        vm.onSaveForLater({ _, cb -> cb(okPayload) }) { messages += it }
        advanceUntilIdle()

        val entry = readingDao.entries.value.single()
        assertEquals("https://example.com/story", entry.url)
        assertEquals("A Story", entry.title)
        assertNull(entry.readAt)
        assertNotNull(entry.filePath)
        assertEquals("<p>Hello — “world” 🚀</p>", File(entry.filePath!!).readText())
        assertEquals(listOf("Saved for offline reading"), messages)
    }

    @Test
    fun `save for later dedupes by url`() = runTest {
        val readingDao = FakeReadingListDao()
        readingDao.insert(ReadingListEntry(url = "https://example.com/story", title = "A Story", addedAt = 1L))
        val vm = vm(readingListDao = readingDao)
        advanceUntilIdle()
        val tabId = vm.activeTabId.value!!
        vm.onPageStarted(tabId, "https://example.com/story")
        advanceUntilIdle()

        var extracted = false
        val messages = mutableListOf<String>()
        vm.onSaveForLater({ _, cb -> extracted = true; cb(okPayload) }) { messages += it }
        advanceUntilIdle()

        assertEquals(1, readingDao.entries.value.size)
        assertFalse(extracted)
        assertEquals(listOf("Already in your reading list"), messages)
    }

    @Test
    fun `save for later keeps row online-only when extraction fails`() = runTest {
        val readingDao = FakeReadingListDao()
        val vm = vm(readingListDao = readingDao)
        advanceUntilIdle()
        val tabId = vm.activeTabId.value!!
        vm.onPageStarted(tabId, "https://example.com/no-article")
        advanceUntilIdle()

        val messages = mutableListOf<String>()
        vm.onSaveForLater({ _, cb -> cb(jsPayload("""{"ok":false}""")) }) { messages += it }
        advanceUntilIdle()

        val entry = readingDao.entries.value.single()
        assertEquals("https://example.com/no-article", entry.url)
        assertNull(entry.filePath)
        assertEquals(listOf("Saved (couldn't make offline copy)"), messages)
    }

    @Test
    fun `save for later survives a malformed extraction payload`() = runTest {
        val readingDao = FakeReadingListDao()
        val vm = vm(readingListDao = readingDao)
        advanceUntilIdle()
        val tabId = vm.activeTabId.value!!
        vm.onPageStarted(tabId, "https://example.com/broken")
        advanceUntilIdle()

        val messages = mutableListOf<String>()
        vm.onSaveForLater({ _, cb -> cb("null") }) { messages += it } // WebView returns "null" on script error
        advanceUntilIdle()

        val entry = readingDao.entries.value.single()
        assertNull(entry.filePath)
        assertEquals(listOf("Saved (couldn't make offline copy)"), messages)
    }

    @Test
    fun `save for later on a home tab is a no-op with feedback`() = runTest {
        val readingDao = FakeReadingListDao()
        val vm = vm(readingListDao = readingDao)
        advanceUntilIdle()

        var extracted = false
        val messages = mutableListOf<String>()
        vm.onSaveForLater({ _, cb -> extracted = true; cb(okPayload) }) { messages += it }
        advanceUntilIdle()

        assertTrue(readingDao.entries.value.isEmpty())
        assertFalse(extracted)
        assertEquals(listOf("Nothing to save on this page"), messages)
    }

    @Test
    fun `save for later works from an incognito tab`() = runTest {
        val readingDao = FakeReadingListDao()
        val vm = vm(readingListDao = readingDao)
        advanceUntilIdle()
        vm.onNewIncognitoTab()
        advanceUntilIdle()
        val tabId = vm.activeTabId.value!!
        vm.onPageStarted(tabId, "https://example.com/private-read")
        advanceUntilIdle()

        vm.onSaveForLater({ _, cb -> cb(okPayload) }) { }
        advanceUntilIdle()

        assertEquals("https://example.com/private-read", readingDao.entries.value.single().url)
    }

    @Test
    fun `delete reading item removes file and row`() = runTest {
        val readingDao = FakeReadingListDao()
        val vm = vm(readingListDao = readingDao)
        advanceUntilIdle()
        val tabId = vm.activeTabId.value!!
        vm.onPageStarted(tabId, "https://example.com/story")
        advanceUntilIdle()
        vm.onSaveForLater({ _, cb -> cb(okPayload) }) { }
        advanceUntilIdle()
        val entry = readingDao.entries.value.single()
        val path = entry.filePath!!
        assertTrue(File(path).exists())

        vm.onDeleteReadingItem(entry.id)
        advanceUntilIdle()

        assertTrue(readingDao.entries.value.isEmpty())
        assertFalse(File(path).exists())
    }

    @Test
    fun `mark read toggles readAt and drives unreadCount`() = runTest {
        val readingDao = FakeReadingListDao()
        val id1 = readingDao.insert(ReadingListEntry(url = "https://a.com/1", title = "One", addedAt = 1L))
        readingDao.insert(ReadingListEntry(url = "https://a.com/2", title = "Two", addedAt = 2L))
        val vm = vm(readingListDao = readingDao)
        advanceUntilIdle()

        assertEquals(2, vm.readingList.value.size)
        assertEquals(2, vm.unreadCount.value)

        vm.onMarkRead(id1, true)
        advanceUntilIdle()
        assertNotNull(readingDao.entries.value.first { it.id == id1 }.readAt)
        assertEquals(1, vm.unreadCount.value)

        vm.onMarkRead(id1, false)
        advanceUntilIdle()
        assertNull(readingDao.entries.value.first { it.id == id1 }.readAt)
        assertEquals(2, vm.unreadCount.value)
    }

    @Test
    fun `incognito tab cannot add background media site`() = runTest {
        val settings = FakeSettingsRepository()
        val vm = vm(settings = settings)
        vm.onNewIncognitoTab()
        advanceUntilIdle()
        val tabId = vm.activeTabId.value!!
        vm.onPageStarted(tabId, "https://secret.com/video")
        advanceUntilIdle()

        vm.onToggleBackgroundMediaForCurrentSite()
        advanceUntilIdle()

        assertTrue(vm.backgroundMediaSites.value.isEmpty())
        assertFalse("secret.com" in vm.backgroundMediaSites.value)
        assertFalse(vm.currentSiteBackgroundAllowed.value)
    }
}
