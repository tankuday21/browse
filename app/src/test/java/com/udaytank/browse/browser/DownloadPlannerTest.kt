package com.udaytank.browse.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadPlannerTest {
    @Test fun `small unknown or unsupported gets one segment`() {
        assertEquals(1, DownloadPlanner.segmentCount(1_000_000, true))
        assertEquals(1, DownloadPlanner.segmentCount(-1, true))
        assertEquals(1, DownloadPlanner.segmentCount(50_000_000, false))
    }
    @Test fun `medium gets three large gets six`() {
        assertEquals(3, DownloadPlanner.segmentCount(10_000_000, true))
        assertEquals(6, DownloadPlanner.segmentCount(100_000_000, true))
    }
    @Test fun `plan covers every byte exactly once`() {
        val segs = DownloadPlanner.plan(10_000_001, 3)
        assertEquals(0, segs.first().start)
        assertEquals(10_000_000, segs.last().endInclusive)
        for (i in 1 until segs.size) assertEquals(segs[i - 1].endInclusive + 1, segs[i].start)
    }
    @Test fun `resume validation`() {
        assertTrue(DownloadPlanner.canResume("abc", "abc", 100, 100))
        assertTrue(DownloadPlanner.canResume(null, null, 100, 100))
        assertFalse(DownloadPlanner.canResume("abc", "def", 100, 100))
        assertFalse(DownloadPlanner.canResume("abc", "abc", 100, 200))
    }
    @Test fun `state round-trips`() {
        val planned = DownloadPlanner.plan(300, 3)
        val withProgress = planned.map { it.copy(downloaded = it.index * 10L) }
        val decoded = DownloadPlanner.decodeState(DownloadPlanner.encodeState(withProgress), planned)
        assertEquals(listOf(0L, 10L, 20L), decoded.map { it.downloaded })
    }
    @Test fun `corrupt state falls back to planned zeros`() {
        val planned = DownloadPlanner.plan(300, 3)
        assertEquals(listOf(0L, 0L, 0L), DownloadPlanner.decodeState("garbage", planned).map { it.downloaded })
        assertEquals(listOf(0L, 0L, 0L), DownloadPlanner.decodeState(null, planned).map { it.downloaded })
    }
}
