package com.udaytank.browse.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeedTrackerTest {
    @Test fun `computes speed over window and keeps history`() {
        val t = SpeedTracker(windowMs = 1_000)
        t.sample(0, 0); t.sample(500, 500_000); t.sample(1_000, 1_000_000)
        assertEquals(1_000_000, t.bytesPerSecond(1_000))
        assertTrue(t.history().isNotEmpty())
    }
    @Test fun `stale samples fall out of window`() {
        val t = SpeedTracker(windowMs = 1_000)
        t.sample(0, 0); t.sample(100, 1_000_000); t.sample(5_000, 1_000_000)
        assertEquals(0, t.bytesPerSecond(5_000))
    }
}
