package com.udaytank.browse.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(bookmark: Bookmark)

    /** One Orbit's bookmarks, newest first (v4.4: the Bookmarks screen is Orbit-scoped). */
    @Query("SELECT * FROM bookmarks WHERE orbitId = :orbitId ORDER BY createdAt DESC")
    fun observeForOrbit(orbitId: Long): Flow<List<Bookmark>>

    /** One Orbit's bookmarks (snapshot). */
    @Query("SELECT * FROM bookmarks WHERE orbitId = :orbitId ORDER BY createdAt DESC")
    suspend fun getAllForOrbit(orbitId: Long): List<Bookmark>

    /** All bookmarks across every Orbit — for whole-DB backup export only. */
    @Query("SELECT * FROM bookmarks ORDER BY createdAt DESC")
    suspend fun getAll(): List<Bookmark>

    @Query("UPDATE bookmarks SET folder = :folder WHERE url = :url AND orbitId = :orbitId")
    suspend fun setFolder(orbitId: Long, url: String, folder: String?)

    /** Whether [url] is bookmarked in this Orbit (drives the current-page star state). */
    @Query("SELECT EXISTS(SELECT 1 FROM bookmarks WHERE url = :url AND orbitId = :orbitId)")
    fun observeIsBookmarked(orbitId: Long, url: String): Flow<Boolean>

    /** Address-bar bookmark suggestions, scoped to one Orbit. */
    @Query(
        "SELECT * FROM bookmarks WHERE orbitId = :orbitId AND (url LIKE '%' || :query || '%' " +
            "OR title LIKE '%' || :query || '%') ORDER BY createdAt DESC LIMIT :limit"
    )
    suspend fun search(orbitId: Long, query: String, limit: Int): List<Bookmark>

    @Query("DELETE FROM bookmarks WHERE url = :url AND orbitId = :orbitId")
    suspend fun deleteByUrl(orbitId: Long, url: String)

    /** Purges a deleted Orbit's bookmarks — a hard isolation requirement. */
    @Query("DELETE FROM bookmarks WHERE orbitId = :orbitId")
    suspend fun deleteForOrbit(orbitId: Long)
}
