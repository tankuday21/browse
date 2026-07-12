package com.udaytank.browse.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingListDao {
    @Query("SELECT * FROM reading_list ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<ReadingListEntry>>

    @Query("SELECT * FROM reading_list WHERE id = :id")
    suspend fun getById(id: Long): ReadingListEntry?

    @Query("SELECT * FROM reading_list WHERE readAt IS NULL ORDER BY addedAt ASC")
    suspend fun getUnread(): List<ReadingListEntry>

    @Insert
    suspend fun insert(entry: ReadingListEntry): Long

    @Query("UPDATE reading_list SET readAt = :readAt WHERE id = :id")
    suspend fun setReadAt(id: Long, readAt: Long?)

    @Query("UPDATE reading_list SET filePath = :filePath WHERE id = :id")
    suspend fun setFilePath(id: Long, filePath: String?)

    @Query("DELETE FROM reading_list WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT EXISTS(SELECT 1 FROM reading_list WHERE url = :url)")
    suspend fun existsByUrl(url: String): Boolean
}
