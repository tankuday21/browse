package com.udaytank.browse

import com.udaytank.browse.browser.FileChooserCoordinator
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The exactly-once contract (v4.8): a WebView file-chooser callback must be invoked exactly once
 * on every path — picked, cancelled, superseded, launch failure — and never twice.
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
        coordinator.begin(recorder.callback) { true }
        coordinator.finish(listOf("file1"))
        assertEquals(listOf<List<String>?>(listOf("file1")), recorder.received)
    }

    @Test
    fun `cancel resolves exactly once with null`() {
        val coordinator = FileChooserCoordinator<List<String>>()
        val recorder = Recorder()
        coordinator.begin(recorder.callback) { true }
        coordinator.finish(null)
        assertEquals(listOf<List<String>?>(null), recorder.received)
    }

    @Test
    fun `second begin supersedes the first with null, first result goes to second`() {
        val coordinator = FileChooserCoordinator<List<String>>()
        val first = Recorder()
        val second = Recorder()
        coordinator.begin(first.callback) { true }
        coordinator.begin(second.callback) { true }
        coordinator.finish(listOf("picked"))
        assertEquals(listOf<List<String>?>(null), first.received)
        assertEquals(listOf<List<String>?>(listOf("picked")), second.received)
    }

    @Test
    fun `launch failure resolves null exactly once and a late finish is dropped`() {
        val coordinator = FileChooserCoordinator<List<String>>()
        val recorder = Recorder()
        coordinator.begin(recorder.callback) { false }
        coordinator.finish(listOf("stale"))
        assertEquals(listOf<List<String>?>(null), recorder.received)
    }

    @Test
    fun `finish with nothing pending is a safe no-op`() {
        val coordinator = FileChooserCoordinator<List<String>>()
        coordinator.finish(listOf("orphan")) // must not throw
    }

    @Test
    fun `double finish resolves only once`() {
        val coordinator = FileChooserCoordinator<List<String>>()
        val recorder = Recorder()
        coordinator.begin(recorder.callback) { true }
        coordinator.finish(listOf("a"))
        coordinator.finish(listOf("b"))
        assertEquals(listOf<List<String>?>(listOf("a")), recorder.received)
    }

    @Test
    fun `reentrant begin from inside finish does not double-resolve`() {
        val coordinator = FileChooserCoordinator<List<String>>()
        val second = Recorder()
        var firstCount = 0
        coordinator.begin(
            callback = {
                firstCount++
                // A page re-opening the chooser from inside the resolution (onReceiveValue can
                // run page JS synchronously) must not corrupt the slot.
                coordinator.begin(second.callback) { true }
            },
        ) { true }
        coordinator.finish(listOf("first"))
        coordinator.finish(listOf("second"))
        assertEquals(1, firstCount)
        assertEquals(listOf<List<String>?>(listOf("second")), second.received)
    }
}
