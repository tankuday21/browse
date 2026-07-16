package com.udaytank.browse.data

import com.udaytank.browse.browser.feed.FeedCategory
import com.udaytank.browse.browser.feed.FeedItem
import com.udaytank.browse.browser.feed.FeedSources
import com.udaytank.browse.browser.feed.RssParser
import com.udaytank.browse.data.feed.FeedDao
import com.udaytank.browse.data.feed.FeedItemEntity
import com.udaytank.browse.data.feed.RssSourceEntity
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * The home feed's data layer (v3.2). Reads come from the Room cache (instant, offline-first);
 * [refresh] fetches each enabled source directly over HTTPS — no third-party profiler, no keys.
 * Privacy: callers MUST NOT invoke [refresh] while incognito or while the feed is off; this class
 * only touches the cache + the chosen publishers.
 */
class FeedRepository(
    private val dao: FeedDao,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    /** Cached items for a section, newest first, mapped to the pure model for the UI. */
    fun observe(category: FeedCategory, limit: Int = 15): Flow<List<FeedItem>> =
        dao.observeByCategory(category.name, limit).map { rows -> rows.map(::toModel) }

    /** All sources (for Settings), as a Flow. */
    fun observeSources(): Flow<List<RssSourceEntity>> = dao.observeSources()

    /** First-run seed: install the preset sources if the user has none yet. */
    suspend fun ensureSeeded() = withContext(io) {
        if (dao.sourceCount() == 0) {
            dao.upsertSources(
                FeedSources.ALL.map {
                    RssSourceEntity(it.id, it.title, it.url, it.category.name, enabled = true)
                },
            )
        }
    }

    suspend fun setSourceEnabled(id: String, enabled: Boolean) = withContext(io) {
        dao.setSourceEnabled(id, enabled)
    }

    suspend fun addCustomSource(url: String, title: String, category: FeedCategory) = withContext(io) {
        // Stable, collision-free id from the URL itself (hashCode could collide / go negative).
        val id = "custom:$url"
        dao.upsertSources(listOf(RssSourceEntity(id, title.ifBlank { url }, url, category.name, enabled = true)))
    }

    suspend fun deleteSource(id: String) = withContext(io) { dao.deleteSource(id) }

    /** Drops every cached feed item (Black Hole panic-wipe); seeded sources are kept. */
    suspend fun clearItems() = withContext(io) { dao.clearItems() }

    /** Drops user-added feed subscriptions (a niche/identifying trace); seeded presets kept. */
    suspend fun clearCustomSources() = withContext(io) { dao.clearCustomSources() }

    /**
     * Fetch every enabled HTTPS source, parse, upsert, and prune to a rolling window. Failure of
     * one source is skipped, never fatal. Runs entirely off the main thread.
     */
    suspend fun refresh() = withContext(io) {
        ensureSeeded()
        val fresh = mutableListOf<FeedItemEntity>()
        for (source in dao.enabledSources()) {
            if (!source.url.startsWith("https://", ignoreCase = true)) continue // HTTPS only
            val category = runCatching { FeedCategory.valueOf(source.category) }.getOrNull() ?: continue
            val xml = fetch(source.url) ?: continue
            RssParser.parse(xml, source.id, category).forEach { fresh.add(toEntity(it)) }
        }
        if (fresh.isNotEmpty()) dao.upsertItems(fresh)
        dao.pruneToNewest(KEEP)
    }

    private fun fetch(url: String): String? = runCatching {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty(
                "Accept",
                "application/rss+xml, application/atom+xml, application/xml;q=0.9, text/xml;q=0.8",
            )
        }
        try {
            if (conn.responseCode !in 200..299) return null
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }.getOrNull()

    private fun toModel(e: FeedItemEntity) = FeedItem(
        sourceId = e.sourceId,
        title = e.title,
        link = e.link,
        publishedAt = e.publishedAt,
        thumbnailUrl = e.thumbnailUrl,
        category = FeedCategory.valueOf(e.category),
        description = e.description,
    )

    private fun toEntity(i: FeedItem) = FeedItemEntity(
        sourceId = i.sourceId,
        title = i.title,
        link = i.link,
        publishedAt = i.publishedAt,
        thumbnailUrl = i.thumbnailUrl,
        category = i.category.name,
        description = i.description,
    )

    private companion object {
        const val KEEP = 120
        const val TIMEOUT_MS = 8000
        // Neutral, non-identifying UA — no tracking handle for publishers.
        const val USER_AGENT = "Mozilla/5.0 (Android) Andromeda/3.2"
    }
}
