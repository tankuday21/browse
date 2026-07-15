package com.udaytank.browse.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Insert
    suspend fun insert(entry: HistoryEntry)

    @Query("SELECT * FROM history ORDER BY visitedAt DESC")
    fun observeAll(): Flow<List<HistoryEntry>>

    @Query("SELECT * FROM history ORDER BY visitedAt DESC, id DESC LIMIT 1")
    suspend fun mostRecent(): HistoryEntry?

    @Query(
        "SELECT * FROM history WHERE url LIKE '%' || :query || '%' " +
            "OR title LIKE '%' || :query || '%' ORDER BY visitedAt DESC LIMIT :limit"
    )
    suspend fun search(query: String, limit: Int): List<HistoryEntry>

    @Query("UPDATE history SET visitedAt = :visitedAt WHERE id = :id")
    suspend fun updateVisitedAt(id: Long, visitedAt: Long)

    @Query("UPDATE history SET title = :title WHERE url = :url")
    suspend fun updateTitleForUrl(url: String, title: String)

    @Query("DELETE FROM history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM history")
    suspend fun clearAll()

    /** Most-visited URLs (for home quick dials): grouped by url, ordered by visit count. */
    @Query(
        "SELECT url, title, COUNT(*) AS visits FROM history " +
            "GROUP BY url ORDER BY visits DESC, MAX(visitedAt) DESC LIMIT :limit"
    )
    suspend fun topVisited(limit: Int): List<TopVisitedRow>
}

/** Projection for [HistoryDao.topVisited]; mapped to the pure quick-dial model at the repo boundary. */
data class TopVisitedRow(val url: String, val title: String, val visits: Int)

