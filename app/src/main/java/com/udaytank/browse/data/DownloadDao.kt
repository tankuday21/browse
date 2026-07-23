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

    /** The active Orbit's downloads (v5.5) — what the Downloads screen and menu badge show. */
    @Query("SELECT * FROM downloads WHERE orbitId = :orbitId ORDER BY createdAt DESC")
    fun observeForOrbit(orbitId: Long): Flow<List<DownloadEntry>>

    /** One Orbit's rows (v5.5) — the orbit-delete purge reads these to delete files first. */
    @Query("SELECT * FROM downloads WHERE orbitId = :orbitId")
    suspend fun getAllForOrbit(orbitId: Long): List<DownloadEntry>

    /** Orbit-delete purge (v5.5). Callers delete the on-disk files first (paths live in rows). */
    @Query("DELETE FROM downloads WHERE orbitId = :orbitId")
    suspend fun deleteForOrbit(orbitId: Long)

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun getById(id: Long): DownloadEntry?

    @Query("UPDATE downloads SET state = :state, error = :error WHERE id = :id")
    suspend fun setState(id: Long, state: String, error: String? = null)

    /**
     * Atomically claim a download for RUNNING only if it hasn't been cancelled or already finished
     * (v6.8 start-path race). Returns the number of rows updated: 1 = claimed, 0 = the row was
     * CANCELLED/DONE or gone. SQLite serializes writes, so this can't interleave with a concurrent
     * ACTION_CANCEL's setState(CANCELLED) — exactly one wins, closing the orphan-file / undead-
     * download race in DownloadService.handleStart.
     */
    @Query("UPDATE downloads SET state = 'RUNNING', error = NULL WHERE id = :id AND state != 'CANCELLED' AND state != 'DONE'")
    suspend fun markRunningIfLive(id: Long): Int

    @Query("UPDATE downloads SET downloadedBytes = :downloaded, totalBytes = :total, segmentState = :segmentState WHERE id = :id")
    suspend fun setProgress(id: Long, downloaded: Long, total: Long, segmentState: String?)

    @Query("UPDATE downloads SET fileName = :fileName, filePath = :filePath, mimeType = :mimeType, etag = :etag, segments = :segments WHERE id = :id")
    suspend fun setFileInfo(id: Long, fileName: String, filePath: String?, mimeType: String?, etag: String?, segments: Int)

    @Insert
    suspend fun insertReturning(entry: DownloadEntry): Long // keep old insert too

    @Query("SELECT * FROM downloads WHERE state IN ('RUNNING','PENDING')")
    suspend fun getActive(): List<DownloadEntry>

    @Query("UPDATE downloads SET attempts = attempts + 1 WHERE id = :id")
    suspend fun incrementAttempts(id: Long)

    @Query("UPDATE downloads SET attempts = 0 WHERE id = :id")
    suspend fun resetAttempts(id: Long)

    /** Global wipe (Black Hole panic-wipe). Callers delete the on-disk files first. */
    @Query("DELETE FROM downloads")
    suspend fun clearAll()
}
