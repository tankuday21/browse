package com.udaytank.browse.browser

import com.udaytank.browse.data.Bookmark
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BookmarkFoldersTest {

    private fun bm(url: String, folder: String? = null, createdAt: Long = 0) =
        Bookmark(url = url, title = url, createdAt = createdAt, folder = folder, orbitId = 1)

    @Test
    fun `normalize trims and maps blank to null`() {
        assertEquals("Work", BookmarkFolders.normalize("  Work  "))
        assertNull(BookmarkFolders.normalize("   "))
        assertNull(BookmarkFolders.normalize(""))
        assertNull(BookmarkFolders.normalize(null))
    }

    @Test
    fun `folders lists distinct names case-insensitively sorted, ignoring top-level`() {
        val list = listOf(
            bm("https://a", "Work"),
            bm("https://b", "art"),
            bm("https://c", null),
            bm("https://d", "Work"), // duplicate
            bm("https://e", "Books"),
        )
        assertEquals(listOf("art", "Books", "Work"), BookmarkFolders.folders(list))
    }

    @Test
    fun `sections put named folders first (alpha) then a trailing top-level section`() {
        val list = listOf(
            bm("https://top1", null, createdAt = 5),
            bm("https://work1", "Work", createdAt = 4),
            bm("https://art1", "Art", createdAt = 3),
            bm("https://work2", "Work", createdAt = 2),
            bm("https://top2", null, createdAt = 1),
        )
        val sections = BookmarkFolders.sections(list)
        assertEquals(listOf("Art", "Work", null), sections.map { it.first })
        // Art section
        assertEquals(listOf("https://art1"), sections[0].second.map { it.url })
        // Work section keeps incoming (newest-first) order
        assertEquals(listOf("https://work1", "https://work2"), sections[1].second.map { it.url })
        // Top-level section last, in incoming order
        assertEquals(listOf("https://top1", "https://top2"), sections[2].second.map { it.url })
    }

    @Test
    fun `all top-level yields a single null section`() {
        val list = listOf(bm("https://a"), bm("https://b"))
        val sections = BookmarkFolders.sections(list)
        assertEquals(listOf<String?>(null), sections.map { it.first })
        assertEquals(2, sections.single().second.size)
    }

    @Test
    fun `all foldered yields no null section`() {
        val list = listOf(bm("https://a", "X"), bm("https://b", "Y"))
        val sections = BookmarkFolders.sections(list)
        assertEquals(listOf("X", "Y"), sections.map { it.first })
    }

    @Test
    fun `empty input yields no sections`() {
        assertEquals(emptyList<Pair<String?, List<Bookmark>>>(), BookmarkFolders.sections(emptyList()))
    }
}
