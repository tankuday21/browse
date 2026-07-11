package com.udaytank.browse

import com.udaytank.browse.data.DownloadDao
import com.udaytank.browse.data.DownloadEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeDownloadDao : DownloadDao {
    val entries = MutableStateFlow<List<DownloadEntry>>(emptyList())
    private var nextId = 1L

    override suspend fun insert(entry: DownloadEntry) {
        entries.value = entries.value + entry.copy(id = nextId++)
    }

    override fun observeAll(): Flow<List<DownloadEntry>> = entries

    override suspend fun deleteById(id: Long) {
        entries.value = entries.value.filterNot { it.id == id }
    }
}
