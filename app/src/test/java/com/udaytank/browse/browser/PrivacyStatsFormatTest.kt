package com.udaytank.browse.browser

import org.junit.Assert.assertEquals
import org.junit.Test

class PrivacyStatsFormatTest {

    @Test
    fun `zero does not crash and formats plainly`() {
        // The caller hides the stats block at zero; format(0) must still be well-formed.
        val (count, saved) = PrivacyStatsFormat.format(0)
        assertEquals("0 ads & trackers blocked", count)
        assertEquals("~0.0 MB saved (estimated)", saved)
    }

    @Test
    fun `single block stays in the megabyte path`() {
        val (count, saved) = PrivacyStatsFormat.format(1)
        assertEquals("1 ads & trackers blocked", count)
        assertEquals("~0.0 MB saved (estimated)", saved) // 50 KB rounds to 0.0 MB
    }

    @Test
    fun `twenty blocks is just under one megabyte`() {
        val (count, saved) = PrivacyStatsFormat.format(20)
        assertEquals("20 ads & trackers blocked", count)
        assertEquals("~1.0 MB saved (estimated)", saved) // 1000 KB = 0.976... MB -> 1.0
    }

    @Test
    fun `thousands get separators and one MB decimal`() {
        val (count, saved) = PrivacyStatsFormat.format(1_234)
        assertEquals("1,234 ads & trackers blocked", count)
        assertEquals("~60.3 MB saved (estimated)", saved) // 1234 * 50 KB = 60.25.. MB
    }

    @Test
    fun `a million blocks switches to the GB path with two decimals`() {
        val (count, saved) = PrivacyStatsFormat.format(1_000_000)
        assertEquals("1,000,000 ads & trackers blocked", count)
        assertEquals("~47.68 GB saved (estimated)", saved) // 50e6 KB = 47.683.. GB
    }
}
