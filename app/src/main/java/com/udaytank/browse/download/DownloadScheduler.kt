package com.udaytank.browse.download

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.udaytank.browse.BrowseApplication
import com.udaytank.browse.DownloadWhen
import java.util.concurrent.TimeUnit

/**
 * Enqueues a [StartDownloadWorker] via WorkManager so a WIFI/1-hour-delayed download (or a retry
 * once connectivity returns) survives process death between now and whenever its constraint is met.
 */
object DownloadScheduler {

    private const val ID_KEY = "id"

    fun enqueue(context: Context, downloadId: Long, constraint: DownloadWhen) {
        val builder = OneTimeWorkRequestBuilder<StartDownloadWorker>()
            .setInputData(workDataOf(ID_KEY to downloadId))
        when (constraint) {
            DownloadWhen.WIFI ->
                builder.setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build())
            DownloadWhen.LATER_1H ->
                builder.setInitialDelay(1, TimeUnit.HOURS)
            DownloadWhen.NOW -> Unit
        }
        WorkManager.getInstance(context).enqueueUniqueWork(
            "download-$downloadId",
            ExistingWorkPolicy.REPLACE,
            builder.build(),
        )
    }

    /**
     * Auto-resumes a download that FAILed with a transient (network) error, once connectivity
     * returns. [attempts] (the count already recorded on the row, post-increment) drives a
     * 30s/60s/120s backoff ladder so a flaky-but-still-connected network can't spin this in a
     * tight loop - CONNECTED alone is satisfied immediately whenever the device is online.
     */
    fun enqueueRetryOnReconnect(context: Context, downloadId: Long, attempts: Int) {
        val request = OneTimeWorkRequestBuilder<StartDownloadWorker>()
            .setInputData(workDataOf(ID_KEY to downloadId))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInitialDelay(30L * (1L shl attempts.coerceAtMost(3)), TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "download-$downloadId",
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    class StartDownloadWorker(
        context: Context,
        params: WorkerParameters,
    ) : CoroutineWorker(context, params) {

        override suspend fun doWork(): Result {
            val id = inputData.getLong(ID_KEY, -1L)
            if (id == -1L) return Result.failure()

            val dao = (applicationContext as BrowseApplication).database.downloadDao()
            if (dao.getById(id) == null) return Result.success() // row deleted/cancelled - nothing to start
            dao.setState(id, "PENDING")
            applicationContext.startForegroundService(
                DownloadService.intentFor(applicationContext, DownloadService.ACTION_START, id),
            )
            return Result.success()
        }
    }
}
