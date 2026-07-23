package com.udaytank.browse.browser

import kotlin.math.sqrt

/**
 * Pure, dependency-free shake recognizer (v6.2) fed raw accelerometer samples. Kept free of
 * Android types so it unit-tests on the JVM. Used to arm — never to directly perform — the
 * Black Hole panic wipe.
 *
 * A "jolt" is a sample whose total g-force exceeds [gThreshold]. A shake is [requiredJolts] jolts
 * within [windowMs]; after firing, recognition is suppressed for [cooldownMs] so one vigorous
 * shake yields exactly one event.
 */
class ShakeDetector(
    private val gThreshold: Float = 2.7f,
    private val requiredJolts: Int = 3,
    private val windowMs: Long = 1000L,
    private val cooldownMs: Long = 2000L,
    private val minJoltGapMs: Long = 120L,
) {
    private val jolts = ArrayDeque<Long>()
    private var lastJoltMs = Long.MIN_VALUE
    private var cooldownUntil = Long.MIN_VALUE

    /**
     * Feed one accelerometer reading (m/s², e.g. SensorEvent.values) at [timeMs]. Returns true
     * exactly once when this sample completes a shake.
     */
    fun onSample(x: Float, y: Float, z: Float, timeMs: Long): Boolean {
        if (timeMs < cooldownUntil) return false

        val gForce = sqrt(x * x + y * y + z * z) / EARTH_GRAVITY
        if (gForce <= gThreshold) return false

        // A shake is DISTINCT jolts, not one sustained impact: ignore over-threshold samples that
        // arrive within minJoltGapMs of the last recorded jolt, so setting the phone down hard
        // (a burst of consecutive high-g samples from a single contact) can't count as three.
        if (lastJoltMs != Long.MIN_VALUE && timeMs - lastJoltMs < minJoltGapMs) return false
        lastJoltMs = timeMs

        // Record this jolt, drop any older than the sliding window.
        jolts.addLast(timeMs)
        while (jolts.isNotEmpty() && timeMs - jolts.first() > windowMs) {
            jolts.removeFirst()
        }

        if (jolts.size >= requiredJolts) {
            jolts.clear()
            cooldownUntil = timeMs + cooldownMs
            return true
        }
        return false
    }

    /** Clears accumulated state; call when (re)starting listening so stale jolts don't carry over. */
    fun reset() {
        jolts.clear()
        lastJoltMs = Long.MIN_VALUE
        cooldownUntil = Long.MIN_VALUE
    }

    private companion object {
        const val EARTH_GRAVITY = 9.80665f
    }
}
