package com.udaytank.browse.data.feed

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FeedDao {
    // ── Items ──────────────────────────────────────────────
    /** Upsert by unique [FeedItemEntity.link] (REPLACE drops the stale row for that link). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertItems(items: List<FeedItemEntity>)

    @Query("SELECT * FROM feed_items WHERE category = :category ORDER BY publishedAt DESC, id DESC LIMIT :limit")
    fun observeByCategory(category: String, limit: Int): Flow<List<FeedItemEntity>>

    /** Keep only the newest [keep] items overall; drop the rest (rolling cache window). */
    @Query(
        "DELETE FROM feed_items WHERE id NOT IN " +
            "(SELECT id FROM feed_items ORDER BY publishedAt DESC, id DESC LIMIT :keep)"
    )
    suspend fun pruneToNewest(keep: Int)

    @Query("DELETE FROM feed_items")
    suspend fun clearItems()

    // ── Sources ────────────────────────────────────────────
    @Query("SELECT * FROM rss_sources ORDER BY category, title")
    fun observeSources(): Flow<List<RssSourceEntity>>

    @Query("SELECT * FROM rss_sources WHERE enabled = 1")
    suspend fun enabledSources(): List<RssSourceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSources(sources: List<RssSourceEntity>)

    @Query("UPDATE rss_sources SET enabled = :enabled WHERE id = :id")
    suspend fun setSourceEnabled(id: String, enabled: Boolean)

    @Query("DELETE FROM rss_sources WHERE id = :id")
    suspend fun deleteSource(id: String)

    /** Black Hole panic-wipe: drop user-added feeds (id 'custom:…'); seeded presets are kept. */
    @Query("DELETE FROM rss_sources WHERE id LIKE 'custom:%'")
    suspend fun clearCustomSources()

    @Query("SELECT COUNT(*) FROM rss_sources")
    suspend fun sourceCount(): Int
}
