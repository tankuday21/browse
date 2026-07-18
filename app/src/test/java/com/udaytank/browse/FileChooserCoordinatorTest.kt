package com.udaytank.browse

import com.udaytank.browse.browser.FileChooserCoordinator
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The exactly-once contract (v4.8) + generation matching (v5.3): a WebView file-chooser callback
 * must be invoked exactly once on every path — picked, cancelled, superseded, launch failure —
 * never twice, and a superseded launch's late result must not consume the new request.
 */
class FileChooserCoordinatorTest {

    /** Records every value a callback receives so tests can assert exact invocation counts. */
    private class Recorder {
        val received = mutableListOf<List<String>?>()
        val callback: (List<String>?) -> Unit = { received.add(it) }
    }

    @Test
    fun `happy path resolves exactly once with the result`() {
        val coordinator = FileChooserCoordinator<List<String>>()
        val recorder = Recorder()
        var gen = 0
        coordinator.begin(recorder.callback) { gen = it; true }
        coordinator.finish(gen, listOf("file1"))
        assertEquals(listOf<List<String>?>(listOf("file1")), recorder.received)
    }

    @Test
    fun `cancel resolves exactly once with null`() {
        val coordinator = FileChooserCoordinator<List<String>>()
        val recorder = Recorder()
        var gen = 0
        coordinator.begin(recorder.callback) { gen = it; true }
        coordinator.finish(gen, null)
        assertEquals(listOf<List<String>?>(null), recorder.received)
    }

    @Test
    fun `second begin supersedes the first with null, the result goes to the second`() {
        val coordinator = FileChooserCoordinator<List<String>>()
        val first = Recorder()
        val second = Recorder()
        var gen2 = 0
        coordinator.begin(first.callback) { true }
        coordinator.begin(second.callback) { gen2 = it; true }
        coordinator.finish(gen2, listOf("picked"))
        assertEquals(listOf<List<String>?>(null), first.received)
        assertEquals(listOf<List<String>?>(listOf("picked")), second.received)
    }

    @Test
    fun `a stale generation's late result is dropped and the current request still resolves`() {
        // The v5.3 race: launch A superseded by launch B; A's result arrives late. It must
        // neither consume B's callback (B's photo would be silently dropped) nor double-fire A.
        val coordinator = FileChooserCoordinator<List<String>>()
        val first = Recorder()
        val second = Recorder()
        var gen1 = 0
        var gen2 = 0
        coordinator.begin(first.callback) { gen1 = it; true }
        coordinator.begin(second.callback) { gen2 = it; true }
        coordinator.finish(gen1, listOf("stale-result"))
        assertEquals(listOf<List<String>?>(null), first.received) // only the supersede null
        assertEquals(emptyList<List<String>?>(), second.received) // untouched — still pending
        coordinator.finish(gen2, listOf("real-photo"))
        assertEquals(listOf<List<String>?>(listOf("real-photo")), second.received)
    }

    @Test
    fun `launch failure resolves null exactly once and a late finish is dropped`() {
        val coordinator = FileChooserCoordinator<List<String>>()
        val recorder = Recorder()
        var gen = 0
        coordinator.begin(recorder.callback) { gen = it; false }
        coordinator.finish(gen, listOf("stale"))
        assertEquals(listOf<List<String>?>(null), recorder.received)
    }

    @Test
    fun `finish with nothing pending is a safe no-op`() {
        val coordinator = FileChooserCoordinator<List<String>>()
        coordinator.finish(1, listOf("orphan")) // must not throw
    }

    @Test
    fun `double finish resolves only once`() {
        val coordinator = FileChooserCoordinator<List<String>>()
        val recorder = Recorder()
        var gen = 0
        coordinator.begin(recorder.callback) { gen = it; true }
        coordinator.finish(gen, listOf("a"))
        coordinator.finish(gen, listOf("b"))
        assertEquals(listOf<List<String>?>(listOf("a")), recorder.received)
    }

    @Test
    fun `reentrant begin from inside a superseded callback is not orphaned`() {
        val coordinator = FileChooserCoordinator<List<String>>()
        val inner = Recorder()
        val outer = Recorder()
        var firstCount = 0
        var outerGen = 0
        coordinator.begin(
            callback = {
                firstCount++
                // First chooser superseded; its resolution reentrantly opens another one.
                coordinator.begin(inner.callback) { true }
            },
        ) { true }
        coordinator.begin(outer.callback) { outerGen = it; true } // supersedes: first → null, inner drained → null
        coordinator.finish(outerGen, listOf("picked"))
        assertEquals(1, firstCount)
        assertEquals(listOf<List<String>?>(null), inner.received) // resolved, never orphaned
        assertEquals(listOf<List<String>?>(listOf("picked")), outer.received)
    }

    @Test
    fun `reentrant begin from inside finish does not double-resolve`() {
        val coordinator = FileChooserCoordinator<List<String>>()
        val second = Recorder()
        var firstCount = 0
        var gen1 = 0
        var gen2 = 0
        coordinator.begin(
            callback = {
                firstCount++
                // A page re-opening the chooser from inside the resolution (onReceiveValue can
                // run page JS synchronously) must not corrupt the slot.
                coordinator.begin(second.callback) { gen2 = it; true }
            },
        ) { gen1 = it; true }
        coordinator.finish(gen1, listOf("first"))
        coordinator.finish(gen2, listOf("second"))
        assertEquals(1, firstCount)
        assertEquals(listOf<List<String>?>(listOf("second")), second.received)
    }
}
