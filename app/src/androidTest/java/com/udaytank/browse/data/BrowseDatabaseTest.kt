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
        db.historyDao().insert(HistoryEntry(url = "https://a.com", title = "A", visitedAt = 1, orbitId = 1))
        db.historyDao().insert(HistoryEntry(url = "https://b.com", title = "B", visitedAt = 2, orbitId = 1))
        val all = db.historyDao().observeForOrbit(1).first()
        assertEquals(listOf("https://b.com", "https://a.com"), all.map { it.url })
        assertEquals("https://b.com", db.historyDao().mostRecent(1)?.url)
    }

    @Test
    fun historyClearForOrbit() = runBlocking {
        db.historyDao().insert(HistoryEntry(url = "https://a.com", title = "A", visitedAt = 1, orbitId = 1))
        db.historyDao().clearForOrbit(1)
        assertTrue(db.historyDao().observeForOrbit(1).first().isEmpty())
    }

    @Test
    fun historyUpdateVisitedAtBumpsTimestamp() = runBlocking {
        db.historyDao().insert(HistoryEntry(url = "https://a.com", title = "A", visitedAt = 1, orbitId = 1))
        val id = db.historyDao().mostRecent(1)!!.id
        db.historyDao().updateVisitedAt(id, 99)
        assertEquals(99L, db.historyDao().mostRecent(1)?.visitedAt)
    }

    @Test
    fun historyIsIsolatedPerOrbit() = runBlocking {
        db.historyDao().insert(HistoryEntry(url = "https://personal.com", title = "P", visitedAt = 1, orbitId = 1))
        db.historyDao().insert(HistoryEntry(url = "https://work.com", title = "W", visitedAt = 2, orbitId = 2))
        assertEquals(listOf("https://personal.com"), db.historyDao().observeForOrbit(1).first().map { it.url })
        assertEquals(listOf("https://work.com"), db.historyDao().observeForOrbit(2).first().map { it.url })
        // Deleting one Orbit's history leaves the other's intact.
        db.historyDao().deleteForOrbit(2)
        assertTrue(db.historyDao().observeForOrbit(2).first().isEmpty())
        assertEquals(1, db.historyDao().observeForOrbit(1).first().size)
        // topVisited and search are Orbit-scoped too.
        assertEquals(listOf("https://personal.com"), db.historyDao().topVisited(1, 10).map { it.url })
        assertTrue(db.historyDao().search(1, "work", 10).isEmpty())
    }

    @Test
    fun bookmarkDuplicateUrlIsIgnoredWithinAnOrbit() = runBlocking {
        db.bookmarkDao().insert(Bookmark(url = "https://a.com", title = "A", createdAt = 1, orbitId = 1))
        db.bookmarkDao().insert(Bookmark(url = "https://a.com", title = "A again", createdAt = 2, orbitId = 1))
        assertEquals(1, db.bookmarkDao().observeForOrbit(1).first().size)
    }

    @Test
    fun sameUrlBookmarkableInTwoOrbits() = runBlocking {
        // The composite (url, orbitId) unique index lets each Orbit keep its own star.
        db.bookmarkDao().insert(Bookmark(url = "https://a.com", title = "A", createdAt = 1, orbitId = 1))
        db.bookmarkDao().insert(Bookmark(url = "https://a.com", title = "A", createdAt = 2, orbitId = 2))
        assertEquals(1, db.bookmarkDao().observeForOrbit(1).first().size)
        assertEquals(1, db.bookmarkDao().observeForOrbit(2).first().size)
        // Deleting in one Orbit leaves the other's intact.
        db.bookmarkDao().deleteForOrbit(2)
        assertEquals(1, db.bookmarkDao().observeForOrbit(1).first().size)
        assertTrue(db.bookmarkDao().observeForOrbit(2).first().isEmpty())
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
        assertFalse(db.bookmarkDao().observeIsBookmarked(1, "https://a.com").first())
        db.bookmarkDao().insert(Bookmark(url = "https://a.com", title = "A", createdAt = 1, orbitId = 1))
        assertTrue(db.bookmarkDao().observeIsBookmarked(1, "https://a.com").first())
        db.bookmarkDao().deleteByUrl(1, "https://a.com")
        assertFalse(db.bookmarkDao().observeIsBookmarked(1, "https://a.com").first())
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
    fun migrate7to8_createsReadingListAndSiteSettings() {
        helper.createDatabase(DB, 7).apply {
            execSQL(
                "INSERT INTO tabs (url, title, position, isActive, isIncognito, pinned, locked) " +
                    "VALUES ('https://a.com', 'A', 0, 1, 0, 0, 0)"
            )
            execSQL(
                "INSERT INTO downloads (downloadId, fileName, url, createdAt, totalBytes, " +
                    "downloadedBytes, state, segments, attempts) " +
                    "VALUES (42, 'file.pdf', 'https://a.com/file.pdf', 1, -1, 0, 'DONE', 1, 0)"
            )
            close()
        }
        val db = helper.runMigrationsAndValidate(DB, 8, true, BrowseDatabase.MIGRATION_7_8)
        // Legacy data survives the migration.
        db.query("SELECT url FROM tabs").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("https://a.com", c.getString(0))
        }
        db.query("SELECT fileName FROM downloads").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("file.pdf", c.getString(0))
        }
        // Both new tables are usable: insert + read back.
        db.execSQL(
            "INSERT INTO reading_list (url, title, addedAt, readAt, filePath) " +
                "VALUES ('https://a.com/article', 'Article', 10, NULL, NULL)"
        )
        db.query("SELECT id, url, title, addedAt, readAt, filePath FROM reading_list").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(1L, c.getLong(0))
            assertEquals("https://a.com/article", c.getString(1))
            assertEquals("Article", c.getString(2))
            assertEquals(10L, c.getLong(3))
            assertTrue(c.isNull(4))
            assertTrue(c.isNull(5))
        }
        db.execSQL(
            "INSERT INTO site_settings (host, textZoom, forceDark, desktopMode) " +
                "VALUES ('a.com', 150, -1, 1)"
        )
        db.query("SELECT host, textZoom, forceDark, desktopMode FROM site_settings").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("a.com", c.getString(0))
            assertEquals(150, c.getInt(1))
            assertEquals(-1, c.getInt(2))
            assertEquals(1, c.getInt(3))
        }
    }

    @Test
    fun migrate8to9_createsHomeShortcutsAndSeedsFromNewestBookmarks() {
        helper.createDatabase(DB, 8).apply {
            execSQL(
                "INSERT INTO tabs (url, title, position, isActive, isIncognito, pinned, locked) " +
                    "VALUES ('https://a.com', 'A', 0, 1, 0, 0, 0)"
            )
            // 10 bookmarks, newest first by createdAt: b10 (createdAt=10) ... b1 (createdAt=1).
            for (i in 1..10) {
                execSQL(
                    "INSERT INTO bookmarks (url, title, createdAt, folder) " +
                        "VALUES ('https://b$i.com', 'B$i', $i, NULL)"
                )
            }
            close()
        }
        val db = helper.runMigrationsAndValidate(DB, 9, true, BrowseDatabase.MIGRATION_8_9)

        // Legacy data survives.
        db.query("SELECT url FROM tabs").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("https://a.com", c.getString(0))
        }
        db.query("SELECT COUNT(*) FROM bookmarks").use { c ->
            c.moveToFirst()
            assertEquals(10, c.getInt(0))
        }

        // Seeded with exactly the 8 newest bookmarks (what the old home speed-dial showed),
        // positions 0..7 in newest-first order.
        db.query("SELECT url, title, position FROM home_shortcuts ORDER BY position").use { c ->
            assertEquals(8, c.count)
            assertTrue(c.moveToFirst())
            assertEquals("https://b10.com", c.getString(0))
            assertEquals("B10", c.getString(1))
            assertEquals(0, c.getInt(2))
            assertTrue(c.moveToLast())
            assertEquals("https://b3.com", c.getString(0))
            assertEquals(7, c.getInt(2))
        }

        // Table is usable: insert + read back.
        db.execSQL(
            "INSERT INTO home_shortcuts (url, title, position) VALUES ('https://new.com', 'New', 8)"
        )
        db.query("SELECT url FROM home_shortcuts WHERE position = 8").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("https://new.com", c.getString(0))
        }
    }

    @Test
    fun migrate8to9_withNoBookmarksSeedsNothing() {
        helper.createDatabase(DB, 8).close()
        val db = helper.runMigrationsAndValidate(DB, 9, true, BrowseDatabase.MIGRATION_8_9)
        db.query("SELECT COUNT(*) FROM home_shortcuts").use { c ->
            c.moveToFirst()
            assertEquals(0, c.getInt(0))
        }
    }

    @Test
    fun homeShortcutDao_roundTripAndReplaceAllForOrbit() = runBlocking {
        val dao = database.homeShortcutDao()
        dao.insert(HomeShortcutEntity(url = "https://a.com", title = "A", position = 0, orbitId = 1))
        dao.insert(HomeShortcutEntity(url = "https://b.com", title = "B", position = 1, orbitId = 1))
        // A tile in another Orbit must be untouched by Orbit 1's reorder.
        dao.insert(HomeShortcutEntity(url = "https://other.com", title = "O", position = 0, orbitId = 2))

        val all = dao.observeForOrbit(1).first()
        assertEquals(listOf("https://a.com", "https://b.com"), all.map { it.url })

        // replaceAllForOrbit rewrites just this Orbit's list atomically (reorder path).
        dao.replaceAllForOrbit(
            1,
            listOf(
                HomeShortcutEntity(url = "https://b.com", title = "B", position = 0, orbitId = 1),
                HomeShortcutEntity(url = "https://a.com", title = "A", position = 1, orbitId = 1),
            )
        )
        assertEquals(listOf("https://b.com", "https://a.com"), dao.getAllForOrbit(1).map { it.url })
        // Orbit 2's tile survived.
        assertEquals(listOf("https://other.com"), dao.getAllForOrbit(2).map { it.url })

        // deleteForOrbit purges just that Orbit's tiles.
        dao.deleteForOrbit(1)
        assertTrue(dao.getAllForOrbit(1).isEmpty())
        assertEquals(1, dao.getAllForOrbit(2).size)
    }

    @Test
    fun readingListDao_roundTrip() = runBlocking {
        val dao = database.readingListDao()
        val id = dao.insert(ReadingListEntry(url = "https://a.com/1", title = "One", addedAt = 1))
        val id2 = dao.insert(ReadingListEntry(url = "https://a.com/2", title = "Two", addedAt = 2))

        // observeAll orders newest-first by addedAt.
        assertEquals(listOf(id2, id), dao.observeAll().first().map { it.id })

        // getById round-trips the inserted values.
        val one = dao.getById(id)
        assertEquals("https://a.com/1", one?.url)
        assertEquals("One", one?.title)
        assertEquals(null, one?.readAt)
        assertEquals(null, one?.filePath)

        // existsByUrl
        assertTrue(dao.existsByUrl("https://a.com/1"))
        assertFalse(dao.existsByUrl("https://nope.com"))

        // getUnread orders oldest-first; setReadAt removes from unread.
        assertEquals(listOf(id, id2), dao.getUnread().map { it.id })
        dao.setReadAt(id, 99L)
        assertEquals(99L, dao.getById(id)?.readAt)
        assertEquals(listOf(id2), dao.getUnread().map { it.id })
        dao.setReadAt(id, null)
        assertEquals(listOf(id, id2), dao.getUnread().map { it.id })

        // setFilePath
        dao.setFilePath(id, "/data/reading_list/1.html")
        assertEquals("/data/reading_list/1.html", dao.getById(id)?.filePath)
        dao.setFilePath(id, null)
        assertEquals(null, dao.getById(id)?.filePath)

        // deleteById
        dao.deleteById(id)
        assertEquals(null, dao.getById(id))
        assertFalse(dao.existsByUrl("https://a.com/1"))
        assertEquals(1, dao.observeAll().first().size)
    }

    @Test
    fun siteSettingsDao_roundTrip() = runBlocking {
        val dao = database.siteSettingsDao()
        assertEquals(null, dao.getByHost("a.com"))

        dao.upsert(SiteSettingsEntity(host = "a.com", textZoom = 150))
        val stored = dao.getByHost("a.com")
        assertEquals(150, stored?.textZoom)
        assertEquals(-1, stored?.forceDark)
        assertEquals(-1, stored?.desktopMode)

        // upsert replaces on conflict (same host).
        dao.upsert(SiteSettingsEntity(host = "a.com", textZoom = 80, forceDark = 1, desktopMode = 0))
        val replaced = dao.getByHost("a.com")
        assertEquals(80, replaced?.textZoom)
        assertEquals(1, replaced?.forceDark)
        assertEquals(0, replaced?.desktopMode)

        dao.upsert(SiteSettingsEntity(host = "b.com"))
        assertEquals(setOf("a.com", "b.com"), dao.observeAll().first().map { it.host }.toSet())

        dao.deleteByHost("a.com")
        assertEquals(null, dao.getByHost("a.com"))
        assertEquals(listOf("b.com"), dao.observeAll().first().map { it.host })
    }

    @Test
    fun migrate13to14_seedsPersonalOrbitAndAssignsNonIncognitoTabs() {
        helper.createDatabase(DB, 13).apply {
            execSQL(
                "INSERT INTO tabs (url, title, position, isActive, isIncognito) " +
                    "VALUES ('https://a.com', 'A', 0, 1, 0)"
            )
            execSQL(
                "INSERT INTO tabs (url, title, position, isActive, isIncognito) " +
                    "VALUES ('https://incognito.com', 'Incognito', 1, 0, 1)"
            )
            close()
        }
        val db = helper.runMigrationsAndValidate(DB, 14, true, BrowseDatabase.MIGRATION_13_14)

        var personalOrbitId = -1L
        db.query("SELECT id, name FROM orbits").use { c ->
            assertEquals(1, c.count)
            assertTrue(c.moveToFirst())
            personalOrbitId = c.getLong(0)
            assertEquals("Personal", c.getString(1))
        }

        db.query("SELECT orbitId FROM tabs WHERE url = 'https://a.com'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(personalOrbitId, c.getLong(0))
        }
        db.query("SELECT orbitId FROM tabs WHERE url = 'https://incognito.com'").use { c ->
            assertTrue(c.moveToFirst())
            assertTrue(c.isNull(0))
        }
    }

    @Test
    fun migrate15to16_addsOrbitIdAndBackfillsHistoryToFirstOrbit() {
        helper.createDatabase(DB, 15).apply {
            // Two orbits; position ASC → the first is the backfill target.
            execSQL(
                "INSERT INTO orbits (id, name, colorArgb, position, profileKey, iconKey) " +
                    "VALUES (10, 'Personal', 1, 0, 'orbit_10', 'person')"
            )
            execSQL(
                "INSERT INTO orbits (id, name, colorArgb, position, profileKey, iconKey) " +
                    "VALUES (11, 'Work', 2, 1, 'orbit_11', 'work')"
            )
            execSQL("INSERT INTO history (url, title, visitedAt) VALUES ('https://a.com', 'A', 1)")
            execSQL("INSERT INTO history (url, title, visitedAt) VALUES ('https://b.com', 'B', 2)")
            close()
        }
        val db = helper.runMigrationsAndValidate(DB, 16, true, BrowseDatabase.MIGRATION_15_16)

        db.query("SELECT orbitId FROM history ORDER BY url").use { c ->
            assertEquals(2, c.count)
            while (c.moveToNext()) {
                assertFalse(c.isNull(0))
                assertEquals(10L, c.getLong(0)) // backfilled to the first (position 0) orbit
            }
        }
    }

    @Test
    fun migrate16to17_addsOrbitIdToBookmarksAndShortcutsAndBackfills() {
        helper.createDatabase(DB, 16).apply {
            execSQL(
                "INSERT INTO orbits (id, name, colorArgb, position, profileKey, iconKey) " +
                    "VALUES (20, 'Personal', 1, 0, 'orbit_20', 'person')"
            )
            execSQL("INSERT INTO bookmarks (url, title, createdAt) VALUES ('https://a.com', 'A', 1)")
            execSQL("INSERT INTO home_shortcuts (url, title, position) VALUES ('https://b.com', 'B', 0)")
            close()
        }
        val db = helper.runMigrationsAndValidate(DB, 17, true, BrowseDatabase.MIGRATION_16_17)

        db.query("SELECT orbitId FROM bookmarks").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(20L, c.getLong(0))
        }
        db.query("SELECT orbitId FROM home_shortcuts").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(20L, c.getLong(0))
        }
        // The new composite unique index exists (and the old single-column one is gone).
        var hasComposite = false
        db.query("PRAGMA index_list(bookmarks)").use { c ->
            while (c.moveToNext()) {
                if (c.getString(1) == "index_bookmarks_url_orbitId") hasComposite = true
                assertTrue(c.getString(1) != "index_bookmarks_url")
            }
        }
        assertTrue(hasComposite)
    }

    @Test
    fun migrate18to19_addsDownloadOrbitIdAndBackfills() {
        helper.createDatabase(DB, 18).apply {
            // Two orbits: backfill must pick the FIRST by position (tie-break by id).
            execSQL(
                "INSERT INTO orbits (id, name, colorArgb, position, profileKey, iconKey) " +
                    "VALUES (30, 'Personal', 1, 0, 'orbit_30', 'person')"
            )
            execSQL(
                "INSERT INTO orbits (id, name, colorArgb, position, profileKey, iconKey) " +
                    "VALUES (31, 'Work', 2, 1, 'orbit_31', 'work')"
            )
            execSQL(
                "INSERT INTO downloads (downloadId, fileName, url, createdAt, totalBytes, " +
                    "downloadedBytes, state, segments, attempts) " +
                    "VALUES (-1, 'old.zip', 'https://x/old.zip', 1, -1, 0, 'DONE', 1, 0)"
            )
            close()
        }
        val db = helper.runMigrationsAndValidate(DB, 19, true, BrowseDatabase.MIGRATION_18_19)
        db.query("SELECT orbitId FROM downloads WHERE fileName = 'old.zip'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(30L, c.getLong(0)) // backfilled to the first orbit
        }
    }

    @Test
    fun migrate17to18_createsCredentialsTable() {
        helper.createDatabase(DB, 17).close()
        val db = helper.runMigrationsAndValidate(DB, 18, true, BrowseDatabase.MIGRATION_17_18)
        // Insert works and the unique index is present.
        db.execSQL(
            "INSERT INTO credentials (orbitId, host, username, passwordCipher, iv, updatedAt) " +
                "VALUES (1, 'a.com', 'u', X'0102', X'03', 1)"
        )
        db.query("SELECT COUNT(*) FROM credentials").use { c ->
            c.moveToFirst()
            assertEquals(1, c.getInt(0))
        }
        var hasIndex = false
        db.query("PRAGMA index_list(credentials)").use { c ->
            while (c.moveToNext()) if (c.getString(1) == "index_credentials_orbitId_host_username") hasIndex = true
        }
        assertTrue(hasIndex)
    }

    @Test
    fun credentialDao_orbitScopingAndPurge() = runBlocking {
        val dao = database.credentialDao()
        dao.upsert(CredentialEntity(orbitId = 1, host = "a.com", username = "u", passwordCipher = byteArrayOf(1), iv = byteArrayOf(2), updatedAt = 1))
        dao.upsert(CredentialEntity(orbitId = 2, host = "a.com", username = "u", passwordCipher = byteArrayOf(3), iv = byteArrayOf(4), updatedAt = 2))
        // Same (host, username) in a different Orbit coexists.
        assertEquals(1, dao.getForOrbitAndHost(1, "a.com").size)
        assertEquals(1, dao.getForOrbitAndHost(2, "a.com").size)
        // Re-saving the same (orbit, host, username) REPLACES (unique index), not duplicates.
        dao.upsert(CredentialEntity(orbitId = 1, host = "a.com", username = "u", passwordCipher = byteArrayOf(9), iv = byteArrayOf(9), updatedAt = 3))
        assertEquals(1, dao.observeForOrbit(1).first().size)
        // Purge one Orbit; the other survives.
        dao.deleteForOrbit(2)
        assertTrue(dao.observeForOrbit(2).first().isEmpty())
        assertEquals(1, dao.observeForOrbit(1).first().size)
        dao.clearAll()
        assertTrue(dao.observeForOrbit(1).first().isEmpty())
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
