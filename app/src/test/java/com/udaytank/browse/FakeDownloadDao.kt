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

    override suspend fun getById(id: Long): DownloadEntry? =
        entries.value.firstOrNull { it.id == id }

    override suspend fun setState(id: Long, state: String, error: String?) {
        entries.value = entries.value.map {
            if (it.id == id) it.copy(state = state, error = error) else it
        }
    }

    override suspend fun setProgress(id: Long, downloaded: Long, total: Long, segmentState: String?) {
        entries.value = entries.value.map {
            if (it.id == id) {
                it.copy(downloadedBytes = downloaded, totalBytes = total, segmentState = segmentState)
            } else it
        }
    }

    override suspend fun setFileInfo(
        id: Long,
        fileName: String,
        filePath: String?,
        mimeType: String?,
        etag: String?,
        segments: Int,
    ) {
        entries.value = entries.value.map {
            if (it.id == id) {
                it.copy(fileName = fileName, filePath = filePath, mimeType = mimeType, etag = etag, segments = segments)
            } else it
        }
    }

    override suspend fun insertReturning(entry: DownloadEntry): Long {
        val id = nextId++
        entries.value = entries.value + entry.copy(id = id)
        return id
    }

    override suspend fun getActive(): List<DownloadEntry> =
        entries.value.filter { it.state == "RUNNING" || it.state == "PENDING" }
}
