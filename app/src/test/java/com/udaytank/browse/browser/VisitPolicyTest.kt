package com.udaytank.browse.browser

import com.udaytank.browse.data.HistoryEntry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VisitPolicyTest {

    private fun prev(url: String, at: Long) = HistoryEntry(url = url, title = "t", visitedAt = at)

    @Test
    fun `first ever visit is recorded`() {
        assertTrue(VisitPolicy.shouldRecord(null, "https://a.com", 1_000L))
    }

    @Test
    fun `different url is always recorded`() {
        assertTrue(VisitPolicy.shouldRecord(prev("https://a.com", 1_000L), "https://b.com", 2_000L))
    }

    @Test
    fun `same url within 5 seconds is skipped (reload spam)`() {
        assertFalse(VisitPolicy.shouldRecord(prev("https://a.com", 1_000L), "https://a.com", 4_000L))
    }

    @Test
    fun `same url after 5 seconds is recorded again`() {
        assertTrue(VisitPolicy.shouldRecord(prev("https://a.com", 1_000L), "https://a.com", 7_000L))
    }

    @Test
    fun `blank pages are never recorded`() {
        assertFalse(VisitPolicy.shouldRecord(null, "about:blank", 1_000L))
        assertFalse(VisitPolicy.shouldRecord(null, "", 1_000L))
    }
}
