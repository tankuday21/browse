package com.udaytank.browse.browser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShakeDetectorTest {

    // A calm sample (~1g, gravity only) and a hard jolt (well over the 2.7g threshold).
    private val calm = Triple(0f, 0f, 9.8f)
    private val jolt = Triple(30f, 30f, 30f) // ~5.3g

    private fun ShakeDetector.feed(sample: Triple<Float, Float, Float>, t: Long) =
        onSample(sample.first, sample.second, sample.third, t)

    @Test
    fun `calm handling never fires`() {
        val d = ShakeDetector()
        var fired = false
        for (t in 0L..3000L step 50L) fired = fired || d.feed(calm, t)
        assertFalse(fired)
    }

    @Test
    fun `three jolts within the window fire exactly once`() {
        val d = ShakeDetector()
        assertFalse(d.feed(jolt, 0L))
        assertFalse(d.feed(jolt, 200L))
        assertTrue("third jolt completes the shake", d.feed(jolt, 400L))
    }

    @Test
    fun `a single hard spike alone does not fire`() {
        val d = ShakeDetector()
        assertFalse(d.feed(jolt, 0L))
    }

    @Test
    fun `one physical impact (a tight burst of samples) does not fire`() {
        // Setting the phone down hard emits several consecutive >threshold samples ~16-60ms apart.
        // The min-jolt-gap must treat them as ONE jolt, so a single impact never reaches three.
        val d = ShakeDetector()
        var fired = false
        for (t in 0L..200L step 20L) fired = fired || d.feed(jolt, t) // 11 samples within 200ms
        assertFalse("one impact must not be mistaken for a shake", fired)
    }

    @Test
    fun `reset clears accumulated jolts`() {
        val d = ShakeDetector()
        d.feed(jolt, 0L); d.feed(jolt, 200L) // two jolts banked
        d.reset()
        // After reset, two fresh jolts still shouldn't fire (the pre-reset ones are gone).
        assertFalse(d.feed(jolt, 400L))
        assertFalse(d.feed(jolt, 600L))
    }

    @Test
    fun `jolts spread beyond the window never accumulate`() {
        val d = ShakeDetector()
        // One jolt every 900ms: the window (1000ms) only ever holds ~1-2, never 3.
        var fired = false
        for (i in 0..9) fired = fired || d.feed(jolt, i * 900L)
        assertFalse(fired)
    }

    @Test
    fun `cooldown suppresses an immediate second fire, then a later shake fires again`() {
        val d = ShakeDetector()
        // Jolts 150ms apart (past the 120ms min gap, like a real back-and-forth shake).
        d.feed(jolt, 0L); d.feed(jolt, 150L)
        assertTrue(d.feed(jolt, 300L)) // first shake; cooldown until 2300

        // Immediately after: within the 2000ms cooldown → suppressed.
        assertFalse(d.feed(jolt, 450L))
        assertFalse(d.feed(jolt, 600L))
        assertFalse(d.feed(jolt, 900L))

        // Past the cooldown, a fresh shake fires again.
        assertFalse(d.feed(jolt, 2300L))
        assertFalse(d.feed(jolt, 2450L))
        assertTrue(d.feed(jolt, 2600L))
    }
}
