package com.udaytank.browse.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ZappedElementDao {
    @Query("SELECT * FROM zapped_elements WHERE host = :host ORDER BY createdAt DESC")
    fun observeForHost(host: String): Flow<List<ZappedElementEntity>>

    @Query("SELECT selector FROM zapped_elements WHERE host = :host")
    suspend fun selectorsForHost(host: String): List<String>

    @Query("SELECT COUNT(*) FROM zapped_elements WHERE host = :host")
    fun countForHost(host: String): Flow<Int>

    @Insert
    suspend fun insert(element: ZappedElementEntity): Long

    @Query("DELETE FROM zapped_elements WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM zapped_elements WHERE host = :host")
    suspend fun deleteForHost(host: String)
}
