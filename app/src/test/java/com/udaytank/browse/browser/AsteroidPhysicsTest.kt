package com.udaytank.browse.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AsteroidPhysicsTest {

    // --- advance ---

    @Test
    fun advance_movesRocksByVelocityTimesDtTimesFactor() {
        val rocks = listOf(
            Rock(x = 100f, y = 0f, r = 10f, vy = 200f),
            Rock(x = 50f, y = 300f, r = 20f, vy = 100f),
        )
        val moved = AsteroidPhysics.advance(rocks, dtSeconds = 0.5f, speedFactor = 2f)
        assertEquals(200f, moved[0].y, 0.001f) // 0 + 200 * 0.5 * 2
        assertEquals(400f, moved[1].y, 0.001f) // 300 + 100 * 0.5 * 2
        // x, r, vy untouched
        assertEquals(100f, moved[0].x, 0.001f)
        assertEquals(10f, moved[0].r, 0.001f)
        assertEquals(200f, moved[0].vy, 0.001f)
    }

    @Test
    fun advance_zeroDt_doesNotMove() {
        val rocks = listOf(Rock(x = 10f, y = 42f, r = 5f, vy = 300f))
        val moved = AsteroidPhysics.advance(rocks, dtSeconds = 0f, speedFactor = 3f)
        assertEquals(42f, moved[0].y, 0.001f)
    }

    @Test
    fun advance_emptyList_staysEmpty() {
        assertTrue(AsteroidPhysics.advance(emptyList(), 0.016f, 1f).isEmpty())
    }

    // --- collides (circle vs rect; ship rect: x±w/2 horizontally, shipY..shipY+w vertically) ---

    @Test
    fun collides_rockFarAway_false() {
        val ship = Ship(x = 500f, w = 100f)
        val rocks = listOf(Rock(x = 100f, y = 100f, r = 30f, vy = 0f))
        assertFalse(AsteroidPhysics.collides(ship, shipY = 900f, rocks = rocks))
    }

    @Test
    fun collides_rockCenteredInsideShip_true() {
        val ship = Ship(x = 500f, w = 100f)
        val rocks = listOf(Rock(x = 500f, y = 950f, r = 5f, vy = 0f))
        assertTrue(AsteroidPhysics.collides(ship, shipY = 900f, rocks = rocks))
    }

    @Test
    fun collides_rockTouchingTopEdge_true() {
        val ship = Ship(x = 500f, w = 100f)
        // Rock directly above ship top edge (y = 900), circle bottom exactly reaches it.
        val rocks = listOf(Rock(x = 500f, y = 870f, r = 30f, vy = 0f))
        assertTrue(AsteroidPhysics.collides(ship, shipY = 900f, rocks = rocks))
    }

    @Test
    fun collides_rockJustAboveTopEdge_false() {
        val ship = Ship(x = 500f, w = 100f)
        // 1px gap between circle bottom (870 + 29) and ship top (900).
        val rocks = listOf(Rock(x = 500f, y = 870f, r = 29f, vy = 0f))
        assertFalse(AsteroidPhysics.collides(ship, shipY = 900f, rocks = rocks))
    }

    @Test
    fun collides_cornerDiagonal_missBeyondRadius() {
        val ship = Ship(x = 500f, w = 100f) // right edge x=550, top y=900
        // Rock 30 up and 30 right of the corner: distance sqrt(1800) ≈ 42.4 > r=40.
        val rocks = listOf(Rock(x = 580f, y = 870f, r = 40f, vy = 0f))
        assertFalse(AsteroidPhysics.collides(ship, shipY = 900f, rocks = rocks))
    }

    @Test
    fun collides_cornerDiagonal_hitWithinRadius() {
        val ship = Ship(x = 500f, w = 100f) // right edge x=550, top y=900
        // Same offset but r=45 > 42.4 — clipping the corner counts.
        val rocks = listOf(Rock(x = 580f, y = 870f, r = 45f, vy = 0f))
        assertTrue(AsteroidPhysics.collides(ship, shipY = 900f, rocks = rocks))
    }

    @Test
    fun collides_sideEdgeOverlap_true() {
        val ship = Ship(x = 500f, w = 100f) // left edge x=450
        val rocks = listOf(Rock(x = 430f, y = 950f, r = 25f, vy = 0f))
        assertTrue(AsteroidPhysics.collides(ship, shipY = 900f, rocks = rocks))
    }

    @Test
    fun collides_noRocks_false() {
        assertFalse(AsteroidPhysics.collides(Ship(0f, 10f), 0f, emptyList()))
    }

    // --- spawnParams (difficulty curve) ---

    @Test
    fun spawnParams_intervalNeverIncreasesAndNeverNegative() {
        var previous = Float.MAX_VALUE
        for (second in 0..600) {
            val params = AsteroidPhysics.spawnParams(second.toFloat())
            assertTrue("interval must be positive", params.intervalSeconds > 0f)
            assertTrue("interval must not increase over time", params.intervalSeconds <= previous)
            previous = params.intervalSeconds
        }
    }

    @Test
    fun spawnParams_intervalCappedAtFloor() {
        val late = AsteroidPhysics.spawnParams(10_000f)
        val evenLater = AsteroidPhysics.spawnParams(100_000f)
        assertEquals(late.intervalSeconds, evenLater.intervalSeconds, 0.0001f)
        assertTrue(late.intervalSeconds > 0f)
    }

    @Test
    fun spawnParams_speedFactorMonotonicNonDecreasing() {
        var previous = 0f
        for (second in 0..600) {
            val params = AsteroidPhysics.spawnParams(second.toFloat())
            assertTrue("speed must not decrease over time", params.speedFactor >= previous)
            previous = params.speedFactor
        }
    }

    @Test
    fun spawnParams_speedFactorCapped() {
        val late = AsteroidPhysics.spawnParams(10_000f)
        val evenLater = AsteroidPhysics.spawnParams(100_000f)
        assertEquals(late.speedFactor, evenLater.speedFactor, 0.0001f)
    }

    @Test
    fun spawnParams_startsAtBaseDifficulty() {
        val start = AsteroidPhysics.spawnParams(0f)
        assertEquals(1f, start.speedFactor, 0.0001f)
        assertTrue(start.intervalSeconds >= AsteroidPhysics.spawnParams(60f).intervalSeconds)
    }

    @Test
    fun spawnParams_negativeElapsedTreatedAsZero() {
        val negative = AsteroidPhysics.spawnParams(-5f)
        val zero = AsteroidPhysics.spawnParams(0f)
        assertEquals(zero.intervalSeconds, negative.intervalSeconds, 0.0001f)
        assertEquals(zero.speedFactor, negative.speedFactor, 0.0001f)
    }
}
