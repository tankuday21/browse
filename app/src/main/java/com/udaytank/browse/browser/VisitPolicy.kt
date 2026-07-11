package com.udaytank.browse.browser

import com.udaytank.browse.data.HistoryEntry

object VisitPolicy {

    private const val DUPLICATE_WINDOW_MS = 5_000L

    /**
     * Should this visit be written to history?
     * Skip junk (blank pages) and reload spam (same URL within the window).
     */
    fun shouldRecord(previous: HistoryEntry?, url: String, visitedAt: Long): Boolean {
        if (url.isBlank() || url == "about:blank") return false
        if (previous == null || previous.url != url) return true
        return (visitedAt - previous.visitedAt) >= DUPLICATE_WINDOW_MS
    }
}
