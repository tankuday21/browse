package com.udaytank.browse.browser

import com.udaytank.browse.data.HistoryEntry
import org.junit.Assert.assertEquals
import org.junit.Test

class VisitPolicyTest {

    private fun prev(id: Long, url: String) = HistoryEntry(id = id, url = url, title = "t", visitedAt = 1_000L)

    @Test
    fun `first ever visit records a new entry`() {
        assertEquals(VisitDecision.RecordNew, VisitPolicy.decide(null, "https://a.com"))
    }

    @Test
    fun `different url records a new entry`() {
        assertEquals(
            VisitDecision.RecordNew,
            VisitPolicy.decide(prev(1, "https://a.com"), "https://b.com")
        )
    }

    @Test
    fun `same url as latest entry refreshes it - no matter how much time passed`() {
        assertEquals(
            VisitDecision.RefreshExisting(existingId = 7),
            VisitPolicy.decide(prev(7, "https://a.com"), "https://a.com")
        )
    }

    @Test
    fun `blank pages are skipped`() {
        assertEquals(VisitDecision.Skip, VisitPolicy.decide(null, "about:blank"))
        assertEquals(VisitDecision.Skip, VisitPolicy.decide(null, ""))
    }
}
