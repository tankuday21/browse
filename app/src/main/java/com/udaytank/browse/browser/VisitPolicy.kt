package com.udaytank.browse.browser

import com.udaytank.browse.data.HistoryEntry

/** What to do with a finished page visit. */
sealed interface VisitDecision {
    data object Skip : VisitDecision
    data object RecordNew : VisitDecision

    /** Same page as the latest entry — bump its timestamp instead of adding a row. */
    data class RefreshExisting(val existingId: Long) : VisitDecision
}

object VisitPolicy {

    /**
     * Junk pages are skipped; a page different from the latest entry is
     * recorded; the same page again (a reload, however much later) only
     * refreshes the existing entry so history never fills with duplicates.
     */
    fun decide(previous: HistoryEntry?, url: String): VisitDecision {
        if (url.isBlank() || url == "about:blank") return VisitDecision.Skip
        if (previous == null || previous.url != url) return VisitDecision.RecordNew
        return VisitDecision.RefreshExisting(previous.id)
    }
}
