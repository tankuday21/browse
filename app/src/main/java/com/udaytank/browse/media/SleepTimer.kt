package com.udaytank.browse.media

/** Sleep-timer presets for the Andromeda Player (v6.3). [minutes] null = stop at end of track. */
enum class SleepPreset(val minutes: Int?) {
    OFF(0),
    M15(15),
    M30(30),
    M45(45),
    M60(60),
    END_OF_TRACK(null),
}

/** Live sleep-timer state the player exposes to its UI. */
data class SleepState(val preset: SleepPreset = SleepPreset.OFF, val remainingSeconds: Int = 0)

/**
 * Pure sleep-timer math (v6.3): deadlines, countdown, and mm:ss formatting. No Android types, so
 * it unit-tests on the JVM. Time is caller-supplied (elapsedRealtime ms) to stay deterministic.
 */
object SleepTimer {

    /** Absolute deadline for a minute preset, or null for OFF / END_OF_TRACK (no wall clock). */
    fun deadline(preset: SleepPreset, nowMs: Long): Long? {
        val minutes = preset.minutes ?: return null // END_OF_TRACK
        if (minutes <= 0) return null // OFF
        return nowMs + minutes * 60_000L
    }

    /** Whole seconds remaining to [deadlineMs] from [nowMs], floored at 0. */
    fun remainingSeconds(deadlineMs: Long, nowMs: Long): Int {
        val remaining = deadlineMs - nowMs
        if (remaining <= 0L) return 0
        return ((remaining + 999L) / 1000L).toInt() // round up so "0:01" shows until truly elapsed
    }

    /** "mm:ss" for a remaining-seconds count (e.g. 9 → "0:09", 3600 → "60:00"). */
    fun formatRemaining(seconds: Int): String {
        val safe = seconds.coerceAtLeast(0)
        val m = safe / 60
        val s = safe % 60
        return "%d:%02d".format(m, s)
    }
}
