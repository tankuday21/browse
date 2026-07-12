package com.udaytank.browse.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface HomeShortcutDao {
    @Query("SELECT * FROM home_shortcuts ORDER BY position ASC")
    fun observeAll(): Flow<List<HomeShortcutEntity>>

    @Query("SELECT * FROM home_shortcuts ORDER BY position ASC")
    suspend fun getAll(): List<HomeShortcutEntity>

    @Insert
    suspend fun insert(shortcut: HomeShortcutEntity): Long

    @Insert
    suspend fun insertAll(shortcuts: List<HomeShortcutEntity>)

    @Query("DELETE FROM home_shortcuts WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM home_shortcuts")
    suspend fun deleteAll()

    /** Reordering = atomic full-list rewrite; callers pass the list with positions reindexed. */
    @Transaction
    suspend fun replaceAll(shortcuts: List<HomeShortcutEntity>) {
        deleteAll()
        insertAll(shortcuts)
    }
}
