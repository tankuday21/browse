package com.udaytank.browse.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Insert
    suspend fun insert(entry: HistoryEntry)

    /** One Orbit's history, newest first (v4.3: the History screen is Orbit-scoped). */
    @Query("SELECT * FROM history WHERE orbitId = :orbitId ORDER BY visitedAt DESC")
    fun observeForOrbit(orbitId: Long): Flow<List<HistoryEntry>>

    /** Latest entry in one Orbit — feeds per-Orbit de-dup so a reload never bumps another Orbit's row. */
    @Query("SELECT * FROM history WHERE orbitId = :orbitId ORDER BY visitedAt DESC, id DESC LIMIT 1")
    suspend fun mostRecent(orbitId: Long): HistoryEntry?

    /** Address-bar suggestions, scoped to one Orbit so URLs never autocomplete across Orbits. */
    @Query(
        "SELECT * FROM history WHERE orbitId = :orbitId AND (url LIKE '%' || :query || '%' " +
            "OR title LIKE '%' || :query || '%') ORDER BY visitedAt DESC LIMIT :limit"
    )
    suspend fun search(orbitId: Long, query: String, limit: Int): List<HistoryEntry>

    @Query("UPDATE history SET visitedAt = :visitedAt WHERE id = :id")
    suspend fun updateVisitedAt(id: Long, visitedAt: Long)

    // Title is a property of the URL, not the Orbit; refreshing it across Orbits is harmless
    // and keeps every Orbit's rows for that URL current, so this stays intentionally unscoped.
    @Query("UPDATE history SET title = :title WHERE url = :url")
    suspend fun updateTitleForUrl(url: String, title: String)

    @Query("DELETE FROM history WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** Clears one Orbit's history (the Orbit-scoped History screen's "Clear" action). */
    @Query("DELETE FROM history WHERE orbitId = :orbitId")
    suspend fun clearForOrbit(orbitId: Long)

    /** Purges a deleted Orbit's history — a hard isolation requirement, alongside cookie + tab purge. */
    @Query("DELETE FROM history WHERE orbitId = :orbitId")
    suspend fun deleteForOrbit(orbitId: Long)

    /** Global wipe for Settings' "Clear all history" (explicitly all Orbits). */
    @Query("DELETE FROM history")
    suspend fun clearAll()

    /** Most-visited URLs in one Orbit (for that Orbit's home quick dials). */
    @Query(
        "SELECT url, title, COUNT(*) AS visits FROM history WHERE orbitId = :orbitId " +
            "GROUP BY url ORDER BY visits DESC, MAX(visitedAt) DESC LIMIT :limit"
    )
    suspend fun topVisited(orbitId: Long, limit: Int): List<TopVisitedRow>
}

/** Projection for [HistoryDao.topVisited]; mapped to the pure quick-dial model at the repo boundary. */
data class TopVisitedRow(val url: String, val title: String, val visits: Int)
