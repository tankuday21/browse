package com.udaytank.browse.download

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class DownloadEngineTest {

    // 3MB: large enough that DownloadPlanner.segmentCount() plans 3 segments (thresholds:
    // <2MB -> 1, <20MB -> 3, else 6), so the "segmented" tests actually exercise multiple segments.
    private val payload = ByteArray(3_000_000) { (it % 251).toByte() }
    private val server = MockWebServer()
    private var destFile: File? = null

    private val rangePattern: Pattern = Pattern.compile("bytes=(\\d+)-(\\d+)")

    @After
    fun tearDown() {
        server.shutdown()
        destFile?.let { if (it.exists()) it.delete() }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun sha256(file: File): String = sha256(file.readBytes())

    /**
     * Dispatcher that honors Range requests with 206 responses. Records every served range.
     * When [throttle] is set, response bodies are throttled so transfers take long enough
     * (well past one 500ms progress tick) for pause()/cancel() to reliably land mid-transfer
     * instead of racing a near-instantaneous in-process loopback transfer to completion.
     */
    private fun rangeAwareDispatcher(servedRanges: MutableList<Pair<Long, Long>>, throttle: Boolean = false): Dispatcher {
        return object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val rangeHeader = request.getHeader("Range")
                if (rangeHeader != null) {
                    val matcher = rangePattern.matcher(rangeHeader)
                    if (matcher.find()) {
                        val start = matcher.group(1)!!.toLong()
                        val end = matcher.group(2)!!.toLong()
                        servedRanges.add(start to end)
                        val slice = payload.copyOfRange(start.toInt(), (end + 1).toInt())
                        val response = MockResponse()
                            .setResponseCode(206)
                            .setHeader("Content-Range", "bytes $start-$end/${payload.size}")
                            .setHeader("ETag", "v1")
                            .setHeader("Content-Length", slice.size.toString())
                            .setBody(Buffer().write(slice))
                        if (throttle && slice.size > 1) {
                            response.throttleBody(32 * 1024, 100, TimeUnit.MILLISECONDS)
                        }
                        return response
                    }
                }
                val response = MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Length", payload.size.toString())
                    .setHeader("ETag", "v1")
                    .setBody(Buffer().write(payload))
                if (throttle) response.throttleBody(32 * 1024, 100, TimeUnit.MILLISECONDS)
                return response
            }
        }
    }

    private fun noRangeDispatcher(): Dispatcher {
        return object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                return MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Length", payload.size.toString())
                    .setHeader("ETag", "v1")
                    .setBody(Buffer().write(payload))
            }
        }
    }

    private class TestListener : DownloadEngine.Listener {
        val terminal = CompletableDeferred<String>()
        val firstProgress = CompletableDeferred<Unit>()
        var lastSegmentState: String = ""
        var lastError: String? = null

        override fun onProgress(id: Long, downloaded: Long, total: Long, segmentState: String) {
            lastSegmentState = segmentState
            if (!firstProgress.isCompleted) firstProgress.complete(Unit)
        }

        override fun onStateChanged(id: Long, state: String, error: String?) {
            if (state == "DONE" || state == "FAILED" || state == "CANCELLED" || state == "PAUSED") {
                lastError = error
                if (!terminal.isCompleted) terminal.complete(state)
            }
        }

        override fun onFileInfo(id: Long, fileName: String, total: Long, etag: String?, segments: Int) {
            // no-op for tests
        }
    }

    @Test
    fun `segmented download reassembles identical bytes`() = runBlocking {
        val servedRanges = CopyOnWriteArrayList<Pair<Long, Long>>()
        server.dispatcher = rangeAwareDispatcher(servedRanges)
        server.start()

        val dest = File.createTempFile("dl_segmented", ".bin")
        destFile = dest

        val scope = CoroutineScope(Dispatchers.IO)
        val engine = DownloadEngine(scope, Dispatchers.IO)
        val listener = TestListener()

        engine.start(
            id = 1L,
            url = server.url("/file").toString(),
            destFile = dest,
            userAgent = null,
            priorEtag = null,
            priorTotal = 0L,
            priorSegmentState = null,
            listener = listener,
        )

        val finalState = withTimeout(60_000) { listener.terminal.await() }
        assertEquals("DONE", finalState)
        assertTrue(servedRanges.size >= 2)
        assertEquals(sha256(payload), sha256(dest))
    }

    @Test
    fun `pause then start resumes without redownloading all`() = runBlocking {
        val servedRanges = CopyOnWriteArrayList<Pair<Long, Long>>()
        server.dispatcher = rangeAwareDispatcher(servedRanges, throttle = true)
        server.start()

        val dest = File.createTempFile("dl_resume", ".bin")
        destFile = dest

        val scope = CoroutineScope(Dispatchers.IO)
        val engine = DownloadEngine(scope, Dispatchers.IO)
        val listener1 = TestListener()

        engine.start(
            id = 2L,
            url = server.url("/file").toString(),
            destFile = dest,
            userAgent = null,
            priorEtag = null,
            priorTotal = 0L,
            priorSegmentState = null,
            listener = listener1,
        )

        withTimeout(60_000) { listener1.firstProgress.await() }
        engine.pause(2L)
        val pausedState = withTimeout(60_000) { listener1.terminal.await() }
        assertEquals("PAUSED", pausedState)

        val segmentStateAtPause = listener1.lastSegmentState
        assertTrue(segmentStateAtPause.isNotEmpty())

        val rangesBeforeResume = servedRanges.size

        val listener2 = TestListener()
        engine.start(
            id = 2L,
            url = server.url("/file").toString(),
            destFile = dest,
            userAgent = null,
            priorEtag = "v1",
            priorTotal = payload.size.toLong(),
            priorSegmentState = segmentStateAtPause,
            listener = listener2,
        )

        val finalState = withTimeout(60_000) { listener2.terminal.await() }
        assertEquals("DONE", finalState)
        assertEquals(sha256(payload), sha256(dest))

        // At least one resume-phase range request should start at an offset > 0,
        // proving bytes already downloaded before pause were skipped.
        val resumeRanges = servedRanges.drop(rangesBeforeResume)
        assertTrue(resumeRanges.isNotEmpty())
        assertTrue(resumeRanges.any { it.first > 0L })
    }

    @Test
    fun `server without ranges falls back to single stream and completes`() = runBlocking {
        server.dispatcher = noRangeDispatcher()
        server.start()

        val dest = File.createTempFile("dl_norange", ".bin")
        destFile = dest

        val scope = CoroutineScope(Dispatchers.IO)
        val engine = DownloadEngine(scope, Dispatchers.IO)
        val listener = TestListener()

        engine.start(
            id = 3L,
            url = server.url("/file").toString(),
            destFile = dest,
            userAgent = null,
            priorEtag = null,
            priorTotal = 0L,
            priorSegmentState = null,
            listener = listener,
        )

        val finalState = withTimeout(60_000) { listener.terminal.await() }
        assertEquals("DONE", finalState)
        assertEquals(sha256(payload), sha256(dest))
    }

    @Test
    fun `cancel deletes the file`() = runBlocking {
        val servedRanges = CopyOnWriteArrayList<Pair<Long, Long>>()
        server.dispatcher = rangeAwareDispatcher(servedRanges, throttle = true)
        server.start()

        val dest = File.createTempFile("dl_cancel", ".bin")
        destFile = dest

        val scope = CoroutineScope(Dispatchers.IO)
        val engine = DownloadEngine(scope, Dispatchers.IO)
        val listener = TestListener()

        engine.start(
            id = 4L,
            url = server.url("/file").toString(),
            destFile = dest,
            userAgent = null,
            priorEtag = null,
            priorTotal = 0L,
            priorSegmentState = null,
            listener = listener,
        )

        withTimeout(60_000) { listener.firstProgress.await() }
        engine.cancel(4L)
        val finalState = withTimeout(60_000) { listener.terminal.await() }
        assertEquals("CANCELLED", finalState)
        assertFalse(dest.exists())
    }

    @Test
    fun `restart from scratch truncates preexisting junk file`() = runBlocking {
        server.dispatcher = noRangeDispatcher()
        server.start()

        val dest = File.createTempFile("dl_restart", ".bin")
        destFile = dest
        // Pre-fill with 5MB of junk, larger than the 3MB payload, so stale trailing bytes
        // would survive past the payload's end unless the engine truncates before writing.
        dest.writeBytes(ByteArray(5_000_000) { 0x7f })

        val scope = CoroutineScope(Dispatchers.IO)
        val engine = DownloadEngine(scope, Dispatchers.IO)
        val listener = TestListener()

        engine.start(
            id = 5L,
            url = server.url("/file").toString(),
            destFile = dest,
            userAgent = null,
            priorEtag = "stale",
            priorTotal = 5_000_000L,
            priorSegmentState = "0:5000000",
            listener = listener,
        )

        val finalState = withTimeout(60_000) { listener.terminal.await() }
        assertEquals("DONE", finalState)
        assertEquals(payload.size.toLong(), dest.length())
        assertEquals(sha256(payload), sha256(dest))
    }

    @Test
    fun `cancel of resumed generation deletes file and reports cancelled`() = runBlocking {
        val servedRanges = CopyOnWriteArrayList<Pair<Long, Long>>()
        server.dispatcher = rangeAwareDispatcher(servedRanges, throttle = true)
        server.start()

        val dest = File.createTempFile("dl_resume_cancel", ".bin")
        destFile = dest

        val scope = CoroutineScope(Dispatchers.IO)
        val engine = DownloadEngine(scope, Dispatchers.IO)

        // Simple per-generation listener that records every terminal state it ever sees.
        class RecordingListener : DownloadEngine.Listener {
            val terminal = CompletableDeferred<String>()
            val firstProgress = CompletableDeferred<Unit>()
            var lastSegmentState: String = ""
            val statesSeen = CopyOnWriteArrayList<String>()

            override fun onProgress(id: Long, downloaded: Long, total: Long, segmentState: String) {
                lastSegmentState = segmentState
                if (!firstProgress.isCompleted) firstProgress.complete(Unit)
            }

            override fun onStateChanged(id: Long, state: String, error: String?) {
                if (state == "DONE" || state == "FAILED" || state == "CANCELLED" || state == "PAUSED") {
                    statesSeen.add(state)
                    if (!terminal.isCompleted) terminal.complete(state)
                }
            }

            override fun onFileInfo(id: Long, fileName: String, total: Long, etag: String?, segments: Int) {
                // no-op for tests
            }
        }

        val listener1 = RecordingListener()
        engine.start(
            id = 6L,
            url = server.url("/file").toString(),
            destFile = dest,
            userAgent = null,
            priorEtag = null,
            priorTotal = 0L,
            priorSegmentState = null,
            listener = listener1,
        )

        withTimeout(60_000) { listener1.firstProgress.await() }
        engine.pause(6L)
        val pausedState = withTimeout(60_000) { listener1.terminal.await() }
        assertEquals("PAUSED", pausedState)
        // Exactly one terminal callback for this generation, and it must be PAUSED - never
        // CANCELLED, since the generation-1 stop reason must not be hijacked by a later cancel().
        assertEquals(listOf("PAUSED"), listener1.statesSeen)

        val segmentStateAtPause = listener1.lastSegmentState
        assertTrue(segmentStateAtPause.isNotEmpty())

        val listener2 = RecordingListener()
        engine.start(
            id = 6L,
            url = server.url("/file").toString(),
            destFile = dest,
            userAgent = null,
            priorEtag = "v1",
            priorTotal = payload.size.toLong(),
            priorSegmentState = segmentStateAtPause,
            listener = listener2,
        )

        withTimeout(60_000) { listener2.firstProgress.await() }
        engine.cancel(6L)
        val finalState = withTimeout(60_000) { listener2.terminal.await() }
        assertEquals("CANCELLED", finalState)
        assertFalse(dest.exists())

        // Exactly one terminal per generation; generation 1 never saw CANCELLED, generation 2
        // never saw PAUSED.
        assertEquals(listOf("PAUSED"), listener1.statesSeen)
        assertEquals(listOf("CANCELLED"), listener2.statesSeen)
    }
}
