package com.udaytank.browse.browser.adblock

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.udaytank.browse.BrowseApplication
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Refreshes filter-list snapshots into filesDir/adblock/<id>.txt, where the loader in
 * [BrowseApplication] prefers them over the bundled assets.
 *
 * Every download goes to a `.tmp` sibling first and only replaces the real file after a
 * sanity check (non-trivial size, plausible first line), so a failed or truncated download
 * always leaves the previous snapshot — or the asset fallback — intact.
 *
 * Scheduling: a 7-day periodic unique work ("filterlist-update", KEEP) enqueued at app start,
 * constrained to unmetered network + battery-not-low; plus [updateNow] for the Settings button.
 */
class FilterListUpdater(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val app = applicationContext as BrowseApplication
        val enabledIds = app.settingsRepository.adBlockLists.first()
        val targets = FilterLists.ADS.filter { it.id in enabledIds } + FilterLists.ANNOYANCE
        val dir = File(app.filesDir, "adblock").apply { mkdirs() }

        var successes = 0
        for (def in targets) {
            val ok = runCatching { download(def.updateUrl, File(dir, "${def.id}.txt")) }
                .getOrDefault(false)
            if (ok) successes++
        }

        // Even a partial refresh is worth loading — each list file is replaced atomically,
        // so whatever is on disk now is coherent per list.
        if (successes > 0) app.reloadAdblock()

        when {
            successes == targets.size -> {
                app.settingsRepository.setAdBlockLastUpdated(System.currentTimeMillis())
                Result.success()
            }
            runAttemptCount < MAX_RETRIES -> Result.retry()
            else -> Result.failure()
        }
    }

    /** Downloads [url] to `dest.tmp`, sanity-checks it, then atomically renames over [dest]. */
    private fun download(url: String, dest: File): Boolean {
        val tmp = File(dest.path + ".tmp")
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 30_000
            connection.readTimeout = 60_000
            connection.instanceFollowRedirects = true
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return false
            connection.inputStream.use { input ->
                tmp.outputStream().use { output -> input.copyTo(output) }
            }
        } finally {
            connection.disconnect()
        }
        if (!looksLikeFilterList(tmp)) {
            tmp.delete()
            return false
        }
        // rename(2) is atomic within a filesystem; filesDir is one filesystem. If a stale
        // dest somehow blocks the rename, replace it explicitly.
        if (!tmp.renameTo(dest)) {
            dest.delete()
            if (!tmp.renameTo(dest)) {
                tmp.delete()
                return false
            }
        }
        return true
    }

    /** A real list is non-trivially sized and starts with an ABP header, comment, or rule. */
    private fun looksLikeFilterList(file: File): Boolean {
        if (file.length() < MIN_LIST_BYTES) return false
        val firstLine = file.bufferedReader().use { reader ->
            generateSequence { reader.readLine() }.firstOrNull { it.isNotBlank() }
        } ?: return false
        // Reject obvious HTML error pages served with 200 (captive portals, CDN errors).
        return !firstLine.trimStart().startsWith("<")
    }

    companion object {
        private const val MIN_LIST_BYTES = 10L * 1024
        private const val MAX_RETRIES = 3

        private const val PERIODIC_WORK_NAME = "filterlist-update"
        private const val NOW_WORK_NAME = "filterlist-update-now"

        /** Weekly background refresh; KEEP preserves the existing cadence across app starts. */
        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<FilterListUpdater>(7, TimeUnit.DAYS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.UNMETERED)
                        .setRequiresBatteryNotLow(true)
                        .build(),
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        /** The Settings "Update filter lists" button: one-shot, any network, replaces a pending one. */
        fun updateNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<FilterListUpdater>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                NOW_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }
    }
}
