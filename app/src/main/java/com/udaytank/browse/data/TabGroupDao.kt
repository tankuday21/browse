package com.udaytank.browse.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TabGroupDao {
    @Insert
    suspend fun insert(group: TabGroupEntity): Long

    @Query("UPDATE tab_groups SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM tab_groups WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM tab_groups ORDER BY position")
    fun observeAll(): Flow<List<TabGroupEntity>>

    @Query("SELECT * FROM tab_groups ORDER BY position")
    suspend fun getAll(): List<TabGroupEntity>
}
