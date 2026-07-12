package com.udaytank.browse

import com.udaytank.browse.data.ReadingListDao
import com.udaytank.browse.data.ReadingListEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeReadingListDao : ReadingListDao {
    val entries = MutableStateFlow<List<ReadingListEntry>>(emptyList())
    private var nextId = 1L

    override fun observeAll(): Flow<List<ReadingListEntry>> =
        entries.map { list -> list.sortedByDescending { it.addedAt } }

    override suspend fun getById(id: Long): ReadingListEntry? =
        entries.value.firstOrNull { it.id == id }

    override suspend fun getUnread(): List<ReadingListEntry> =
        entries.value.filter { it.readAt == null }.sortedBy { it.addedAt }

    override suspend fun insert(entry: ReadingListEntry): Long {
        val id = nextId++
        entries.value = entries.value + entry.copy(id = id)
        return id
    }

    override suspend fun setReadAt(id: Long, readAt: Long?) {
        entries.value = entries.value.map { if (it.id == id) it.copy(readAt = readAt) else it }
    }

    override suspend fun setFilePath(id: Long, filePath: String?) {
        entries.value = entries.value.map { if (it.id == id) it.copy(filePath = filePath) else it }
    }

    override suspend fun deleteById(id: Long) {
        entries.value = entries.value.filterNot { it.id == id }
    }

    override suspend fun existsByUrl(url: String): Boolean =
        entries.value.any { it.url == url }
}
