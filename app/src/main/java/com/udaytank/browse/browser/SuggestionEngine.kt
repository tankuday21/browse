package com.udaytank.browse.browser

import com.udaytank.browse.data.BookmarkDao
import com.udaytank.browse.data.HistoryDao
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

enum class SuggestionKind { BOOKMARK, HISTORY, SEARCH }

data class Suggestion(val title: String, val url: String, val kind: SuggestionKind)

/**
 * Address-bar suggestions: instant local matches (bookmarks, history)
 * plus network search suggestions, deduplicated, locals first.
 */
class SuggestionEngine(
    private val historyDao: HistoryDao,
    private val bookmarkDao: BookmarkDao,
    private val fetchSearchSuggestions: suspend (String) -> List<String> = ::googleSuggest,
) {
    suspend fun suggest(query: String, orbitId: Long): List<Suggestion> {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return emptyList()

        val bookmarks = bookmarkDao.search(trimmed, 2)
            .map { Suggestion(it.title, it.url, SuggestionKind.BOOKMARK) }
        // History suggestions are Orbit-scoped: one Orbit's URLs never autocomplete in another.
        val history = historyDao.search(orbitId, trimmed, 3)
            .map { Suggestion(it.title, it.url, SuggestionKind.HISTORY) }
        val web = runCatching { fetchSearchSuggestions(trimmed) }.getOrDefault(emptyList())
            .take(3)
            .map { Suggestion(it, it, SuggestionKind.SEARCH) }

        val seen = HashSet<String>()
        return (bookmarks + history + web).filter { seen.add(it.url) }.take(6)
    }
}

/** Public suggestion endpoint (firefox client returns plain JSON). */
suspend fun googleSuggest(query: String): List<String> = withContext(Dispatchers.IO) {
    val url = URL(
        "https://suggestqueries.google.com/complete/search?client=firefox&q=" +
            URLEncoder.encode(query, "UTF-8")
    )
    val connection = url.openConnection() as HttpURLConnection
    connection.connectTimeout = 1500
    connection.readTimeout = 1500
    try {
        val body = connection.inputStream.bufferedReader().use { it.readText() }
        val array = JSONArray(body).optJSONArray(1) ?: return@withContext emptyList()
        List(array.length()) { array.getString(it) }
    } finally {
        connection.disconnect()
    }
}
