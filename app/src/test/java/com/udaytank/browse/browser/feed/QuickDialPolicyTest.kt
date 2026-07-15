package com.udaytank.browse.browser.feed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickDialPolicyTest {

    @Test
    fun aggregatesByHostAndOrdersByVisits() {
        val visited = listOf(
            VisitedUrl("https://news.example.com/a", "A", 3),
            VisitedUrl("https://news.example.com/b", "B", 5), // same host -> total 8
            VisitedUrl("https://blog.example.org/x", "X", 6),
            VisitedUrl("https://shop.example.net/y", "Y", 1),
        )

        val dials = QuickDialPolicy.rank(visited, excludeHosts = emptySet())

        assertEquals(3, dials.size)
        // news host total 8 > blog 6 > shop 1
        assertEquals("news.example.com", dials[0].host)
        assertEquals("blog.example.org", dials[1].host)
        assertEquals("shop.example.net", dials[2].host)
        // keeps url of highest-visit member of the host
        assertEquals("https://news.example.com/b", dials[0].url)
    }

    @Test
    fun stripsWwwForLabelAndDropsExcluded() {
        val visited = listOf(
            VisitedUrl("https://www.bbc.com/news", "BBC", 10),
            VisitedUrl("https://www.excluded.com/", "Ex", 99),
        )
        val dials = QuickDialPolicy.rank(visited, excludeHosts = setOf("www.excluded.com"))

        assertEquals(1, dials.size)
        assertEquals("www.bbc.com", dials[0].host)
        assertEquals("bbc.com", dials[0].label)
        assertFalse(dials.any { it.host == "www.excluded.com" })
    }

    @Test
    fun capsAtMax() {
        val visited = (1..12).map { VisitedUrl("https://site$it.com/", "T$it", it) }
        val dials = QuickDialPolicy.rank(visited, excludeHosts = emptySet(), max = 5)

        assertEquals(5, dials.size)
        // highest visits first: site12 (12) down to site8 (8)
        assertEquals("site12.com", dials[0].host)
        assertTrue(dials.none { it.host == "site1.com" })
    }
}
