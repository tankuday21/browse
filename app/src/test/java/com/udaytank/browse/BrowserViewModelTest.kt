package com.udaytank.browse

import com.udaytank.browse.browser.BrowserCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserViewModelTest {

    @Test
    fun `typing updates address bar text`() {
        val vm = BrowserViewModel()
        vm.onAddressBarTextChanged("bbc.com")
        assertEquals("bbc.com", vm.uiState.value.addressBarText)
    }

    @Test
    fun `go pressed emits LoadUrl command with normalized url`() {
        val vm = BrowserViewModel()
        vm.onAddressBarTextChanged("bbc.com")
        vm.onGoPressed()
        assertEquals(
            BrowserCommand.LoadUrl("https://bbc.com"),
            vm.uiState.value.pendingCommand
        )
    }

    @Test
    fun `consuming a command clears it`() {
        val vm = BrowserViewModel()
        vm.onGoPressed()
        vm.onCommandConsumed()
        assertNull(vm.uiState.value.pendingCommand)
    }

    @Test
    fun `page start sets loading and syncs address bar`() {
        val vm = BrowserViewModel()
        vm.onPageStarted("https://bbc.com/news")
        assertTrue(vm.uiState.value.isLoading)
        assertEquals("https://bbc.com/news", vm.uiState.value.addressBarText)
        assertEquals("https://bbc.com/news", vm.uiState.value.currentUrl)
    }

    @Test
    fun `page finish clears loading`() {
        val vm = BrowserViewModel()
        vm.onPageStarted("https://bbc.com")
        vm.onPageFinished()
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test
    fun `history change updates nav button state`() {
        val vm = BrowserViewModel()
        vm.onHistoryChanged(canGoBack = true, canGoForward = false)
        assertTrue(vm.uiState.value.canGoBack)
        assertFalse(vm.uiState.value.canGoForward)
    }
}
