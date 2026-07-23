package com.udaytank.browse.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SleepTimerTest {

    @Test
    fun `minute presets produce now plus that many minutes`() {
        assertEquals(15 * 60_000L, SleepTimer.deadline(SleepPreset.M15, 0L))
        assertEquals(1_000_000L + 30 * 60_000L, SleepTimer.deadline(SleepPreset.M30, 1_000_000L))
    }

    @Test
    fun `off and end-of-track have no wall-clock deadline`() {
        assertNull(SleepTimer.deadline(SleepPreset.OFF, 5_000L))
        assertNull(SleepTimer.deadline(SleepPreset.END_OF_TRACK, 5_000L))
    }

    @Test
    fun `remaining seconds counts down and floors at zero`() {
        val deadline = 60_000L
        assertEquals(60, SleepTimer.remainingSeconds(deadline, 0L))
        assertEquals(30, SleepTimer.remainingSeconds(deadline, 30_000L))
        assertEquals(1, SleepTimer.remainingSeconds(deadline, 59_500L)) // rounds up while any time left
        assertEquals(0, SleepTimer.remainingSeconds(deadline, 60_000L))
        assertEquals(0, SleepTimer.remainingSeconds(deadline, 90_000L)) // past deadline
    }

    @Test
    fun `formats mm ss`() {
        assertEquals("0:09", SleepTimer.formatRemaining(9))
        assertEquals("1:00", SleepTimer.formatRemaining(60))
        assertEquals("14:05", SleepTimer.formatRemaining(14 * 60 + 5))
        assertEquals("60:00", SleepTimer.formatRemaining(3600))
        assertEquals("0:00", SleepTimer.formatRemaining(-5)) // defensive
    }
}
