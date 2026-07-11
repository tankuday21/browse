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
}
