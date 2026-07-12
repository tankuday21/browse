package com.udaytank.browse

/** Records calls instead of touching a real DownloadService; used by VM unit tests. */
class RecordingDownloadController : DownloadController {
    val started = mutableListOf<Long>()
    val scheduled = mutableListOf<Pair<Long, DownloadWhen>>()
    val paused = mutableListOf<Long>()
    val resumed = mutableListOf<Long>()
    val cancelled = mutableListOf<Long>()

    override fun startDownload(id: Long) {
        started += id
    }

    override fun schedule(id: Long, constraint: DownloadWhen) {
        scheduled += id to constraint
    }

    override fun pause(id: Long) {
        paused += id
    }

    override fun resume(id: Long) {
        resumed += id
    }

    override fun cancel(id: Long) {
        cancelled += id
    }
}
