package com.udaytank.browse.browser

import com.udaytank.browse.data.Bookmark
import com.udaytank.browse.data.HomeShortcutEntity
import com.udaytank.browse.data.ReadingListEntry
import com.udaytank.browse.data.TabGroupEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCodecTest {

    private fun fullBackup() = Backup(
        settings = mapOf(
            "searchEngine" to "DUCKDUCKGO",
            "themeMode" to "DARK",
            "javaScriptEnabled" to "false",
            "readerFontScale" to "120",
        ),
        bookmarks = listOf(
            Bookmark(url = "https://a.com", title = "Plain", createdAt = 1_700_000_000_000),
            Bookmark(url = "https://b.com/päge", title = "Ünïcode 星際 🚀", createdAt = 2, folder = "Trips & \"quotes\""),
        ),
        homeShortcuts = listOf(
            HomeShortcutEntity(url = "https://c.com", title = "C\nnewline\ttab", position = 0),
            HomeShortcutEntity(url = "https://d.com", title = "D", position = 1),
        ),
        readingList = listOf(
            ReadingListEntry(url = "https://e.com/1", title = "Unread", addedAt = 10),
            ReadingListEntry(url = "https://e.com/2", title = "Read 读", addedAt = 20, readAt = 30),
        ),
        tabGroups = listOf(
            TabGroupEntity(name = "Work \\ backslash", color = 3, position = 0),
        ),
    )

    @Test
    fun `round trips every section including unicode and escapes`() {
        val original = fullBackup()
        val decoded = BackupCodec.decode(BackupCodec.encode(original))
        assertNotNull(decoded)
        decoded!!

        assertEquals(original.settings, decoded.settings)

        assertEquals(original.bookmarks.size, decoded.bookmarks.size)
        assertEquals("Ünïcode 星際 🚀", decoded.bookmarks[1].title)
        assertEquals("Trips & \"quotes\"", decoded.bookmarks[1].folder)
        assertEquals(null, decoded.bookmarks[0].folder)
        assertEquals(1_700_000_000_000, decoded.bookmarks[0].createdAt)

        assertEquals("C\nnewline\ttab", decoded.homeShortcuts[0].title)
        assertEquals(listOf(0, 1), decoded.homeShortcuts.map { it.position })

        assertEquals(null, decoded.readingList[0].readAt)
        assertEquals(30L, decoded.readingList[1].readAt)
        assertEquals("Read 读", decoded.readingList[1].title)
        // Reading list is metadata only: filePath never travels.
        assertTrue(decoded.readingList.all { it.filePath == null })

        assertEquals("Work \\ backslash", decoded.tabGroups.single().name)
        assertEquals(3, decoded.tabGroups.single().color)
    }

    @Test
    fun `round trips an empty backup`() {
        val empty = Backup(emptyMap(), emptyList(), emptyList(), emptyList(), emptyList())
        val decoded = BackupCodec.decode(BackupCodec.encode(empty))
        assertNotNull(decoded)
        assertEquals(empty, decoded)
    }

    @Test
    fun `decode of garbage is null`() {
        assertNull(BackupCodec.decode("not json at all"))
        assertNull(BackupCodec.decode(""))
        assertNull(BackupCodec.decode("{\"schemaVersion\":1"))
        assertNull(BackupCodec.decode("[1,2,3]"))
        assertNull(BackupCodec.decode("<!DOCTYPE NETSCAPE-Bookmark-file-1>"))
    }

    @Test
    fun `decode with missing sections is null`() {
        assertNull(BackupCodec.decode("{\"schemaVersion\":1}"))
        assertNull(
            BackupCodec.decode("{\"schemaVersion\":1,\"settings\":{},\"bookmarks\":[]}")
        )
    }

    @Test
    fun `unknown newer schemaVersion is rejected`() {
        val v2 = BackupCodec.encode(fullBackup()).replaceFirst("\"schemaVersion\":1", "\"schemaVersion\":2")
        assertNull(BackupCodec.decode(v2))
    }

    @Test
    fun `missing schemaVersion is rejected`() {
        assertNull(
            BackupCodec.decode(
                "{\"settings\":{},\"bookmarks\":[],\"homeShortcuts\":[],\"readingList\":[],\"tabGroups\":[]}"
            )
        )
    }
}
