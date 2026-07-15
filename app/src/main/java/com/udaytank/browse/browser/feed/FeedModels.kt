package com.udaytank.browse.browser.feed

/** Broad topic a home-feed source belongs to. */
enum class FeedCategory { NEWS, SPORTS }

/** One parsed feed entry. publishedAt is epoch millis, 0L if the source omitted/!parseable a date. */
data class FeedItem(
    val sourceId: String,
    val title: String,
    val link: String,
    val publishedAt: Long,
    val thumbnailUrl: String?,
    val category: FeedCategory,
)
