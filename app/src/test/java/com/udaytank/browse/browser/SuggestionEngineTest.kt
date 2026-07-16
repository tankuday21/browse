package com.udaytank.browse.browser

import com.udaytank.browse.FakeBookmarkDao
import com.udaytank.browse.FakeHistoryDao
import com.udaytank.browse.data.Bookmark
import com.udaytank.browse.data.HistoryEntry
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestionEngineTest {

    private fun engine(
        history: FakeHistoryDao = FakeHistoryDao(),
        bookmarks: FakeBookmarkDao = FakeBookmarkDao(),
        web: suspend (String) -> List<String> = { emptyList() },
    ) = SuggestionEngine(history, bookmarks, web)

    // History suggestions are Orbit-scoped; tests seed and query the same Orbit.
    private val orbit = 1L

    @Test
    fun `blank query suggests nothing`() = runTest {
        assertTrue(engine().suggest("   ", orbit).isEmpty())
    }

    @Test
    fun `locals come before web suggestions and are deduplicated`() = runTest {
        val history = FakeHistoryDao().apply {
            entries.value = listOf(HistoryEntry(1, "https://kotlinlang.org", "Kotlin", 5, orbit))
        }
        val bookmarks = FakeBookmarkDao().apply {
            bookmarks.value = listOf(Bookmark(1, "https://kotlinlang.org/docs", "Kotlin Docs", 1, orbitId = orbit))
        }
        val result = engine(history, bookmarks, web = { listOf("kotlin tutorial") }).suggest("kotlin", orbit)

        assertEquals(SuggestionKind.BOOKMARK, result[0].kind)
        assertEquals(SuggestionKind.HISTORY, result[1].kind)
        assertEquals(SuggestionKind.SEARCH, result[2].kind)
        assertEquals(3, result.size)
    }

    @Test
    fun `duplicate urls across sources appear once`() = runTest {
        val history = FakeHistoryDao().apply {
            entries.value = listOf(HistoryEntry(1, "https://a.com", "A", 5, orbit))
        }
        val bookmarks = FakeBookmarkDao().apply {
            bookmarks.value = listOf(Bookmark(1, "https://a.com", "A starred", 1, orbitId = orbit))
        }
        val result = engine(history, bookmarks).suggest("a.com", orbit)
        assertEquals(1, result.count { it.url == "https://a.com" })
    }

    @Test
    fun `network failure degrades to local results`() = runTest {
        val history = FakeHistoryDao().apply {
            entries.value = listOf(HistoryEntry(1, "https://a.com", "A", 5, orbit))
        }
        val result = engine(history, web = { error("network down") }).suggest("a", orbit)
        assertEquals(1, result.size)
        assertEquals(SuggestionKind.HISTORY, result[0].kind)
    }

    @Test
    fun `history from another Orbit never appears`() = runTest {
        val history = FakeHistoryDao().apply {
            entries.value = listOf(HistoryEntry(1, "https://work.com", "Work", 5, orbitId = 2L))
        }
        // Querying Orbit 1 must not surface Orbit 2's visit.
        val result = engine(history).suggest("work", orbit)
        assertTrue(result.none { it.kind == SuggestionKind.HISTORY })
    }
}
