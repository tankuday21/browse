package com.udaytank.browse.browser

import com.udaytank.browse.FakeBookmarkDao
import com.udaytank.browse.FakeHistoryDao
import com.udaytank.browse.data.Bookmark
import com.udaytank.browse.data.HistoryEntry
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestionEngineTest {

    private fun engine(
        history: FakeHistoryDao = FakeHistoryDao(),
        bookmarks: FakeBookmarkDao = FakeBookmarkDao(),
        web: suspend (String, String) -> List<String> = { _, _ -> emptyList() },
    ) = SuggestionEngine(history, bookmarks, web)

    // History suggestions are Orbit-scoped; tests seed and query the same Orbit.
    private val orbit = 1L
    private val suggestUrl = "https://example.com/suggest?q=%s"

    @Test
    fun `blank query suggests nothing`() = runTest {
        assertTrue(engine().suggest("   ", orbit, suggestUrl).isEmpty())
    }

    @Test
    fun `locals come before web suggestions and are deduplicated`() = runTest {
        val history = FakeHistoryDao().apply {
            entries.value = listOf(HistoryEntry(1, "https://kotlinlang.org", "Kotlin", 5, orbit))
        }
        val bookmarks = FakeBookmarkDao().apply {
            bookmarks.value = listOf(Bookmark(1, "https://kotlinlang.org/docs", "Kotlin Docs", 1, orbitId = orbit))
        }
        val result = engine(history, bookmarks, web = { _, _ -> listOf("kotlin tutorial") })
            .suggest("kotlin", orbit, suggestUrl)

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
        val result = engine(history, bookmarks).suggest("a.com", orbit, suggestUrl)
        assertEquals(1, result.count { it.url == "https://a.com" })
    }

    @Test
    fun `network failure degrades to local results`() = runTest {
        val history = FakeHistoryDao().apply {
            entries.value = listOf(HistoryEntry(1, "https://a.com", "A", 5, orbit))
        }
        val result = engine(history, web = { _, _ -> error("network down") }).suggest("a", orbit, suggestUrl)
        assertEquals(1, result.size)
        assertEquals(SuggestionKind.HISTORY, result[0].kind)
    }

    @Test
    fun `history from another Orbit never appears`() = runTest {
        val history = FakeHistoryDao().apply {
            entries.value = listOf(HistoryEntry(1, "https://work.com", "Work", 5, orbitId = 2L))
        }
        // Querying Orbit 1 must not surface Orbit 2's visit.
        val result = engine(history).suggest("work", orbit, suggestUrl)
        assertTrue(result.none { it.kind == SuggestionKind.HISTORY })
    }

    // --- v5.9: per-engine suggest URL + no-network gate ---

    @Test
    fun `null suggest url never touches the network, locals still appear`() = runTest {
        val history = FakeHistoryDao().apply {
            entries.value = listOf(HistoryEntry(1, "https://a.com", "A", 5, orbit))
        }
        // A recording flag, not fail(): the engine wraps the fetcher in runCatching, which
        // would swallow an AssertionError and let a vacuous test pass.
        var fetched = false
        val result = engine(history, web = { _, _ -> fetched = true; emptyList() })
            .suggest("a", orbit, suggestUrl = null)
        assertFalse(fetched)
        assertEquals(1, result.size)
        assertEquals(SuggestionKind.HISTORY, result[0].kind)
    }

    @Test
    fun `the fetcher receives the selected engine's suggest url`() = runTest {
        var received: String? = null
        engine(web = { url, _ -> received = url; emptyList() }).suggest("cats", orbit, suggestUrl)
        assertEquals(suggestUrl, received)
    }

    // --- v5.9: OpenSearch body parsing (the shape Google, DuckDuckGo, and Bing all return) ---

    @Test
    fun `parses the OpenSearch suggest body`() {
        val body = """["kot", ["kotlin", "kotlin tutorial", "kotor"]]"""
        assertEquals(listOf("kotlin", "kotlin tutorial", "kotor"), parseOpenSearchSuggestions(body))
    }

    @Test
    fun `parses the real shapes of all three built-in endpoints`() {
        // Google (client=firefox) appends extra metadata elements after the suggestions array.
        val google = """["kot",["kotlin","kotor"],[],{"google:suggestsubtypes":[[512],[512]]}]"""
        assertEquals(listOf("kotlin", "kotor"), parseOpenSearchSuggestions(google))
        // DuckDuckGo (/ac/?type=list) and Bing (osjson.aspx) return the bare two-element form.
        val ddg = """["kot",["kotlin","kotlin vs java"]]"""
        assertEquals(listOf("kotlin", "kotlin vs java"), parseOpenSearchSuggestions(ddg))
        val bing = """["kot",["kotlin download"]]"""
        assertEquals(listOf("kotlin download"), parseOpenSearchSuggestions(bing))
        // No results is a valid response, not an error.
        assertEquals(emptyList<String>(), parseOpenSearchSuggestions("""["zxqj", []]"""))
    }

    @Test
    fun `malformed or unexpected bodies parse to empty, never throw`() {
        assertEquals(emptyList<String>(), parseOpenSearchSuggestions("not json"))
        assertEquals(emptyList<String>(), parseOpenSearchSuggestions("{}"))
        assertEquals(emptyList<String>(), parseOpenSearchSuggestions("""["query only"]"""))
        assertEquals(emptyList<String>(), parseOpenSearchSuggestions("""["q", "not an array"]"""))
        // Non-string and blank entries are dropped, valid ones survive.
        assertEquals(listOf("ok"), parseOpenSearchSuggestions("""["q", ["ok", 42, ""]]"""))
    }
}
