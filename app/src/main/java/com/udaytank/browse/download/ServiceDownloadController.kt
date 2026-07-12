package com.udaytank.browse.download

import android.content.Context
import com.udaytank.browse.DownloadController
import com.udaytank.browse.DownloadWhen

/** Production [DownloadController]: forwards every action to [DownloadService] via intents. */
class ServiceDownloadController(private val context: Context) : DownloadController {

    override fun startDownload(id: Long) {
        context.startForegroundService(DownloadService.intentFor(context, DownloadService.ACTION_START, id))
    }

    override fun schedule(id: Long, constraint: DownloadWhen) {
        // Scheduling lands with DownloadScheduler (plan Task 6); until then, start immediately.
        startDownload(id)
    }

    override fun pause(id: Long) {
        context.startService(DownloadService.intentFor(context, DownloadService.ACTION_PAUSE, id))
    }

    override fun resume(id: Long) {
        context.startForegroundService(DownloadService.intentFor(context, DownloadService.ACTION_RESUME, id))
    }

    override fun cancel(id: Long) {
        context.startService(DownloadService.intentFor(context, DownloadService.ACTION_CANCEL, id))
    }
}
