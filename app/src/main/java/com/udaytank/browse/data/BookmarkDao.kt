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

    @Query("SELECT * FROM bookmarks ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<Bookmark>>

    @Query("SELECT EXISTS(SELECT 1 FROM bookmarks WHERE url = :url)")
    fun observeIsBookmarked(url: String): Flow<Boolean>

    @Query(
        "SELECT * FROM bookmarks WHERE url LIKE '%' || :query || '%' " +
            "OR title LIKE '%' || :query || '%' ORDER BY createdAt DESC LIMIT :limit"
    )
    suspend fun search(query: String, limit: Int): List<Bookmark>

    @Query("DELETE FROM bookmarks WHERE url = :url")
    suspend fun deleteByUrl(url: String)
}
