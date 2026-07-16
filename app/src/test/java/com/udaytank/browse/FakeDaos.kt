package com.udaytank.browse

import com.udaytank.browse.data.Bookmark
import com.udaytank.browse.data.BookmarkDao
import com.udaytank.browse.data.HistoryDao
import com.udaytank.browse.data.HistoryEntry
import com.udaytank.browse.data.TopVisitedRow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeHistoryDao : HistoryDao {
    val entries = MutableStateFlow<List<HistoryEntry>>(emptyList())

    override suspend fun insert(entry: HistoryEntry) {
        entries.value = entries.value + entry.copy(id = (entries.value.size + 1).toLong())
    }

    override fun observeForOrbit(orbitId: Long): Flow<List<HistoryEntry>> =
        entries.map { list ->
            list.filter { it.orbitId == orbitId }.sortedByDescending { e -> e.visitedAt }
        }

    override suspend fun mostRecent(orbitId: Long): HistoryEntry? =
        entries.value
            .filter { it.orbitId == orbitId }
            .maxWithOrNull(compareBy({ it.visitedAt }, { it.id }))

    override suspend fun updateTitleForUrl(url: String, title: String) {
        entries.value = entries.value.map { if (it.url == url) it.copy(title = title) else it }
    }

    override suspend fun search(orbitId: Long, query: String, limit: Int): List<HistoryEntry> =
        entries.value
            .filter { it.orbitId == orbitId }
            .filter { it.url.contains(query, true) || it.title.contains(query, true) }
            .sortedByDescending { it.visitedAt }
            .take(limit)

    override suspend fun updateVisitedAt(id: Long, visitedAt: Long) {
        entries.value = entries.value.map {
            if (it.id == id) it.copy(visitedAt = visitedAt) else it
        }
    }

    override suspend fun deleteById(id: Long) {
        entries.value = entries.value.filterNot { it.id == id }
    }

    override suspend fun clearForOrbit(orbitId: Long) {
        entries.value = entries.value.filterNot { it.orbitId == orbitId }
    }

    override suspend fun deleteForOrbit(orbitId: Long) {
        entries.value = entries.value.filterNot { it.orbitId == orbitId }
    }

    override suspend fun clearAll() {
        entries.value = emptyList()
    }

    override suspend fun topVisited(orbitId: Long, limit: Int): List<TopVisitedRow> =
        entries.value
            .filter { it.orbitId == orbitId }
            .groupBy { it.url }
            .map { (url, rows) ->
                val newest = rows.maxByOrNull { it.visitedAt }!!
                TopVisitedRow(url = url, title = newest.title, visits = rows.size)
            }
            .sortedWith(compareByDescending<TopVisitedRow> { it.visits })
            .take(limit)
}

class FakeBookmarkDao : BookmarkDao {
    val bookmarks = MutableStateFlow<List<Bookmark>>(emptyList())

    override suspend fun insert(bookmark: Bookmark) {
        // Mirrors the composite (url, orbitId) unique index: same url may exist in two Orbits.
        if (bookmarks.value.none { it.url == bookmark.url && it.orbitId == bookmark.orbitId }) {
            bookmarks.value = bookmarks.value + bookmark
        }
    }

    override fun observeForOrbit(orbitId: Long): Flow<List<Bookmark>> =
        bookmarks.map { list -> list.filter { it.orbitId == orbitId }.sortedByDescending { it.createdAt } }

    override suspend fun getAllForOrbit(orbitId: Long): List<Bookmark> =
        bookmarks.value.filter { it.orbitId == orbitId }.sortedByDescending { it.createdAt }

    override suspend fun getAll(): List<Bookmark> = bookmarks.value

    override suspend fun setFolder(orbitId: Long, url: String, folder: String?) {
        bookmarks.value = bookmarks.value.map {
            if (it.url == url && it.orbitId == orbitId) it.copy(folder = folder) else it
        }
    }

    override fun observeIsBookmarked(orbitId: Long, url: String): Flow<Boolean> =
        bookmarks.map { list -> list.any { it.url == url && it.orbitId == orbitId } }

    override suspend fun search(orbitId: Long, query: String, limit: Int): List<Bookmark> =
        bookmarks.value
            .filter { it.orbitId == orbitId }
            .filter { it.url.contains(query, true) || it.title.contains(query, true) }
            .sortedByDescending { it.createdAt }
            .take(limit)

    override suspend fun deleteByUrl(orbitId: Long, url: String) {
        bookmarks.value = bookmarks.value.filterNot { it.url == url && it.orbitId == orbitId }
    }

    override suspend fun deleteForOrbit(orbitId: Long) {
        bookmarks.value = bookmarks.value.filterNot { it.orbitId == orbitId }
    }

    override suspend fun clearAll() {
        bookmarks.value = emptyList()
    }
}
