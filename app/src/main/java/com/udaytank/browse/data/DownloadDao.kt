package com.udaytank.browse.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Insert
    suspend fun insert(entry: DownloadEntry)

    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DownloadEntry>>

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun getById(id: Long): DownloadEntry?

    @Query("UPDATE downloads SET state = :state, error = :error WHERE id = :id")
    suspend fun setState(id: Long, state: String, error: String? = null)

    @Query("UPDATE downloads SET downloadedBytes = :downloaded, totalBytes = :total, segmentState = :segmentState WHERE id = :id")
    suspend fun setProgress(id: Long, downloaded: Long, total: Long, segmentState: String?)

    @Query("UPDATE downloads SET fileName = :fileName, filePath = :filePath, mimeType = :mimeType, etag = :etag, segments = :segments WHERE id = :id")
    suspend fun setFileInfo(id: Long, fileName: String, filePath: String?, mimeType: String?, etag: String?, segments: Int)

    @Insert
    suspend fun insertReturning(entry: DownloadEntry): Long // keep old insert too

    @Query("SELECT * FROM downloads WHERE state IN ('RUNNING','PENDING')")
    suspend fun getActive(): List<DownloadEntry>
}
