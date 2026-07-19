package com.udaytank.browse.browser

import com.udaytank.browse.FakeClosedTabDao
import com.udaytank.browse.FakeTabDao
import com.udaytank.browse.data.TabEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TabManagerTest {

    @Test
    fun `initialize with empty storage creates one home tab`() = runTest {
        val manager = TabManager(FakeTabDao(), FakeClosedTabDao())
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
        val manager = TabManager(dao, FakeClosedTabDao())
        manager.initialize("https://home")
        assertEquals(2, manager.tabs.value.size)
        assertEquals(activeId, manager.activeTabId.value)
    }

    @Test
    fun `newTab appends and becomes active`() = runTest {
        val manager = TabManager(FakeTabDao(), FakeClosedTabDao())
        manager.initialize("https://home")
        val newId = manager.newTab("https://b.com")
        assertEquals(2, manager.tabs.value.size)
        assertEquals(newId, manager.activeTabId.value)
    }

    // --- v5.6: synchronous id allocation + explicit-id registration (popup adoption) ---

    @Test
    fun `allocateTabId yields unique ids interleaved with newTab`() = runTest {
        val dao = FakeTabDao()
        val manager = TabManager(dao, FakeClosedTabDao())
        manager.initialize("https://home")
        val ids = mutableSetOf<Long>()
        ids += manager.tabs.value.first().id
        ids += manager.allocateTabId(incognito = false) // reserved, not yet registered
        ids += manager.newTab("https://a.com") // interleaved normal creation
        ids += manager.allocateTabId(incognito = false)
        ids += manager.newTab("https://b.com")
        assertEquals(5, ids.size) // no collisions anywhere in the interleaving
        assertTrue(ids.all { it > 0 })
        // Incognito allocation stays in the negative id space.
        assertTrue(manager.allocateTabId(incognito = true) < 0)
    }

    @Test
    fun `registerTab persists the pre-allocated id and can stay in the background`() = runTest {
        val dao = FakeTabDao()
        val manager = TabManager(dao, FakeClosedTabDao())
        manager.initialize("https://home")
        val activeBefore = manager.activeTabId.value
        val id = manager.allocateTabId(incognito = false)
        manager.registerTab(id, url = "", foreground = false)
        assertEquals(id, manager.tabs.value.last().id)
        assertEquals(id, dao.stored.last().id) // the DAO row carries the SAME explicit id
        assertEquals(activeBefore, manager.activeTabId.value) // background: active unchanged
        // A later normal newTab still gets a fresh id (sequence advanced past the explicit one).
        assertTrue(manager.newTab("https://c.com") != id)
    }

    @Test
    fun `registerTab incognito stays in-memory and can foreground`() = runTest {
        val dao = FakeTabDao()
        val manager = TabManager(dao, FakeClosedTabDao())
        manager.initialize("https://home")
        val storedBefore = dao.stored.size
        val id = manager.allocateTabId(incognito = true)
        manager.registerTab(id, url = "", incognito = true, foreground = true)
        assertEquals(id, manager.activeTabId.value)
        assertEquals(storedBefore, dao.stored.size) // never persisted
    }

    @Test
    fun `switchTo changes the active tab`() = runTest {
        val manager = TabManager(FakeTabDao(), FakeClosedTabDao())
        manager.initialize("https://home")
        val first = manager.activeTabId.value!!
        manager.newTab("https://b.com")
        manager.switchTo(first)
        assertEquals(first, manager.activeTabId.value)
    }

    @Test
    fun `closing the active tab activates per policy`() = runTest {
        val manager = TabManager(FakeTabDao(), FakeClosedTabDao())
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
        val manager = TabManager(FakeTabDao(), FakeClosedTabDao())
        manager.initialize("https://home")
        manager.closeTab(manager.activeTabId.value!!, "https://home")
        assertEquals(1, manager.tabs.value.size)
        assertEquals("https://home", manager.tabs.value.first().url)
    }

    @Test
    fun `incognito tab is never written to storage`() = runTest {
        val dao = FakeTabDao()
        val manager = TabManager(dao, FakeClosedTabDao())
        manager.initialize("https://home")
        val incognitoId = manager.newTab("https://secret.com", incognito = true)
        manager.onContentChanged(incognitoId, "https://secret.com/page", "Secret")
        assertTrue(incognitoId < 0)
        assertEquals(1, dao.stored.size)
        assertTrue(dao.stored.none { it.isIncognito })
    }

    @Test
    fun `incognito tabs vanish on restart but normal tabs survive`() = runTest {
        val dao = FakeTabDao()
        val manager = TabManager(dao, FakeClosedTabDao())
        manager.initialize("https://home")
        manager.newTab("https://secret.com", incognito = true)
        assertEquals(2, manager.tabs.value.size)

        val rebornManager = TabManager(dao, FakeClosedTabDao()) // simulates process death
        rebornManager.initialize("https://home")
        assertEquals(1, rebornManager.tabs.value.size)
        assertTrue(rebornManager.tabs.value.none { it.isIncognito })
    }

    @Test
    fun `closing an incognito tab works without touching storage`() = runTest {
        val dao = FakeTabDao()
        val manager = TabManager(dao, FakeClosedTabDao())
        manager.initialize("https://home")
        val incognitoId = manager.newTab("https://secret.com", incognito = true)
        manager.closeTab(incognitoId, "https://home")
        assertEquals(1, manager.tabs.value.size)
        assertTrue(manager.tabs.value.none { it.isIncognito })
    }

    @Test
    fun `content change updates the tab entry`() = runTest {
        val manager = TabManager(FakeTabDao(), FakeClosedTabDao())
        manager.initialize("https://home")
        val id = manager.activeTabId.value!!
        manager.onContentChanged(id, "https://a.com/x", "Page X")
        assertEquals("Page X", manager.tabs.value.first().title)
    }

    @Test
    fun `closing a normal tab records it in the closed ring`() = runTest {
        val closedTabDao = FakeClosedTabDao()
        val manager = TabManager(FakeTabDao(), closedTabDao)
        manager.initialize("home")
        val id = manager.newTab("https://a.com")
        manager.onContentChanged(id, "https://a.com", "Site A")
        manager.closeTab(id, "home")
        val closed = closedTabDao.entries.value.single()
        assertEquals("https://a.com", closed.url)
        assertEquals("Site A", closed.title)
    }

    @Test
    fun `closing an incognito tab records nothing`() = runTest {
        val closedTabDao = FakeClosedTabDao()
        val manager = TabManager(FakeTabDao(), closedTabDao)
        manager.initialize("home")
        val id = manager.newTab("https://secret.com", incognito = true)
        manager.closeTab(id, "home")
        assertTrue(closedTabDao.entries.value.isEmpty())
    }

    @Test
    fun `new tab can join a group and group setters write through`() = runTest {
        val manager = TabManager(FakeTabDao(), FakeClosedTabDao())
        manager.initialize("home")
        val id = manager.newTab("https://a.com", groupId = 5L)
        assertEquals(5L, manager.tabs.value.first { it.id == id }.groupId)
        manager.setPinned(id, true)
        manager.setLocked(id, true)
        val tab = manager.tabs.value.first { it.id == id }
        assertTrue(tab.pinned)
        assertTrue(tab.locked)
        manager.setGroup(id, null)
        assertNull(manager.tabs.value.first { it.id == id }.groupId)
    }
}
