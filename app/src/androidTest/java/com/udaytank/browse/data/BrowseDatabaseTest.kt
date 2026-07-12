package com.udaytank.browse.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val DB = "migration-test"

@RunWith(AndroidJUnit4::class)
class BrowseDatabaseTest {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        BrowseDatabase::class.java
    )

    private lateinit var db: BrowseDatabase
    private val database get() = db

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
    fun downloadEntryRoundTrip() = runBlocking {
        db.downloadDao().insert(
            DownloadEntry(downloadId = 42, fileName = "file.pdf", url = "https://a.com/file.pdf", createdAt = 1)
        )
        val all = db.downloadDao().observeAll().first()
        assertEquals(1, all.size)
        assertEquals(42L, all.first().downloadId)
        db.downloadDao().deleteById(all.first().id)
        assertTrue(db.downloadDao().observeAll().first().isEmpty())
    }

    @Test
    fun bookmarkToggleLifecycle() = runBlocking {
        assertFalse(db.bookmarkDao().observeIsBookmarked("https://a.com").first())
        db.bookmarkDao().insert(Bookmark(url = "https://a.com", title = "A", createdAt = 1))
        assertTrue(db.bookmarkDao().observeIsBookmarked("https://a.com").first())
        db.bookmarkDao().deleteByUrl("https://a.com")
        assertFalse(db.bookmarkDao().observeIsBookmarked("https://a.com").first())
    }

    @Test
    fun migrate5to6_preservesTabsAndAddsGroupTables() {
        helper.createDatabase(DB, 5).apply {
            execSQL(
                "INSERT INTO tabs (url, title, position, isActive, isIncognito) " +
                    "VALUES ('https://a.com', 'A', 0, 1, 0)"
            )
            close()
        }
        val db = helper.runMigrationsAndValidate(DB, 6, true, BrowseDatabase.MIGRATION_5_6)
        db.query("SELECT groupId, pinned, locked FROM tabs").use { c ->
            assertTrue(c.moveToFirst())
            assertTrue(c.isNull(0))
            assertEquals(0, c.getInt(1))
            assertEquals(0, c.getInt(2))
        }
        db.query("SELECT COUNT(*) FROM tab_groups").use { c -> c.moveToFirst() } // table exists
        db.query("SELECT COUNT(*) FROM closed_tabs").use { c -> c.moveToFirst() } // table exists
    }

    @Test
    fun closedTabDao_trimsToCap() = runBlocking {
        val dao = database.closedTabDao()
        repeat(105) { dao.insert(ClosedTabEntity(url = "https://x$it.com", title = "x$it", closedAt = it.toLong())) }
        dao.trimTo(100)
        val recent = dao.observeRecent(200).first()
        assertEquals(100, recent.size)
        assertEquals("x104", recent.first().title) // newest kept, ordered newest-first
    }

    @Test
    fun tabGroupDao_roundTrip() = runBlocking {
        val dao = database.tabGroupDao()
        val id = dao.insert(TabGroupEntity(name = "Trip", color = 2, position = 0))
        dao.rename(id, "Holiday")
        assertEquals("Holiday", dao.getAll().single().name)
        dao.deleteById(id)
        assertTrue(dao.getAll().isEmpty())
    }

    @Test
    fun migrate6to7_preservesDownloadsWithDoneState() {
        helper.createDatabase(DB, 6).apply {
            execSQL(
                "INSERT INTO downloads (downloadId, fileName, url, createdAt) " +
                    "VALUES (42, 'file.pdf', 'https://a.com/file.pdf', 1)"
            )
            close()
        }
        val db = helper.runMigrationsAndValidate(DB, 7, true, BrowseDatabase.MIGRATION_6_7)
        db.query("SELECT state, downloadedBytes, totalBytes, attempts FROM downloads").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("DONE", c.getString(0))
            assertEquals(0, c.getLong(1))
            assertEquals(-1, c.getLong(2))
            assertEquals(0, c.getInt(3))
        }
    }

    @Test
    fun downloadDao_progressAndStateRoundTrip() = runBlocking {
        val dao = database.downloadDao()
        val id = dao.insertReturning(
            DownloadEntry(fileName = "file.pdf", url = "https://a.com/file.pdf", createdAt = 1, state = "PENDING")
        )
        dao.setProgress(id, downloaded = 50, total = 100, segmentState = "{\"0\":50}")
        dao.setState(id, "RUNNING")
        val running = dao.getById(id)
        assertEquals("RUNNING", running?.state)
        assertEquals(50L, running?.downloadedBytes)
        assertEquals(100L, running?.totalBytes)
        assertEquals("{\"0\":50}", running?.segmentState)

        val otherId = dao.insertReturning(
            DownloadEntry(fileName = "other.pdf", url = "https://a.com/other.pdf", createdAt = 2, state = "DONE")
        )
        dao.setState(otherId, "DONE")

        val active = dao.getActive()
        assertEquals(listOf(id), active.map { it.id })

        dao.setState(id, "FAILED", error = "network error")
        val failed = dao.getById(id)
        assertEquals("FAILED", failed?.state)
        assertEquals("network error", failed?.error)
    }
}
