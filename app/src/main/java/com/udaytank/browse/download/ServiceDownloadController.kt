package com.udaytank.browse.download

import android.content.Context
import androidx.work.WorkManager
import com.udaytank.browse.DownloadController
import com.udaytank.browse.DownloadWhen

/** Production [DownloadController]: forwards every action to [DownloadService] via intents. */
class ServiceDownloadController(private val context: Context) : DownloadController {

    override fun startDownload(id: Long) {
        context.startForegroundService(DownloadService.intentFor(context, DownloadService.ACTION_START, id))
    }

    override fun schedule(id: Long, constraint: DownloadWhen) {
        DownloadScheduler.enqueue(context, id, constraint)
    }

    override fun pause(id: Long) {
        context.startService(DownloadService.intentFor(context, DownloadService.ACTION_PAUSE, id))
    }

    override fun resume(id: Long) {
        context.startForegroundService(DownloadService.intentFor(context, DownloadService.ACTION_RESUME, id))
    }

    override fun cancel(id: Long) {
        // Cancel any WorkManager-scheduled start/retry for this id first - otherwise a
        // Wi-Fi/delayed schedule or a retry-on-reconnect can fire after the row (and its file)
        // are gone, resurrecting a "cancelled" download.
        WorkManager.getInstance(context).cancelUniqueWork("download-$id")
        context.startService(DownloadService.intentFor(context, DownloadService.ACTION_CANCEL, id))
    }
}
