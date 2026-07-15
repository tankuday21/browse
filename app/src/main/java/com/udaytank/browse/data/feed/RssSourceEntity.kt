package com.udaytank.browse.data.feed

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A feed source the user has (preset or custom). [enabled] toggles it in the feed;
 * [category] is "NEWS"/"SPORTS". Presets are seeded on first run from `FeedSources`.
 */
@Entity(tableName = "rss_sources")
data class RssSourceEntity(
    @PrimaryKey val id: String,
    val title: String,
    val url: String,
    val category: String,
    val enabled: Boolean,
)
