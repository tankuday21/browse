package com.udaytank.browse

import com.udaytank.browse.data.Bookmark
import com.udaytank.browse.data.BookmarkDao
import com.udaytank.browse.data.HistoryDao
import com.udaytank.browse.data.HistoryEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeHistoryDao : HistoryDao {
    val entries = MutableStateFlow<List<HistoryEntry>>(emptyList())

    override suspend fun insert(entry: HistoryEntry) {
        entries.value = entries.value + entry.copy(id = (entries.value.size + 1).toLong())
    }

    override fun observeAll(): Flow<List<HistoryEntry>> =
        entries.map { it.sortedByDescending { e -> e.visitedAt } }

    override suspend fun mostRecent(): HistoryEntry? =
        entries.value.maxWithOrNull(compareBy({ it.visitedAt }, { it.id }))

    override suspend fun search(query: String, limit: Int): List<HistoryEntry> =
        entries.value
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

    override suspend fun clearAll() {
        entries.value = emptyList()
    }
}

class FakeBookmarkDao : BookmarkDao {
    val bookmarks = MutableStateFlow<List<Bookmark>>(emptyList())

    override suspend fun insert(bookmark: Bookmark) {
        if (bookmarks.value.none { it.url == bookmark.url }) {
            bookmarks.value = bookmarks.value + bookmark
        }
    }

    override fun observeAll(): Flow<List<Bookmark>> = bookmarks

    override fun observeIsBookmarked(url: String): Flow<Boolean> =
        bookmarks.map { list -> list.any { it.url == url } }

    override suspend fun search(query: String, limit: Int): List<Bookmark> =
        bookmarks.value
            .filter { it.url.contains(query, true) || it.title.contains(query, true) }
            .sortedByDescending { it.createdAt }
            .take(limit)

    override suspend fun deleteByUrl(url: String) {
        bookmarks.value = bookmarks.value.filterNot { it.url == url }
    }
}
