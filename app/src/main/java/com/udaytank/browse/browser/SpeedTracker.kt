package com.udaytank.browse.browser

class SpeedTracker(private val windowMs: Long = 3_000) {
    private data class Sample(val timeMs: Long, val totalBytes: Long)

    private val samples = ArrayDeque<Sample>()
    private val speedHistory = mutableListOf<Long>()

    fun sample(nowMs: Long, totalDownloadedBytes: Long) {
        samples.addLast(Sample(nowMs, totalDownloadedBytes))
        pruneStale(nowMs)

        val speed = bytesPerSecond(nowMs)
        speedHistory.add(speed)
        if (speedHistory.size > 30) {
            speedHistory.removeAt(0)
        }
    }

    fun bytesPerSecond(nowMs: Long): Long {
        pruneStale(nowMs)
        if (samples.size < 2) return 0

        val earliest = samples.first()
        val latest = samples.last()
        val elapsed = latest.timeMs - earliest.timeMs
        val bytes = latest.totalBytes - earliest.totalBytes
        return bytes * 1000 / maxOf(1, elapsed)
    }

    fun history(): List<Long> = speedHistory.toList()

    private fun pruneStale(nowMs: Long) {
        while (samples.isNotEmpty() && nowMs - samples.first().timeMs > windowMs) {
            samples.removeFirst()
        }
    }
}
