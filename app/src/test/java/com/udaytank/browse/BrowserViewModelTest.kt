package com.udaytank.browse

import com.udaytank.browse.browser.BrowserCommand
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
    ) = BrowserViewModel(historyDao, bookmarkDao)

    @Test
    fun `typing updates address bar text`() {
        val vm = vm()
        vm.onAddressBarTextChanged("bbc.com")
        assertEquals("bbc.com", vm.uiState.value.addressBarText)
    }

    @Test
    fun `go pressed emits LoadUrl command with normalized url`() {
        val vm = vm()
        vm.onAddressBarTextChanged("bbc.com")
        vm.onGoPressed()
        assertEquals(BrowserCommand.LoadUrl("https://bbc.com"), vm.uiState.value.pendingCommand)
    }

    @Test
    fun `consuming a command clears it`() {
        val vm = vm()
        vm.onGoPressed()
        vm.onCommandConsumed()
        assertNull(vm.uiState.value.pendingCommand)
    }

    @Test
    fun `page start sets loading and syncs address bar`() {
        val vm = vm()
        vm.onPageStarted("https://bbc.com/news")
        assertTrue(vm.uiState.value.isLoading)
        assertEquals("https://bbc.com/news", vm.uiState.value.addressBarText)
        assertEquals("https://bbc.com/news", vm.uiState.value.currentUrl)
    }

    @Test
    fun `page finish clears loading and records history`() {
        val history = FakeHistoryDao()
        val vm = vm(historyDao = history)
        vm.onPageStarted("https://bbc.com")
        vm.onPageFinished("https://bbc.com", "BBC")
        assertFalse(vm.uiState.value.isLoading)
        assertEquals(1, history.entries.value.size)
        assertEquals("BBC", history.entries.value.first().title)
    }

    @Test
    fun `reload spam records history only once`() {
        val history = FakeHistoryDao()
        val vm = vm(historyDao = history)
        vm.onPageFinished("https://bbc.com", "BBC")
        vm.onPageFinished("https://bbc.com", "BBC")
        assertEquals(1, history.entries.value.size)
    }

    @Test
    fun `toggle bookmark adds bookmark for current page`() {
        val bookmarks = FakeBookmarkDao()
        val vm = vm(bookmarkDao = bookmarks)
        vm.onPageStarted("https://bbc.com")
        vm.onToggleBookmark()
        assertEquals(1, bookmarks.bookmarks.value.size)
    }

    @Test
    fun `history change updates nav button state`() {
        val vm = vm()
        vm.onHistoryChanged(canGoBack = true, canGoForward = false)
        assertTrue(vm.uiState.value.canGoBack)
        assertFalse(vm.uiState.value.canGoForward)
    }
}
