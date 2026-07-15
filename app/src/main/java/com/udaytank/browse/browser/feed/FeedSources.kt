package com.udaytank.browse.browser.feed

/** A built-in, ready-to-subscribe RSS/Atom source shown on the home feed. */
data class FeedSourcePreset(
    val id: String,
    val title: String,
    val url: String,
    val category: FeedCategory,
)

/** Curated presets of reputable HTTPS RSS feeds grouped by [FeedCategory]. */
object FeedSources {
    /** General/world news sources. */
    val NEWS: List<FeedSourcePreset> = listOf(
        FeedSourcePreset(
            id = "bbc-news-world",
            title = "BBC News - World",
            url = "https://feeds.bbci.co.uk/news/world/rss.xml",
            category = FeedCategory.NEWS,
        ),
        FeedSourcePreset(
            id = "the-hindu-national",
            title = "The Hindu - National",
            url = "https://www.thehindu.com/news/national/feeder/default.rss",
            category = FeedCategory.NEWS,
        ),
        FeedSourcePreset(
            id = "ars-technica",
            title = "Ars Technica",
            url = "https://feeds.arstechnica.com/arstechnica/index",
            category = FeedCategory.NEWS,
        ),
    )

    /** Sports sources. */
    val SPORTS: List<FeedSourcePreset> = listOf(
        FeedSourcePreset(
            id = "bbc-sport",
            title = "BBC Sport",
            url = "https://feeds.bbci.co.uk/sport/rss.xml",
            category = FeedCategory.SPORTS,
        ),
        FeedSourcePreset(
            id = "espn-cricinfo",
            title = "ESPNcricinfo",
            url = "https://www.espn.com/espn/rss/cricinfo/news",
            category = FeedCategory.SPORTS,
        ),
    )

    /** All presets: [NEWS] followed by [SPORTS]. */
    val ALL: List<FeedSourcePreset> = NEWS + SPORTS
}
