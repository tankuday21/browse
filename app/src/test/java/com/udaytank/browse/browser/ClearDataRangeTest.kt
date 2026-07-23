package com.udaytank.browse.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClearDataRangeTest {

    private val now = 10_000_000_000L

    @Test
    fun `cutoffs are computed relative to now`() {
        assertEquals(now - 3_600_000L, ClearDataRange.LAST_HOUR.cutoff(now))
        assertEquals(now - 86_400_000L, ClearDataRange.LAST_24H.cutoff(now))
        assertEquals(0L, ClearDataRange.ALL_TIME.cutoff(now))
    }

    @Test
    fun `wider ranges have earlier cutoffs`() {
        val hour = ClearDataRange.LAST_HOUR.cutoff(now)
        val day = ClearDataRange.LAST_24H.cutoff(now)
        val all = ClearDataRange.ALL_TIME.cutoff(now)
        assertTrue(hour > day)
        assertTrue(day > all)
    }
}
