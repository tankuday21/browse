package com.udaytank.browse.browser

object DownloadPlanner {
    data class Segment(val index: Int, val start: Long, val endInclusive: Long, val downloaded: Long = 0)

    /** <2MB or unknown size or no range support -> 1; <20MB -> 3; else -> 6 */
    fun segmentCount(totalBytes: Long, acceptRanges: Boolean): Int {
        if (totalBytes <= 0 || !acceptRanges || totalBytes < 2_000_000) return 1
        if (totalBytes < 20_000_000) return 3
        return 6
    }

    fun plan(totalBytes: Long, count: Int): List<Segment> {
        val effective = minOf(count.coerceAtLeast(1).toLong(), totalBytes.coerceAtLeast(1)).toInt()
        val lastByte = totalBytes - 1
        val baseSize = totalBytes / effective
        val segments = mutableListOf<Segment>()
        var start = 0L
        for (i in 0 until effective) {
            val end = if (i == effective - 1) lastByte else start + baseSize - 1
            segments.add(Segment(index = i, start = start, endInclusive = end))
            start = end + 1
        }
        return segments
    }

    /** Resume is valid only when etag matches (or both null) and total matches. */
    fun canResume(storedEtag: String?, serverEtag: String?, storedTotal: Long, serverTotal: Long): Boolean {
        return storedEtag == serverEtag && storedTotal == serverTotal
    }

    fun encodeState(segments: List<Segment>): String {
        return segments.joinToString(",") { "${it.index}:${it.downloaded}" }
    }

    fun decodeState(encoded: String?, planned: List<Segment>): List<Segment> {
        if (encoded == null) return planned
        val parts = encoded.split(",").filter { it.isNotEmpty() }
        if (parts.size != planned.size) return planned

        val downloadedByIndex = HashMap<Int, Long>()
        for (part in parts) {
            val pieces = part.split(":")
            if (pieces.size != 2) return planned
            val index = pieces[0].toIntOrNull() ?: return planned
            val downloaded = pieces[1].toLongOrNull() ?: return planned
            downloadedByIndex[index] = downloaded
        }

        return planned.map { segment ->
            val raw = downloadedByIndex[segment.index] ?: return planned
            val segmentLength = segment.endInclusive - segment.start + 1
            val clamped = raw.coerceIn(0, segmentLength)
            segment.copy(downloaded = clamped)
        }
    }
}
