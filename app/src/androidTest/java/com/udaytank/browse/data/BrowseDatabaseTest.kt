package com.udaytank.browse.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrowseDatabaseTest {

    private lateinit var db: BrowseDatabase

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            BrowseDatabase::class.java
        ).build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun historyInsertAndReadNewestFirst() = runBlocking {
        db.historyDao().insert(HistoryEntry(url = "https://a.com", title = "A", visitedAt = 1))
        db.historyDao().insert(HistoryEntry(url = "https://b.com", title = "B", visitedAt = 2))
        val all = db.historyDao().observeAll().first()
        assertEquals(listOf("https://b.com", "https://a.com"), all.map { it.url })
        assertEquals("https://b.com", db.historyDao().mostRecent()?.url)
    }

    @Test
    fun historyClearAll() = runBlocking {
        db.historyDao().insert(HistoryEntry(url = "https://a.com", title = "A", visitedAt = 1))
        db.historyDao().clearAll()
        assertTrue(db.historyDao().observeAll().first().isEmpty())
    }

    @Test
    fun historyUpdateVisitedAtBumpsTimestamp() = runBlocking {
        db.historyDao().insert(HistoryEntry(url = "https://a.com", title = "A", visitedAt = 1))
        val id = db.historyDao().mostRecent()!!.id
        db.historyDao().updateVisitedAt(id, 99)
        assertEquals(99L, db.historyDao().mostRecent()?.visitedAt)
    }

    @Test
    fun bookmarkDuplicateUrlIsIgnored() = runBlocking {
        db.bookmarkDao().insert(Bookmark(url = "https://a.com", title = "A", createdAt = 1))
        db.bookmarkDao().insert(Bookmark(url = "https://a.com", title = "A again", createdAt = 2))
        assertEquals(1, db.bookmarkDao().observeAll().first().size)
    }

    @Test
    fun tabInsertReturnsIdAndKeepsOrder() = runBlocking {
        val id1 = db.tabDao().insert(TabEntity(url = "https://a.com", title = "A", position = 0, isActive = true))
        val id2 = db.tabDao().insert(TabEntity(url = "https://b.com", title = "B", position = 1, isActive = false))
        val all = db.tabDao().getAll()
        assertEquals(listOf(id1, id2), all.map { it.id })
    }

    @Test
    fun tabSetActiveMarksExactlyOne() = runBlocking {
        val id1 = db.tabDao().insert(TabEntity(url = "https://a.com", title = "A", position = 0, isActive = true))
        val id2 = db.tabDao().insert(TabEntity(url = "https://b.com", title = "B", position = 1, isActive = false))
        db.tabDao().setActive(id2)
        val all = db.tabDao().getAll()
        assertEquals(listOf(false, true), all.map { it.isActive })
        assertEquals(id1, all.first().id)
    }

    @Test
    fun tabIncognitoFlagRoundTrips() = runBlocking {
        db.tabDao().insert(TabEntity(url = "https://a.com", title = "A", position = 0, isActive = true, isIncognito = true))
        assertTrue(db.tabDao().getAll().first().isIncognito)
    }

    @Test
    fun tabUpdateContent() = runBlocking {
        val id = db.tabDao().insert(TabEntity(url = "https://a.com", title = "A", position = 0, isActive = true))
        db.tabDao().updateContent(id, "https://a.com/page", "A page")
        assertEquals("A page", db.tabDao().getAll().first().title)
    }

    @Test
    fun bookmarkToggleLifecycle() = runBlocking {
        assertFalse(db.bookmarkDao().observeIsBookmarked("https://a.com").first())
        db.bookmarkDao().insert(Bookmark(url = "https://a.com", title = "A", createdAt = 1))
        assertTrue(db.bookmarkDao().observeIsBookmarked("https://a.com").first())
        db.bookmarkDao().deleteByUrl("https://a.com")
        assertFalse(db.bookmarkDao().observeIsBookmarked("https://a.com").first())
    }
}
