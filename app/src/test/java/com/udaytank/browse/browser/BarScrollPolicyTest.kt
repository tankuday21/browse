package com.udaytank.browse.browser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BarScrollPolicyTest {

    private fun policy() = BarScrollPolicy(hideThresholdPx = 66, showThresholdPx = 8, topThresholdPx = 8)

    @Test
    fun `starts visible`() {
        assertTrue(policy().visible)
    }

    @Test
    fun `hides after cumulative downward scroll beyond threshold`() {
        val p = policy()
        assertTrue(p.onScroll(scrollY = 100, dy = 30))
        assertTrue(p.onScroll(scrollY = 130, dy = 30)) // 60 accumulated, still under 66
        assertFalse(p.onScroll(scrollY = 170, dy = 40)) // 100 accumulated, over 66
    }

    @Test
    fun `stays visible below the hide threshold`() {
        val p = policy()
        assertTrue(p.onScroll(scrollY = 100, dy = 40))
        assertTrue(p.onScroll(scrollY = 126, dy = 26)) // exactly 66: not strictly greater
        assertTrue(p.visible)
    }

    @Test
    fun `small upward scroll shows the bar again`() {
        val p = policy()
        p.onScroll(scrollY = 300, dy = 200) // hidden
        assertFalse(p.visible)
        assertTrue(p.onScroll(scrollY = 290, dy = -10)) // 10 > 8: back
    }

    @Test
    fun `upward scroll accumulates before showing`() {
        val p = policy()
        p.onScroll(scrollY = 300, dy = 200) // hidden
        assertFalse(p.onScroll(scrollY = 295, dy = -5)) // 5, not yet over 8
        assertTrue(p.onScroll(scrollY = 290, dy = -5)) // cumulative 10 > 8
    }

    @Test
    fun `always visible near the top regardless of direction`() {
        val p = policy()
        p.onScroll(scrollY = 300, dy = 200) // hidden
        assertTrue(p.onScroll(scrollY = 5, dy = 50)) // within topThreshold: forced visible
    }

    @Test
    fun `at the very top the bar never hides`() {
        val p = policy()
        assertTrue(p.onScroll(scrollY = 0, dy = 500))
        assertTrue(p.visible)
    }

    @Test
    fun `direction flip resets the downward accumulation`() {
        val p = policy()
        assertTrue(p.onScroll(scrollY = 100, dy = 40)) // +40
        assertTrue(p.onScroll(scrollY = 80, dy = -20)) // flip: accumulation restarts (and -20 shows, already visible)
        assertTrue(p.onScroll(scrollY = 120, dy = 40)) // only 40 counted, not 80 — still visible
        assertFalse(p.onScroll(scrollY = 150, dy = 30)) // 70 > 66 — now hides
    }

    @Test
    fun `direction flip resets the upward accumulation`() {
        val p = policy()
        p.onScroll(scrollY = 300, dy = 200) // hidden
        assertFalse(p.onScroll(scrollY = 295, dy = -5)) // -5
        assertFalse(p.onScroll(scrollY = 300, dy = 5)) // flip down: up-count forgotten (5 < 66 keeps hidden)
        assertFalse(p.onScroll(scrollY = 295, dy = -5)) // -5 again, not -10: still hidden
        assertTrue(p.onScroll(scrollY = 286, dy = -9)) // -14 > 8: shows
    }

    @Test
    fun `zero delta changes nothing`() {
        val p = policy()
        p.onScroll(scrollY = 300, dy = 200) // hidden
        assertFalse(p.onScroll(scrollY = 300, dy = 0))
    }

    @Test
    fun `reset restores visibility and forgets accumulation`() {
        val p = policy()
        p.onScroll(scrollY = 300, dy = 200) // hidden
        p.reset()
        assertTrue(p.visible)
        assertTrue(p.onScroll(scrollY = 330, dy = 30)) // fresh count: 30 < 66
    }
}
