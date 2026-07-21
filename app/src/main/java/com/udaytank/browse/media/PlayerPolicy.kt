package com.udaytank.browse.media

import com.udaytank.browse.data.DownloadEntry

/** Resume-position rules for the Andromeda Player (v6.0). Pure, JVM-tested. */
object PlayerProgressPolicy {

    /** At/after this fraction of a clip we treat it as "finished" — restart, don't resume. */
    const val FINISHED_FRACTION = 0.95

    /**
     * Resume only when we have a real saved position that isn't essentially the end: duration
     * must be known (> 0), the saved position must be positive, and it must sit before
     * [FINISHED_FRACTION] of the clip. Otherwise start from the beginning.
     */
    fun shouldResume(savedMs: Long, durationMs: Long): Boolean =
        durationMs > 0 && savedMs > 0 && savedMs < durationMs * FINISHED_FRACTION

    /** A clip is finished (its saved row should be cleared) once playback reaches the tail. */
    fun isFinished(positionMs: Long, durationMs: Long): Boolean =
        durationMs > 0 && positionMs >= durationMs * FINISHED_FRACTION
}

/** Builds the auto-play queue for the player (v6.0). Pure, JVM-tested. */
object PlayerQueuePolicy {

    /** "audio/mpeg" -> "audio"; null/blank/malformed -> null. */
    fun topLevelType(mime: String?): String? =
        mime?.substringBefore('/')?.trim()?.takeIf { it.isNotEmpty() && it != mime.trim() }

    /**
     * The queue is the Orbit's other finished media of the SAME top-level kind (audio next to
     * audio, video next to video), in stable `createdAt` order (oldest → newest) so next/prev
     * move forward/back in time. Only rows with a real on-disk file are included. The current
     * item is always present (it seeds the queue). Returns a single-item list when nothing else
     * matches.
     */
    fun buildQueue(items: List<DownloadEntry>, currentId: Long, topLevel: String): List<DownloadEntry> =
        items
            .filter { it.state == "DONE" && it.filePath != null && topLevelType(it.mimeType) == topLevel }
            .sortedWith(compareBy({ it.createdAt }, { it.id }))
}
