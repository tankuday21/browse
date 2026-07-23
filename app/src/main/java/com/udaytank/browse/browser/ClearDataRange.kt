package com.udaytank.browse.browser

/**
 * Time range for the Settings "Clear browsing data" action (v6.14). History at or after [cutoff] is
 * deleted; ALL_TIME clears everything. (The WebView cache has no time-range API and is always fully
 * cleared regardless of the chosen range.)
 */
enum class ClearDataRange(val label: String) {
    LAST_HOUR("Last hour"),
    LAST_24H("Last 24 hours"),
    ALL_TIME("All time");

    /** Epoch-millis cutoff relative to [now]; history with visitedAt >= this is deleted. */
    fun cutoff(now: Long): Long = when (this) {
        LAST_HOUR -> now - 3_600_000L
        LAST_24H -> now - 86_400_000L
        ALL_TIME -> 0L
    }
}
