package com.udaytank.browse.data.feed

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A cached feed entry (v3.2 home feed). [category] is stored as a plain string ("NEWS"/"SPORTS")
 * so the data layer stays independent of the pure `browser.feed` model enum. [link] is uniquely
 * indexed so re-fetching a source replaces rather than duplicates items.
 */
@Entity(
    tableName = "feed_items",
    indices = [Index(value = ["link"], unique = true)],
)
data class FeedItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceId: String,
    val title: String,
    val link: String,
    val publishedAt: Long,
    val thumbnailUrl: String?,
    val category: String,
)
