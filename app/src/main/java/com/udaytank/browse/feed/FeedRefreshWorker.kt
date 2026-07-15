package com.udaytank.browse.feed

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.udaytank.browse.BrowseApplication
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

/**
 * Periodic background refresh of the home feed cache (v3.2). No-op when the feed is turned off.
 * Only runs on a connected network; failures retry. Never touches incognito (the feed cache is a
 * normal-mode concept — incognito renders nothing).
 */
class FeedRefreshWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? BrowseApplication ?: return Result.success()
        return try {
            if (app.settingsRepository.showFeed.first()) {
                app.feedRepository.refresh()
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val UNIQUE_NAME = "feed_refresh"

        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<FeedRefreshWorker>(6, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                // UPDATE so a future cadence/constraint change propagates to existing installs.
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }
    }
}
