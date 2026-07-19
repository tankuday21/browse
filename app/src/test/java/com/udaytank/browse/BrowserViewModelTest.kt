package com.udaytank.browse

import com.udaytank.browse.browser.BrowserCommand
import com.udaytank.browse.data.ReaderTheme
import com.udaytank.browse.data.ReadingListEntry
import com.udaytank.browse.data.SearchEngine
import com.udaytank.browse.reading.ArticleStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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
        siteSettingsDao: FakeSiteSettingsDao = FakeSiteSettingsDao(),
        homeShortcutDao: FakeHomeShortcutDao = FakeHomeShortcutDao(),
        downloadController: RecordingDownloadController = RecordingDownloadController(),
        downloadManagerRemover: (Long) -> Unit = {},
    ) = BrowserViewModel(
        historyDao, bookmarkDao, tabDao, settings, downloadDao, closedTabDao, tabGroupDao,
        readingListDao, articleStore, siteSettingsDao, homeShortcutDao, downloadController,
        downloadManagerRemover, ioDispatcher = Dispatchers.Unconfined,
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

    // --- custom search engines (v5.8) ---

    @Test
    fun `adding and selecting a custom engine drives searches, removal falls back`() = runTest {
        val vm = vm(); advanceUntilIdle()
        vm.onAddCustomEngine("Kagi", "https://kagi.com/search?q=%s"); advanceUntilIdle()
        vm.onSelectCustomEngine("Kagi"); advanceUntilIdle()
        assertEquals("Kagi", vm.resolvedSearchEngine.value.label)

        vm.onSearchFromQr("hello"); advanceUntilIdle()
        val tab = vm.tabs.value.first { it.id == vm.activeTabId.value }
        assertTrue(tab.url.startsWith("https://kagi.com/search?q=hello"))

        // Same-name add replaces the template in place.
        vm.onAddCustomEngine("Kagi", "https://kagi.com/html?q=%s"); advanceUntilIdle()
        assertEquals("https://kagi.com/html?q=%s", vm.resolvedSearchEngine.value.queryUrl)
        assertEquals(1, vm.customSearchEngines.value.size)

        // Removing the selected custom silently falls back to the built-in.
        vm.onRemoveCustomEngine("Kagi"); advanceUntilIdle()
        assertEquals(
            com.udaytank.browse.data.SearchEngine.GOOGLE.label,
            vm.resolvedSearchEngine.value.label,
        )
    }

    @Test
    fun `invalid custom engines are refused and built-in pick clears the custom selection`() = runTest {
        val vm = vm(); advanceUntilIdle()
        vm.onAddCustomEngine("Bad", "http://plain.text/?q=%s"); advanceUntilIdle() // not https
        vm.onAddCustomEngine("AlsoBad", "https://x.com/?q="); advanceUntilIdle() // no %s
        assertTrue(vm.customSearchEngines.value.isEmpty())

        vm.onAddCustomEngine("Kagi", "https://kagi.com/search?q=%s"); advanceUntilIdle()
        vm.onSelectCustomEngine("Kagi"); advanceUntilIdle()
        vm.onSearchEngineSelected(com.udaytank.browse.data.SearchEngine.DUCKDUCKGO); advanceUntilIdle()
        assertEquals(
            com.udaytank.browse.data.SearchEngine.DUCKDUCKGO.label,
            vm.resolvedSearchEngine.value.label, // built-in pick cleared the custom selection
        )
    }

    @Test
    fun `two rapid adds both survive (atomic transform, no clobber)`() = runTest {
        val vm = vm(); advanceUntilIdle()
        // No advanceUntilIdle between them — a read-StateFlow-then-write implementation would
        // lose the first engine because the flow hasn't re-emitted when the second add reads.
        vm.onAddCustomEngine("Kagi", "https://kagi.com/search?q=%s")
        vm.onAddCustomEngine("Startpage", "https://www.startpage.com/sp/search?query=%s")
        advanceUntilIdle()
        assertEquals(
            setOf("Kagi", "Startpage"),
            vm.customSearchEngines.value.map { it.name }.toSet(),
        )
    }

    @Test
    fun `restore clears a stale local custom selection`() = runTest {
        // Backup: one custom (Startpage), built-in selected (blank selection).
        val vm = vm(); advanceUntilIdle()
        vm.onAddCustomEngine("Startpage", "https://www.startpage.com/sp/search?query=%s")
        advanceUntilIdle()
        val backup = com.udaytank.browse.browser.BackupCodec.decode(vm.buildBackupJson())!!

        // Device: has (and selected) a different custom, "Kagi".
        val vm2 = vm(); advanceUntilIdle()
        vm2.onAddCustomEngine("Kagi", "https://kagi.com/search?q=%s"); advanceUntilIdle()
        vm2.onSelectCustomEngine("Kagi"); advanceUntilIdle()

        vm2.onRestoreBackup(backup) {}
        advanceUntilIdle()
        // Ghost selection cleared: were "Kagi" kept, adding a same-named engine later would
        // silently activate it.
        assertEquals("", vm2.selectedCustomEngine.value)
        assertEquals(
            com.udaytank.browse.data.SearchEngine.GOOGLE.label,
            vm2.resolvedSearchEngine.value.label,
        )
    }

    @Test
    fun `backup round-trips custom engines and the selection`() = runTest {
        val vm = vm(); advanceUntilIdle()
        vm.onAddCustomEngine("Kagi", "https://kagi.com/search?q=%s"); advanceUntilIdle()
        vm.onSelectCustomEngine("Kagi"); advanceUntilIdle()
        val backup = com.udaytank.browse.browser.BackupCodec.decode(vm.buildBackupJson())!!

        val vm2 = vm(); advanceUntilIdle()
        vm2.onRestoreBackup(backup) {}
        advanceUntilIdle()
        assertEquals("Kagi", vm2.resolvedSearchEngine.value.label)
        assertEquals("https://kagi.com/search?q=%s", vm2.resolvedSearchEngine.value.queryUrl)
    }

    @Test
    fun `onSearchFromQr opens a search-url tab for plain text`() = runTest {
        val vm = vm(); advanceUntilIdle()
        vm.onSearchFromQr("hello there"); advanceUntilIdle()
        val tab = vm.tabs.value.first { it.id == vm.activeTabId.value }
        // Non-URL text becomes a search query on the active engine, not a navigation.
        assertTrue(tab.url.contains("hello") && tab.url.startsWith("http"))
    }

    // --- popups (v5.6: adoption — onCreatePopup allocates synchronously, onPopupReady registers) ---

    @Test
    fun `popup spec from an incognito parent is incognito with a negative id and no profile`() = runTest {
        val vm = vm(); advanceUntilIdle()
        vm.onNewIncognitoTab(); advanceUntilIdle()
        val parentId = vm.activeTabId.value!!
        val spec = vm.onCreatePopup(parentId)!!
        assertTrue(spec.incognito)
        assertTrue(spec.tabId < 0) // incognito id space: in-memory, never persisted
        assertNull(spec.profileKey) // obtain() applies the fixed incognito profile itself
        vm.onPopupReady(spec.tabId, parentId); advanceUntilIdle()
        val popup = vm.tabs.value.first { it.id == spec.tabId }
        assertTrue(popup.isIncognito)
        assertNull(popup.orbitId)
        assertEquals(popup.id, vm.activeTabId.value) // parent was active → foregrounds
    }

    @Test
    fun `popup backgrounds when the user switched away before it registered`() = runTest {
        val vm = vm(); advanceUntilIdle()
        vm.onNewIncognitoTab(); advanceUntilIdle()
        val incognitoParent = vm.activeTabId.value!!
        val spec = vm.onCreatePopup(incognitoParent)!!
        vm.onNewTab(); advanceUntilIdle() // user switched away — a NORMAL tab is now active
        val activeBefore = vm.activeTabId.value
        vm.onPopupReady(spec.tabId, incognitoParent); advanceUntilIdle()
        val popup = vm.tabs.value.first { it.id == spec.tabId }
        assertTrue(popup.isIncognito) // born with the PARENT's mode, not the active tab's
        // The parent is no longer what the user is looking at → the popup must NOT foreground
        // (activating a tab from another mode/Orbit breaks the switcher's invariant).
        assertEquals(activeBefore, vm.activeTabId.value)
        assertFalse(popup.isActive)
    }

    @Test
    fun `popup inherits its parent tab's Orbit and profile, not the now-active one`() = runTest {
        val vm = vm(); advanceUntilIdle()
        vm.onNewTab(); advanceUntilIdle() // parent carries the CURRENT active Orbit id
        val parent = vm.tabs.value.first { it.id == vm.activeTabId.value }
        vm.onSwitchOrbit(42L); advanceUntilIdle() // user moves to another Orbit
        val activeBefore = vm.activeTabId.value
        val spec = vm.onCreatePopup(parent.id)!!
        // (Profile-key inheritance is asserted in BrowserViewModelOrbitTest, where real
        // Orbit rows exist — this harness has none.)
        vm.onPopupReady(spec.tabId, parent.id); advanceUntilIdle()
        val popup = vm.tabs.value.first { it.id == spec.tabId }
        assertEquals(parent.orbitId, popup.orbitId)
        assertNotEquals(42L, popup.orbitId)
        assertEquals(activeBefore, vm.activeTabId.value) // cross-Orbit popup must NOT foreground
        assertFalse(popup.isActive)
    }

    @Test
    fun `popup foregrounds when its parent is still the active tab`() = runTest {
        val vm = vm(); advanceUntilIdle()
        vm.onNewTab(); advanceUntilIdle()
        val parentId = vm.activeTabId.value!!
        val spec = vm.onCreatePopup(parentId)!!
        vm.onPopupReady(spec.tabId, parentId); advanceUntilIdle()
        assertEquals(spec.tabId, vm.activeTabId.value)
    }

    @Test
    fun `popup joins parent island when auto-islands on`() = runTest {
        val vm = vm(); advanceUntilIdle()
        val parentId = vm.tabs.value.first().id
        vm.onCreateGroupWithTabs("Island", listOf(parentId)); advanceUntilIdle()
        val spec = vm.onCreatePopup(parentId)!!
        vm.onPopupReady(spec.tabId, parentId); advanceUntilIdle()
        val group = vm.tabGroups.value.single()
        assertEquals(group.id, vm.tabs.value.first { it.id == spec.tabId }.groupId)
    }

    @Test
    fun `popup from an unknown parent is blocked and stray onPopupReady is a no-op`() = runTest {
        val vm = vm(); advanceUntilIdle()
        assertNull(vm.onCreatePopup(9999L)) // engine returns false → popup blocked
        val tabsBefore = vm.tabs.value.size
        vm.onPopupReady(tabId = 12345L, parentTabId = 9999L); advanceUntilIdle()
        assertEquals(tabsBefore, vm.tabs.value.size) // nothing pending under that id
    }

    @Test
    fun `onCloseWindow closes exactly the popup tab`() = runTest {
        val vm = vm(); advanceUntilIdle()
        vm.onNewTab(); advanceUntilIdle()
        val parentId = vm.activeTabId.value!!
        val spec = vm.onCreatePopup(parentId)!!
        vm.onPopupReady(spec.tabId, parentId); advanceUntilIdle()
        vm.onCloseWindow(spec.tabId); advanceUntilIdle()
        assertTrue(vm.tabs.value.none { it.id == spec.tabId })
        assertTrue(vm.tabs.value.any { it.id == parentId }) // parent untouched
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
    fun `opening an online-only reading item marks it read and navigates`() = runTest {
        val readingDao = FakeReadingListDao()
        readingDao.insert(ReadingListEntry(url = "https://a.com/story", title = "Story", addedAt = 1L))
        val vm = vm(readingListDao = readingDao)
        advanceUntilIdle()

        vm.onOpenReadingItem(readingDao.entries.value.single())
        advanceUntilIdle()

        assertNotNull(readingDao.entries.value.single().readAt)
        assertEquals("https://a.com/story", vm.tabs.value.first().url)
    }

    @Test
    fun `opening an offline reading item marks it read without navigating`() = runTest {
        val readingDao = FakeReadingListDao()
        readingDao.insert(
            ReadingListEntry(url = "https://a.com/story", title = "Story", addedAt = 1L, filePath = "/x/1.html")
        )
        val vm = vm(readingListDao = readingDao)
        advanceUntilIdle()

        vm.onOpenReadingItem(readingDao.entries.value.single())
        advanceUntilIdle()

        assertNotNull(readingDao.entries.value.single().readAt)
        assertEquals(BrowserViewModel.HOME_URL, vm.tabs.value.first().url)
        assertNull(vm.uiState.value.pendingCommand)
    }

    @Test
    fun `undo after delete restores row fields and offline copy`() = runTest {
        val readingDao = FakeReadingListDao()
        val store = ArticleStore(createTempDirectory("reading").toFile())
        val vm = vm(readingListDao = readingDao, articleStore = store)
        advanceUntilIdle()

        val path = store.save(1L, "<p>offline body</p>")
        val id = readingDao.insert(
            ReadingListEntry(url = "https://a.com/x", title = "X", addedAt = 42L, readAt = 7L)
        )
        readingDao.setFilePath(id, path)

        vm.onDeleteReadingItem(id)
        advanceUntilIdle()
        assertTrue(readingDao.entries.value.isEmpty())
        assertFalse(File(path).exists())

        vm.onReopenReadingItem()
        advanceUntilIdle()
        val restored = readingDao.entries.value.single()
        assertEquals("https://a.com/x", restored.url)
        assertEquals("X", restored.title)
        assertEquals(42L, restored.addedAt)
        assertEquals(7L, restored.readAt)
        assertNotNull(restored.filePath)
        assertEquals("<p>offline body</p>", File(restored.filePath!!).readText())
    }

    @Test
    fun `reader pref setters round-trip through settings`() = runTest {
        val settings = FakeSettingsRepository()
        val vm = vm(settings = settings)
        advanceUntilIdle()

        vm.onReaderFontScaleChanged(130)
        vm.onReaderThemeSelected(ReaderTheme.SEPIA)
        vm.onReaderWideToggled(true)
        advanceUntilIdle()

        assertEquals(130, settings.readerFontScale.value)
        assertEquals(130, vm.readerFontScale.value)
        assertEquals(ReaderTheme.SEPIA, settings.readerTheme.value)
        assertEquals(ReaderTheme.SEPIA, vm.readerTheme.value)
        assertTrue(settings.readerWide.value)
        assertTrue(vm.readerWide.value)
    }

    @Test
    fun `undo with nothing deleted is a no-op`() = runTest {
        val readingDao = FakeReadingListDao()
        val vm = vm(readingListDao = readingDao)
        advanceUntilIdle()

        vm.onReopenReadingItem()
        advanceUntilIdle()

        assertTrue(readingDao.entries.value.isEmpty())
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

    // --- per-site display memory (H6) ---

    @Test
    fun `site override persists for the current host`() = runTest {
        val siteDao = FakeSiteSettingsDao()
        val vm = vm(siteSettingsDao = siteDao)
        advanceUntilIdle()
        vm.onPageStarted(vm.activeTabId.value!!, "https://en.wikipedia.org/wiki/Andromeda")

        vm.onSetSiteOverride(textZoom = 150)
        advanceUntilIdle()

        val row = siteDao.entries.value.single()
        assertEquals("en.wikipedia.org", row.host)
        assertEquals(150, row.textZoom)
        assertEquals(-1, row.forceDark)
        assertEquals(-1, row.desktopMode)
    }

    @Test
    fun `site override merges with the existing row`() = runTest {
        val siteDao = FakeSiteSettingsDao()
        val vm = vm(siteSettingsDao = siteDao)
        advanceUntilIdle()
        vm.onPageStarted(vm.activeTabId.value!!, "https://a.com/page")

        vm.onSetSiteOverride(textZoom = 120)
        advanceUntilIdle()
        vm.onSetSiteOverride(forceDark = 1)
        advanceUntilIdle()

        val row = siteDao.entries.value.single()
        assertEquals(120, row.textZoom)
        assertEquals(1, row.forceDark)
    }

    @Test
    fun `setting every field back to default deletes the row instead of storing a no-op`() = runTest {
        val siteDao = FakeSiteSettingsDao()
        val vm = vm(siteSettingsDao = siteDao)
        advanceUntilIdle()
        vm.onPageStarted(vm.activeTabId.value!!, "https://a.com/page")

        vm.onSetSiteOverride(forceDark = 1)
        advanceUntilIdle()
        vm.onSetSiteOverride(forceDark = -1)
        advanceUntilIdle()

        assertTrue(siteDao.entries.value.isEmpty())
    }

    @Test
    fun `clear site overrides removes the current host's row`() = runTest {
        val siteDao = FakeSiteSettingsDao()
        val vm = vm(siteSettingsDao = siteDao)
        advanceUntilIdle()
        vm.onPageStarted(vm.activeTabId.value!!, "https://a.com/page")
        vm.onSetSiteOverride(textZoom = 150, desktopMode = 1)
        advanceUntilIdle()

        vm.onClearSiteOverrides()
        advanceUntilIdle()

        assertTrue(siteDao.entries.value.isEmpty())
    }

    @Test
    fun `incognito tab never writes site settings to the dao`() = runTest {
        val siteDao = FakeSiteSettingsDao()
        val vm = vm(siteSettingsDao = siteDao)
        vm.onNewIncognitoTab()
        advanceUntilIdle()
        val incognitoId = vm.activeTabId.value!!
        assertTrue(incognitoId < 0)
        vm.onPageStarted(incognitoId, "https://secret.com/page")
        advanceUntilIdle()

        vm.onSetSiteOverride(textZoom = 150, forceDark = 1, desktopMode = 1)
        vm.onClearSiteOverrides()
        advanceUntilIdle()

        assertTrue(siteDao.entries.value.isEmpty())
    }

    @Test
    fun `incognito clear never deletes another tab's persisted row`() = runTest {
        val siteDao = FakeSiteSettingsDao()
        val vm = vm(siteSettingsDao = siteDao)
        advanceUntilIdle()
        // Persist a row from a normal tab first.
        vm.onPageStarted(vm.activeTabId.value!!, "https://a.com/page")
        vm.onSetSiteOverride(textZoom = 150)
        advanceUntilIdle()

        // Visit the same host in incognito and try to clear.
        vm.onNewIncognitoTab()
        advanceUntilIdle()
        vm.onPageStarted(vm.activeTabId.value!!, "https://a.com/page")
        vm.onClearSiteOverrides()
        advanceUntilIdle()

        assertEquals(1, siteDao.entries.value.size)
    }

    @Test
    fun `current site override flow tracks the active page's host`() = runTest {
        val siteDao = FakeSiteSettingsDao()
        val vm = vm(siteSettingsDao = siteDao)
        advanceUntilIdle()
        vm.onPageStarted(vm.activeTabId.value!!, "https://a.com/page")
        vm.onSetSiteOverride(textZoom = 150)
        advanceUntilIdle()

        assertEquals(150, vm.siteSettingsForCurrentSite.value?.textZoom)
        assertEquals(150, vm.siteSettingsByHost.value["a.com"]?.textZoom)

        vm.onPageStarted(vm.activeTabId.value!!, "https://other.com/page")
        advanceUntilIdle()
        assertNull(vm.siteSettingsForCurrentSite.value)
    }

    // --- home shortcut grid (C1) ---

    @Test
    fun `add shortcut appends at the next position`() = runTest {
        val dao = FakeHomeShortcutDao()
        val vm = vm(homeShortcutDao = dao)
        advanceUntilIdle()

        vm.onAddShortcut("https://a.com", "A")
        vm.onAddShortcut("https://b.com", "B")
        advanceUntilIdle()

        assertEquals(listOf("https://a.com", "https://b.com"), dao.getAll().map { it.url })
        assertEquals(listOf(0, 1), dao.getAll().map { it.position })
    }

    @Test
    fun `add shortcut dedupes by url and reports feedback`() = runTest {
        val dao = FakeHomeShortcutDao()
        val vm = vm(homeShortcutDao = dao)
        advanceUntilIdle()

        val messages = mutableListOf<String>()
        vm.onAddShortcut("https://a.com", "A") { messages += it }
        advanceUntilIdle()
        vm.onAddShortcut("https://a.com", "A again") { messages += it }
        advanceUntilIdle()

        assertEquals(1, dao.getAll().size)
        assertEquals(listOf("Added to home", "Already on your home screen"), messages)
    }

    @Test
    fun `add shortcut with blank title falls back to host`() = runTest {
        val dao = FakeHomeShortcutDao()
        val vm = vm(homeShortcutDao = dao)
        advanceUntilIdle()

        vm.onAddShortcut("https://news.bbc.co.uk/story", "  ")
        advanceUntilIdle()

        assertEquals("news.bbc.co.uk", dao.getAll().single().title)
    }

    @Test
    fun `add current page to home uses the active tab's url and title`() = runTest {
        val dao = FakeHomeShortcutDao()
        val vm = vm(homeShortcutDao = dao)
        advanceUntilIdle()
        val tabId = vm.activeTabId.value!!
        vm.onPageStarted(tabId, "https://a.com/page")
        vm.onPageFinished(tabId, "https://a.com/page", "A page")
        advanceUntilIdle()

        val messages = mutableListOf<String>()
        vm.onAddCurrentPageToHome { messages += it }
        advanceUntilIdle()

        assertEquals("https://a.com/page", dao.getAll().single().url)
        assertEquals("A page", dao.getAll().single().title)
        assertEquals(listOf("Added to home"), messages)
    }

    @Test
    fun `add current page on the home tab reports nothing to add`() = runTest {
        val dao = FakeHomeShortcutDao()
        val vm = vm(homeShortcutDao = dao)
        advanceUntilIdle()

        val messages = mutableListOf<String>()
        vm.onAddCurrentPageToHome { messages += it }
        advanceUntilIdle()

        assertTrue(dao.getAll().isEmpty())
        assertEquals(listOf("Nothing to add on this page"), messages)
    }

    @Test
    fun `remove shortcut deletes the row`() = runTest {
        val dao = FakeHomeShortcutDao()
        val vm = vm(homeShortcutDao = dao)
        advanceUntilIdle()
        vm.onAddShortcut("https://a.com", "A")
        vm.onAddShortcut("https://b.com", "B")
        advanceUntilIdle()

        vm.onRemoveShortcut(dao.getAll().first { it.url == "https://a.com" }.id)
        advanceUntilIdle()

        assertEquals(listOf("https://b.com"), dao.getAll().map { it.url })
    }

    @Test
    fun `move shortcut to front reindexes every position`() = runTest {
        val dao = FakeHomeShortcutDao()
        val vm = vm(homeShortcutDao = dao)
        advanceUntilIdle()
        vm.onAddShortcut("https://a.com", "A")
        vm.onAddShortcut("https://b.com", "B")
        vm.onAddShortcut("https://c.com", "C")
        advanceUntilIdle()

        vm.onMoveShortcutToFront(dao.getAll().first { it.url == "https://c.com" }.id)
        advanceUntilIdle()

        assertEquals(
            listOf("https://c.com", "https://a.com", "https://b.com"),
            dao.getAll().map { it.url },
        )
        assertEquals(listOf(0, 1, 2), dao.getAll().map { it.position })
    }

    // --- backup & restore (J1) ---

    @Test
    fun `backup json round-trips through the codec with all sections`() = runTest {
        val bookmarkDao = FakeBookmarkDao()
        val shortcutDao = FakeHomeShortcutDao()
        val readingDao = FakeReadingListDao()
        val groupDao = FakeTabGroupDao()
        val settings = FakeSettingsRepository()
        settings.setSearchEngine(SearchEngine.BING)
        settings.setReaderFontScale(130)
        val vm = vm(
            bookmarkDao = bookmarkDao, homeShortcutDao = shortcutDao,
            readingListDao = readingDao, tabGroupDao = groupDao, settings = settings,
        )
        advanceUntilIdle()
        val orbit = vm.activeOrbitId.value
        bookmarkDao.insert(
            com.udaytank.browse.data.Bookmark(url = "https://a.com", title = "A", createdAt = 1, folder = "Work", orbitId = orbit)
        )
        shortcutDao.insert(
            com.udaytank.browse.data.HomeShortcutEntity(url = "https://b.com", title = "B", position = 0, orbitId = orbit)
        )
        readingDao.insert(ReadingListEntry(url = "https://c.com", title = "C", addedAt = 2, filePath = "/gone.html"))
        groupDao.insert(com.udaytank.browse.data.TabGroupEntity(name = "Trip", color = 1, position = 0))

        val decoded = com.udaytank.browse.browser.BackupCodec.decode(vm.buildBackupJson())

        assertNotNull(decoded)
        decoded!!
        assertEquals("BING", decoded.settings["searchEngine"])
        assertEquals("130", decoded.settings["readerFontScale"])
        assertEquals("Work", decoded.bookmarks.single().folder)
        assertEquals("https://b.com", decoded.homeShortcuts.single().url)
        // Reading list travels as metadata only - never a device file path.
        assertNull(decoded.readingList.single().filePath)
        assertEquals("Trip", decoded.tabGroups.single().name)
    }

    @Test
    fun `restore merges with dedupe across every section and reports counts`() = runTest {
        val bookmarkDao = FakeBookmarkDao()
        val shortcutDao = FakeHomeShortcutDao()
        val readingDao = FakeReadingListDao()
        val groupDao = FakeTabGroupDao()
        val vm = vm(
            bookmarkDao = bookmarkDao, homeShortcutDao = shortcutDao,
            readingListDao = readingDao, tabGroupDao = groupDao,
        )
        advanceUntilIdle()
        // Existing data that overlaps with the backup (in the active Orbit, where restore lands).
        val orbit = vm.activeOrbitId.value
        bookmarkDao.insert(com.udaytank.browse.data.Bookmark(url = "https://dup.com", title = "Dup", createdAt = 1, orbitId = orbit))
        shortcutDao.insert(com.udaytank.browse.data.HomeShortcutEntity(url = "https://dup.com", title = "Dup", position = 0, orbitId = orbit))
        readingDao.insert(ReadingListEntry(url = "https://dup.com", title = "Dup", addedAt = 1))
        groupDao.insert(com.udaytank.browse.data.TabGroupEntity(name = "Trip", color = 0, position = 0))

        val backup = com.udaytank.browse.browser.Backup(
            settings = emptyMap(),
            bookmarks = listOf(
                com.udaytank.browse.data.Bookmark(url = "https://dup.com", title = "Dup copy", createdAt = 9),
                com.udaytank.browse.data.Bookmark(url = "https://new.com", title = "New", createdAt = 9, folder = "Work"),
            ),
            homeShortcuts = listOf(
                com.udaytank.browse.data.HomeShortcutEntity(url = "https://dup.com", title = "Dup", position = 0),
                com.udaytank.browse.data.HomeShortcutEntity(url = "https://fresh.com", title = "Fresh", position = 1),
            ),
            readingList = listOf(
                ReadingListEntry(url = "https://dup.com", title = "Dup", addedAt = 9),
                ReadingListEntry(url = "https://article.com", title = "Article", addedAt = 9, readAt = 10),
            ),
            tabGroups = listOf(
                com.udaytank.browse.data.TabGroupEntity(name = "Trip", color = 5, position = 0),
                com.udaytank.browse.data.TabGroupEntity(name = "Research", color = 2, position = 1),
            ),
        )

        val messages = mutableListOf<String>()
        vm.onRestoreBackup(backup) { messages += it }
        advanceUntilIdle()

        // Bookmarks: dup skipped (original title kept), new one added with its folder.
        assertEquals(2, bookmarkDao.bookmarks.value.size)
        assertEquals("Dup", bookmarkDao.bookmarks.value.first { it.url == "https://dup.com" }.title)
        assertEquals("Work", bookmarkDao.bookmarks.value.first { it.url == "https://new.com" }.folder)

        // Shortcuts: dup skipped, fresh one appended after the existing tiles.
        assertEquals(listOf("https://dup.com", "https://fresh.com"), shortcutDao.getAll().map { it.url })
        assertEquals(listOf(0, 1), shortcutDao.getAll().map { it.position })

        // Reading list: dup skipped; restored row keeps readAt but never a filePath.
        assertEquals(2, readingDao.entries.value.size)
        val restored = readingDao.entries.value.first { it.url == "https://article.com" }
        assertEquals(10L, restored.readAt)
        assertNull(restored.filePath)

        // Groups: "Trip" deduped by name (original color kept), "Research" appended.
        assertEquals(2, groupDao.groups.value.size)
        assertEquals(0, groupDao.groups.value.first { it.name == "Trip" }.color)
        assertEquals(1, groupDao.groups.value.first { it.name == "Research" }.position)

        assertEquals(
            listOf("Restored 1 bookmarks, 1 shortcuts, 1 reading list items, 1 tab groups"),
            messages,
        )
    }

    @Test
    fun `restoring a multi-orbit backup folds duplicate urls into one row per active orbit`() = runTest {
        // A whole-DB backup can hold the same URL from two Orbits. Restore folds everything into
        // the active Orbit; the same URL must land once (honest count, no duplicate shortcut tile).
        val bookmarkDao = FakeBookmarkDao()
        val shortcutDao = FakeHomeShortcutDao()
        val vm = vm(bookmarkDao = bookmarkDao, homeShortcutDao = shortcutDao)
        advanceUntilIdle()
        val orbit = vm.activeOrbitId.value

        val backup = com.udaytank.browse.browser.Backup(
            settings = emptyMap(),
            bookmarks = listOf(
                com.udaytank.browse.data.Bookmark(url = "https://x.com", title = "X (personal)", createdAt = 1, orbitId = 100),
                com.udaytank.browse.data.Bookmark(url = "https://x.com", title = "X (work)", createdAt = 2, orbitId = 200),
            ),
            homeShortcuts = listOf(
                com.udaytank.browse.data.HomeShortcutEntity(url = "https://x.com", title = "X", position = 0, orbitId = 100),
                com.udaytank.browse.data.HomeShortcutEntity(url = "https://x.com", title = "X", position = 0, orbitId = 200),
            ),
            readingList = emptyList(),
            tabGroups = emptyList(),
        )

        val messages = mutableListOf<String>()
        vm.onRestoreBackup(backup) { messages += it }
        advanceUntilIdle()

        assertEquals(1, bookmarkDao.bookmarks.value.count { it.url == "https://x.com" && it.orbitId == orbit })
        assertEquals(1, shortcutDao.shortcuts.value.count { it.url == "https://x.com" && it.orbitId == orbit })
        assertEquals(listOf("Restored 1 bookmarks, 1 shortcuts, 0 reading list items, 0 tab groups"), messages)
    }

    @Test
    fun `restore overwrites settings and skips unparseable values`() = runTest {
        val settings = FakeSettingsRepository()
        val vm = vm(settings = settings)
        advanceUntilIdle()

        val backup = com.udaytank.browse.browser.Backup(
            settings = mapOf(
                "searchEngine" to "DUCKDUCKGO",
                "themeMode" to "DARK",
                "javaScriptEnabled" to "false",
                "readerFontScale" to "120",
                "httpsOnly" to "true",
                "searchEngineTypo" to "ignored",
                "gpcEnabled" to "not-a-boolean",
            ),
            bookmarks = emptyList(),
            homeShortcuts = emptyList(),
            readingList = emptyList(),
            tabGroups = emptyList(),
        )
        vm.onRestoreBackup(backup) {}
        advanceUntilIdle()

        assertEquals(SearchEngine.DUCKDUCKGO, settings.searchEngine.value)
        assertEquals(com.udaytank.browse.data.ThemeMode.DARK, settings.themeMode.value)
        assertFalse(settings.javaScriptEnabled.value)
        assertEquals(120, settings.readerFontScale.value)
        assertTrue(settings.httpsOnly.value)
        assertFalse(settings.gpcEnabled.value) // unparseable -> untouched
    }
}
