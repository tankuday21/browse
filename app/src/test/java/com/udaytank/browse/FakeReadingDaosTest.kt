package com.udaytank.browse

import com.udaytank.browse.data.ReadingListEntry
import com.udaytank.browse.data.SiteSettingsEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeReadingDaosTest {

    @Test
    fun fakeReadingListDao_behavesLikeContract() = runTest {
        val dao = FakeReadingListDao()
        val id1 = dao.insert(ReadingListEntry(url = "https://a.com/1", title = "One", addedAt = 1))
        val id2 = dao.insert(ReadingListEntry(url = "https://a.com/2", title = "Two", addedAt = 2))

        assertEquals(listOf(id2, id1), dao.observeAll().first().map { it.id })
        assertEquals(listOf(id1, id2), dao.getUnread().map { it.id })
        assertTrue(dao.existsByUrl("https://a.com/1"))
        assertFalse(dao.existsByUrl("https://nope.com"))

        dao.setReadAt(id1, 99L)
        assertEquals(99L, dao.getById(id1)?.readAt)
        assertEquals(listOf(id2), dao.getUnread().map { it.id })

        dao.setFilePath(id2, "/x/2.html")
        assertEquals("/x/2.html", dao.getById(id2)?.filePath)

        dao.deleteById(id1)
        assertNull(dao.getById(id1))
        assertEquals(1, dao.observeAll().first().size)
    }

    @Test
    fun fakeSiteSettingsDao_behavesLikeContract() = runTest {
        val dao = FakeSiteSettingsDao()
        assertNull(dao.getByHost("a.com"))

        dao.upsert(SiteSettingsEntity(host = "a.com", textZoom = 150))
        assertEquals(150, dao.getByHost("a.com")?.textZoom)

        // Replaces on same host.
        dao.upsert(SiteSettingsEntity(host = "a.com", textZoom = 80, forceDark = 1, desktopMode = 0))
        val replaced = dao.getByHost("a.com")
        assertEquals(80, replaced?.textZoom)
        assertEquals(1, replaced?.forceDark)
        assertEquals(0, replaced?.desktopMode)

        dao.upsert(SiteSettingsEntity(host = "b.com"))
        assertEquals(setOf("a.com", "b.com"), dao.observeAll().first().map { it.host }.toSet())

        dao.deleteByHost("a.com")
        assertNull(dao.getByHost("a.com"))
        assertEquals(listOf("b.com"), dao.observeAll().first().map { it.host })
    }
}
