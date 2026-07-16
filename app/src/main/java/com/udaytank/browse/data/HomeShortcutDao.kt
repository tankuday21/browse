package com.udaytank.browse.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface HomeShortcutDao {
    /** One Orbit's shortcut tiles, in grid order (v4.4: the home grid is per-Orbit). */
    @Query("SELECT * FROM home_shortcuts WHERE orbitId = :orbitId ORDER BY position ASC")
    fun observeForOrbit(orbitId: Long): Flow<List<HomeShortcutEntity>>

    @Query("SELECT * FROM home_shortcuts WHERE orbitId = :orbitId ORDER BY position ASC")
    suspend fun getAllForOrbit(orbitId: Long): List<HomeShortcutEntity>

    /** All shortcuts across every Orbit — for whole-DB backup export only. */
    @Query("SELECT * FROM home_shortcuts ORDER BY position ASC")
    suspend fun getAll(): List<HomeShortcutEntity>

    @Insert
    suspend fun insert(shortcut: HomeShortcutEntity): Long

    @Insert
    suspend fun insertAll(shortcuts: List<HomeShortcutEntity>)

    @Query("DELETE FROM home_shortcuts WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM home_shortcuts WHERE orbitId = :orbitId")
    suspend fun deleteForOrbit(orbitId: Long)

    /**
     * Reordering one Orbit's grid = atomic rewrite of just that Orbit's rows; callers pass the
     * list with positions reindexed. Other Orbits' tiles are untouched.
     */
    @Transaction
    suspend fun replaceAllForOrbit(orbitId: Long, shortcuts: List<HomeShortcutEntity>) {
        deleteForOrbit(orbitId)
        insertAll(shortcuts)
    }
}
