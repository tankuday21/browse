package com.udaytank.browse.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ClosedTabDao {
    @Insert
    suspend fun insert(entry: ClosedTabEntity)

    /** Ring semantics: keep only the newest [max] rows. */
    @Query(
        "DELETE FROM closed_tabs WHERE id NOT IN " +
            "(SELECT id FROM closed_tabs ORDER BY closedAt DESC, id DESC LIMIT :max)"
    )
    suspend fun trimTo(max: Int)

    @Query("SELECT * FROM closed_tabs ORDER BY closedAt DESC, id DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<ClosedTabEntity>>

    @Query("DELETE FROM closed_tabs WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM closed_tabs")
    suspend fun clear()
}
