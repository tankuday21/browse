package com.udaytank.browse.browser

import org.junit.Assert.assertEquals
import org.junit.Test

class BarScrollPolicyTest {

    @Test
    fun shrinksAfterCumulativeDownScroll() {
        val p = BarScrollPolicy(shrinkThresholdPx = 60, expandThresholdPx = 8)
        assertEquals(BarState.Full, p.onScroll(0, 0))
        assertEquals(BarState.Full, p.onScroll(40, 40))
        assertEquals(BarState.Slim, p.onScroll(120, 80)) // cumulative down > 60
    }

    @Test
    fun expandsOnSmallUpScroll() {
        val p = BarScrollPolicy(60, 8)
        p.onScroll(200, 200)                 // Slim
        assertEquals(BarState.Full, p.onScroll(188, -12))
    }

    @Test
    fun alwaysFullNearTop() {
        val p = BarScrollPolicy(60, 8)
        p.onScroll(300, 300)
        assertEquals(BarState.Full, p.onScroll(4, -296))
    }

    @Test
    fun directionFlipResetsAccumulation() {
        val p = BarScrollPolicy(60, 8)
        p.onScroll(50, 50); p.onScroll(45, -5) // flip
        assertEquals(BarState.Full, p.state)
    }
}
