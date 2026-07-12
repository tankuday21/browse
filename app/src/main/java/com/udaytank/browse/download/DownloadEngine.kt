package com.udaytank.browse.download

import com.udaytank.browse.browser.DownloadPlanner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLongArray

/**
 * Core segmented download engine. One [Generation] (job + its own stop reason) tracked per
 * download [id] so pause/cancel/double-start can be guarded and cooperative-cancelled without
 * a later generation's stop reason ever leaking into an earlier one still unwinding.
 */
class DownloadEngine(
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    interface Listener {
        fun onProgress(id: Long, downloaded: Long, total: Long, segmentState: String)
        fun onStateChanged(id: Long, state: String, error: String? = null)
        fun onFileInfo(id: Long, fileName: String, total: Long, etag: String?, segments: Int)
    }

    private enum class StopReason { PAUSE, CANCEL }

    /**
     * One instance per start() call. The download coroutine captures its OWN [Generation]
     * reference directly (passed as a parameter) rather than looking itself up by [id] in a
     * shared map, so a later generation (e.g. resume after pause) can never have its stop reason
     * clobbered by, or clobber, an earlier generation that is still unwinding after cancellation.
     */
    private class Generation {
        @Volatile var job: Job? = null
        @Volatile var stopReason: StopReason? = null
    }

    private val generations = ConcurrentHashMap<Long, Generation>()

    fun isActive(id: Long): Boolean = generations[id]?.job?.isActive == true

    fun start(
        id: Long,
        url: String,
        destFile: File,
        userAgent: String?,
        priorEtag: String?,
        priorTotal: Long,
        priorSegmentState: String?,
        listener: Listener,
    ) {
        if (isActive(id)) return // double-start guard, checked against the CURRENT generation

        val gen = Generation()
        val job = scope.launch(io) {
            runDownload(id, url, destFile, userAgent, priorEtag, priorTotal, priorSegmentState, listener, gen)
        }
        gen.job = job
        generations[id] = gen
    }

    fun pause(id: Long) {
        val gen = generations[id] ?: return
        gen.stopReason = StopReason.PAUSE
        gen.job?.cancel()
    }

    fun cancel(id: Long) {
        val gen = generations[id] ?: return
        gen.stopReason = StopReason.CANCEL
        gen.job?.cancel()
    }

    private suspend fun runDownload(
        id: Long,
        url: String,
        destFile: File,
        userAgent: String?,
        priorEtag: String?,
        priorTotal: Long,
        priorSegmentState: String?,
        listener: Listener,
        gen: Generation,
    ) {
        try {
            listener.onStateChanged(id, "RUNNING")

            // Probe with GET Range: bytes=0-0. 206 => ranges supported; 200 => not.
            val probe = probe(url, userAgent)
            val serverTotal = probe.total
            val etag = probe.etag
            val acceptRanges = probe.acceptsRanges

            val canResume = priorSegmentState != null &&
                DownloadPlanner.canResume(priorEtag, etag, priorTotal, serverTotal)

            val segmentCount = DownloadPlanner.segmentCount(serverTotal, acceptRanges)
            val planned = DownloadPlanner.plan(serverTotal.coerceAtLeast(0), segmentCount)
            val segments = if (canResume) DownloadPlanner.decodeState(priorSegmentState, planned) else planned

            listener.onFileInfo(id, destFile.name, serverTotal, etag, segments.size)

            // Starting from scratch (no valid resume, unknown total, or nothing was actually
            // downloaded yet) must truncate any pre-existing file first, otherwise stale trailing
            // bytes from a previous unrelated download can survive past the new file's end.
            val startsFromScratch = !canResume || serverTotal <= 0 || segments.all { it.downloaded == 0L }
            if (startsFromScratch) {
                RandomAccessFile(destFile, "rw").use { it.setLength(0) }
            }

            if (serverTotal > 0) {
                // Idempotent: setLength to the same size preserves already-written bytes on resume.
                RandomAccessFile(destFile, "rw").use { it.setLength(serverTotal) }
            } else if (!destFile.exists()) {
                destFile.createNewFile()
            }

            val progress = AtomicLongArray(segments.size)
            for (i in segments.indices) progress.set(i, segments[i].downloaded)
            val segmentLengths = LongArray(segments.size) { segments[it].endInclusive - segments[it].start + 1 }

            val tickerJob = scope.launch(io) {
                while (isActive) {
                    delay(500)
                    emitProgress(id, segments, progress, segmentLengths, serverTotal, listener)
                }
            }
            try {
                withContext(io) {
                    val deferreds = segments.mapIndexed { i, seg ->
                        async { downloadSegment(url, userAgent, destFile, seg, i, progress) }
                    }
                    deferreds.awaitAll()
                }
            } finally {
                // NonCancellable: this coroutine may already be cancelling (pause/cancel path),
                // in which case a plain suspend call here would throw immediately instead of
                // actually waiting for the ticker to stop. Must fully join BEFORE any terminal
                // onStateChanged below, or a lagging onProgress can land after it.
                withContext(NonCancellable) {
                    tickerJob.cancelAndJoin()
                }
            }

            // Final snapshot so listeners observe 100% before the terminal DONE callback.
            emitProgress(id, segments, progress, segmentLengths, serverTotal, listener)
            listener.onStateChanged(id, "DONE")
        } catch (e: CancellationException) {
            when (gen.stopReason) {
                StopReason.CANCEL -> {
                    if (destFile.exists()) destFile.delete()
                    listener.onStateChanged(id, "CANCELLED")
                }
                else -> listener.onStateChanged(id, "PAUSED")
            }
        } catch (e: IOException) {
            listener.onStateChanged(id, "FAILED", "${e.javaClass.simpleName}: ${e.message}")
        } catch (e: Exception) {
            listener.onStateChanged(id, "FAILED", e.message ?: e.toString())
        } finally {
            // Two-arg remove: only clear the map entry if it still points at *this* generation, so
            // a fresh start() that already replaced it (e.g. immediate resume after pause) is safe.
            generations.remove(id, gen)
        }
    }

    private fun emitProgress(
        id: Long,
        segments: List<DownloadPlanner.Segment>,
        progress: AtomicLongArray,
        segmentLengths: LongArray,
        total: Long,
        listener: Listener,
    ) {
        var downloadedSum = 0L
        val withProgress = segments.mapIndexed { i, seg ->
            val d = progress.get(i).coerceIn(0, segmentLengths[i].coerceAtLeast(0))
            downloadedSum += d
            seg.copy(downloaded = d)
        }
        listener.onProgress(id, downloadedSum, total, DownloadPlanner.encodeState(withProgress))
    }

    private data class ProbeResult(val total: Long, val etag: String?, val acceptsRanges: Boolean)

    private fun probe(url: String, userAgent: String?): ProbeResult {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.setRequestProperty("Range", "bytes=0-0")
            if (userAgent != null) connection.setRequestProperty("User-Agent", userAgent)
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.connect()

            val etag = connection.getHeaderField("ETag")
            return if (connection.responseCode == HttpURLConnection.HTTP_PARTIAL) {
                // For a 206 probe, the connection's own contentLength is the 1-byte probe body,
                // NOT the resource size - never fall back to it. Total is either parsed from a
                // well-formed "bytes 0-0/<digits>" Content-Range, or unknown (-1) when the
                // suffix is "*" or the header is missing/unparseable.
                val total = parseTotalFromContentRange(connection.getHeaderField("Content-Range")) ?: -1L
                ProbeResult(total = total, etag = etag, acceptsRanges = true)
            } else {
                ProbeResult(total = connection.contentLengthLong, etag = etag, acceptsRanges = false)
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun parseTotalFromContentRange(contentRange: String?): Long? {
        if (contentRange == null) return null
        val slashIndex = contentRange.lastIndexOf('/')
        if (slashIndex < 0) return null
        return contentRange.substring(slashIndex + 1).toLongOrNull()
    }

    private suspend fun downloadSegment(
        url: String,
        userAgent: String?,
        destFile: File,
        segment: DownloadPlanner.Segment,
        index: Int,
        progress: AtomicLongArray,
    ) {
        val openEnded = segment.endInclusive < segment.start
        val segmentLength = segment.endInclusive - segment.start + 1
        if (!openEnded && segment.downloaded >= segmentLength) return // already complete from prior run

        val rangeStart = segment.start + segment.downloaded
        if (!openEnded && rangeStart > segment.endInclusive) return

        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            val rangeHeader = if (openEnded) "bytes=$rangeStart-" else "bytes=$rangeStart-${segment.endInclusive}"
            connection.setRequestProperty("Range", rangeHeader)
            if (userAgent != null) connection.setRequestProperty("User-Agent", userAgent)
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.connect()

            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_PARTIAL && code != HttpURLConnection.HTTP_OK) {
                throw IOException("Unexpected response code $code for segment $index")
            }

            RandomAccessFile(destFile, "rw").use { raf ->
                raf.seek(segment.start + segment.downloaded)
                connection.inputStream.use { input ->
                    val buffer = ByteArray(64 * 1024)
                    var written = segment.downloaded
                    while (coroutineContext.isActive) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        raf.write(buffer, 0, read)
                        written += read
                        progress.set(index, written)
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }
}
