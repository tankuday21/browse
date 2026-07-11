package com.udaytank.browse.browser

import com.udaytank.browse.FakeTabDao
import com.udaytank.browse.data.TabEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class TabManagerTest {

    @Test
    fun `initialize with empty storage creates one home tab`() = runTest {
        val manager = TabManager(FakeTabDao())
        manager.initialize("https://home")
        assertEquals(1, manager.tabs.value.size)
        assertEquals("https://home", manager.tabs.value.first().url)
        assertEquals(manager.tabs.value.first().id, manager.activeTabId.value)
    }

    @Test
    fun `initialize restores stored tabs and active choice`() = runTest {
        val dao = FakeTabDao()
        dao.insert(TabEntity(url = "https://a.com", title = "A", position = 0, isActive = false))
        val activeId = dao.insert(TabEntity(url = "https://b.com", title = "B", position = 1, isActive = true))
        val manager = TabManager(dao)
        manager.initialize("https://home")
        assertEquals(2, manager.tabs.value.size)
        assertEquals(activeId, manager.activeTabId.value)
    }

    @Test
    fun `newTab appends and becomes active`() = runTest {
        val manager = TabManager(FakeTabDao())
        manager.initialize("https://home")
        val newId = manager.newTab("https://b.com")
        assertEquals(2, manager.tabs.value.size)
        assertEquals(newId, manager.activeTabId.value)
    }

    @Test
    fun `switchTo changes the active tab`() = runTest {
        val manager = TabManager(FakeTabDao())
        manager.initialize("https://home")
        val first = manager.activeTabId.value!!
        manager.newTab("https://b.com")
        manager.switchTo(first)
        assertEquals(first, manager.activeTabId.value)
    }

    @Test
    fun `closing the active tab activates per policy`() = runTest {
        val manager = TabManager(FakeTabDao())
        manager.initialize("https://home")
        val first = manager.activeTabId.value!!
        val second = manager.newTab("https://b.com")
        manager.switchTo(first)
        manager.closeTab(first, "https://home")
        assertEquals(second, manager.activeTabId.value)
        assertEquals(1, manager.tabs.value.size)
    }

    @Test
    fun `closing the last tab opens a fresh home tab`() = runTest {
        val manager = TabManager(FakeTabDao())
        manager.initialize("https://home")
        manager.closeTab(manager.activeTabId.value!!, "https://home")
        assertEquals(1, manager.tabs.value.size)
        assertEquals("https://home", manager.tabs.value.first().url)
    }

    @Test
    fun `content change updates the tab entry`() = runTest {
        val manager = TabManager(FakeTabDao())
        manager.initialize("https://home")
        val id = manager.activeTabId.value!!
        manager.onContentChanged(id, "https://a.com/x", "Page X")
        assertEquals("Page X", manager.tabs.value.first().title)
    }
}
