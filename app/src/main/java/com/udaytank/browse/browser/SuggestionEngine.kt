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
    private val fetchSearchSuggestions: suspend (String, String) -> List<String> = ::openSearchSuggest,
) {
    /**
     * [suggestUrl] is the selected engine's suggest endpoint (`%s` = query) — null skips the
     * network branch entirely: custom engines have no known endpoint, and incognito typing
     * must never leave the device. Local results are unaffected either way.
     */
    suspend fun suggest(query: String, orbitId: Long, suggestUrl: String?): List<Suggestion> {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return emptyList()

        // Bookmark + history suggestions are Orbit-scoped: one Orbit's saved/visited URLs never
        // autocomplete in another.
        val bookmarks = bookmarkDao.search(orbitId, trimmed, 2)
            .map { Suggestion(it.title, it.url, SuggestionKind.BOOKMARK) }
        val history = historyDao.search(orbitId, trimmed, 3)
            .map { Suggestion(it.title, it.url, SuggestionKind.HISTORY) }
        val web = if (suggestUrl == null) emptyList() else {
            runCatching { fetchSearchSuggestions(suggestUrl, trimmed) }.getOrDefault(emptyList())
                .take(3)
                .map { Suggestion(it, it, SuggestionKind.SEARCH) }
        }

        val seen = HashSet<String>()
        return (bookmarks + history + web).filter { seen.add(it.url) }.take(6)
    }
}

/**
 * Fetches from an OpenSearch-style suggest endpoint (Google/DuckDuckGo/Bing all speak the
 * same `["query", ["s1", ...]]` JSON). [suggestUrl] carries `%s` where the encoded query goes.
 */
suspend fun openSearchSuggest(suggestUrl: String, query: String): List<String> =
    withContext(Dispatchers.IO) {
        val url = URL(suggestUrl.replace("%s", URLEncoder.encode(query, "UTF-8")))
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 1500
        connection.readTimeout = 1500
        // Neutral UA (project constraint): the platform default leaks device model + OS
        // version with every keystroke. Same convention as WeatherRepository/FeedRepository.
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) Andromeda/5.9")
        try {
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            parseOpenSearchSuggestions(body)
        } finally {
            connection.disconnect()
        }
    }

/** Lenient parse of the OpenSearch suggest body; malformed input yields an empty list. */
fun parseOpenSearchSuggestions(body: String): List<String> = runCatching {
    val array = JSONArray(body).optJSONArray(1) ?: return@runCatching emptyList()
    (0 until array.length()).mapNotNull { i ->
        (array.opt(i) as? String)?.takeIf { it.isNotBlank() }
    }
}.getOrDefault(emptyList())
