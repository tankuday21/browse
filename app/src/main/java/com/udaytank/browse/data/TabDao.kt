package com.udaytank.browse.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface TabDao {
    @Query("SELECT * FROM tabs ORDER BY position")
    suspend fun getAll(): List<TabEntity>

    @Insert
    suspend fun insert(tab: TabEntity): Long

    @Query("DELETE FROM tabs WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** Marks exactly one tab active: SQLite evaluates (id = :id) to 1 or 0. */
    @Query("UPDATE tabs SET isActive = (id = :id)")
    suspend fun setActive(id: Long)

    @Query("UPDATE tabs SET url = :url, title = :title WHERE id = :id")
    suspend fun updateContent(id: Long, url: String, title: String)

    @Query("UPDATE tabs SET groupId = :groupId WHERE id = :id")
    suspend fun setGroup(id: Long, groupId: Long?)

    @Query("UPDATE tabs SET pinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean)

    @Query("UPDATE tabs SET locked = :locked WHERE id = :id")
    suspend fun setLocked(id: Long, locked: Boolean)

    @Query("UPDATE tabs SET groupId = NULL WHERE groupId = :groupId")
    suspend fun clearGroup(groupId: Long)

    /** Global wipe (Black Hole panic-wipe). TabManager clears its in-memory state alongside. */
    @Query("DELETE FROM tabs")
    suspend fun clearAll()
}
