package com.udaytank.browse.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ClosedTabDao {
    @Insert
    suspend fun insert(entry: ClosedTabEntity)

    /**
     * Ring semantics PER ORBIT (v6.16): keep only the newest [max] rows *of this Orbit*. A global
     * trim let a busy Orbit evict another Orbit's entries.
     */
    @Query(
        "DELETE FROM closed_tabs WHERE orbitId = :orbitId AND id NOT IN " +
            "(SELECT id FROM closed_tabs WHERE orbitId = :orbitId " +
            "ORDER BY closedAt DESC, id DESC LIMIT :max)"
    )
    suspend fun trimTo(orbitId: Long, max: Int)

    /** Orbit-scoped (v6.16): recently-closed must never cross profiles. */
    @Query(
        "SELECT * FROM closed_tabs WHERE orbitId = :orbitId " +
            "ORDER BY closedAt DESC, id DESC LIMIT :limit"
    )
    fun observeRecent(orbitId: Long, limit: Int): Flow<List<ClosedTabEntity>>

    @Query("DELETE FROM closed_tabs WHERE orbitId = :orbitId")
    suspend fun deleteForOrbit(orbitId: Long)

    @Query("DELETE FROM closed_tabs WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM closed_tabs")
    suspend fun clear()
}
