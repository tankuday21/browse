package com.udaytank.browse.browser

/**
 * The player's starship for the offline asteroid game (K1). [x] is the horizontal CENTER in
 * pixels; [w] the full width. The collision hitbox is a rect [x]±[w]/2 wide starting at the
 * ship's top edge (`shipY`, passed separately since only x moves) and [w] tall.
 */
data class Ship(val x: Float, val w: Float)

/** A falling asteroid: center ([x], [y]), radius [r], downward speed [vy] in px/second. */
data class Rock(val x: Float, val y: Float, val r: Float, val vy: Float)

/** Spawn cadence + global speed multiplier for the current difficulty (see [AsteroidPhysics.spawnParams]). */
data class SpawnParams(val intervalSeconds: Float, val speedFactor: Float)

/**
 * Pure game math for the "Lost in space" asteroid game (K1): frame advancement, circle-vs-rect
 * collision, and the difficulty curve. No Android/Compose imports — unit-testable on the JVM;
 * the Compose layer (ui/game/AsteroidGame.kt) owns rendering, input, and the frame clock.
 */
object AsteroidPhysics {

    // Difficulty curve: spawn interval shrinks and rocks speed up as the run goes on, both
    // monotonically and both hard-capped so long runs stay survivable-in-principle.
    private const val BASE_INTERVAL_SECONDS = 1.1f
    private const val MIN_INTERVAL_SECONDS = 0.35f
    private const val INTERVAL_DECAY_PER_SECOND = 0.012f
    private const val BASE_SPEED_FACTOR = 1f
    private const val MAX_SPEED_FACTOR = 2.6f
    private const val SPEED_GROWTH_PER_SECOND = 0.025f

    /** Moves every rock down by `vy * dt * speedFactor`. Off-screen culling is the caller's job. */
    fun advance(rocks: List<Rock>, dtSeconds: Float, speedFactor: Float): List<Rock> =
        rocks.map { it.copy(y = it.y + it.vy * dtSeconds * speedFactor) }

    /**
     * Circle-vs-rect test of every rock against the ship's hitbox (see [Ship]): clamp the rock
     * center into the rect, then compare the residual distance against the rock's radius.
     * Touching (distance == r) counts as a hit.
     */
    fun collides(ship: Ship, shipY: Float, rocks: List<Rock>): Boolean {
        val left = ship.x - ship.w / 2f
        val right = ship.x + ship.w / 2f
        val top = shipY
        val bottom = shipY + ship.w
        return rocks.any { rock ->
            val nearestX = rock.x.coerceIn(left, right)
            val nearestY = rock.y.coerceIn(top, bottom)
            val dx = rock.x - nearestX
            val dy = rock.y - nearestY
            dx * dx + dy * dy <= rock.r * rock.r
        }
    }

    /**
     * Difficulty at [elapsedSeconds] into a run: the spawn interval decays linearly to a floor
     * of [MIN_INTERVAL_SECONDS] (never zero or negative) while the speed multiplier grows
     * linearly to a cap of [MAX_SPEED_FACTOR]. Negative elapsed (clock weirdness) reads as 0.
     */
    fun spawnParams(elapsedSeconds: Float): SpawnParams {
        val elapsed = elapsedSeconds.coerceAtLeast(0f)
        return SpawnParams(
            intervalSeconds = (BASE_INTERVAL_SECONDS - elapsed * INTERVAL_DECAY_PER_SECOND)
                .coerceAtLeast(MIN_INTERVAL_SECONDS),
            speedFactor = (BASE_SPEED_FACTOR + elapsed * SPEED_GROWTH_PER_SECOND)
                .coerceAtMost(MAX_SPEED_FACTOR),
        )
    }
}
