package com.udaytank.browse.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Environment
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.udaytank.browse.BrowseApplication
import com.udaytank.browse.data.DownloadDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/**
 * Foreground (dataSync) service driving one [DownloadEngine] for all in-flight downloads.
 * Every DB write happens through [dao] from [scope] (its own SupervisorJob+IO scope, independent
 * of the service lifecycle-bound coroutine helpers) so a listener callback can never be dropped
 * mid-write by the service being torn down.
 */
class DownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val engine = DownloadEngine(scope)
    private val dao: DownloadDao by lazy { (application as BrowseApplication).database.downloadDao() }

    /** Count of downloads this service instance currently considers "in flight". */
    private val activeCount = AtomicInteger(0)

    /**
     * Guards first-start filename resolution + destination-file creation as one atomic unit.
     * Without this, two concurrent first-starts for the same fileName can both see
     * UniqueName.resolve's exists() check return false (the engine only creates the file after
     * a network probe, leaving a full-RTT TOCTOU window), resolve to the same dest file, and
     * end up with two DB rows writing the same path. Creating the file inside the lock closes
     * the window: the second resolver's exists() check will see the first's claimed file.
     */
    private val nameLock = Any()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): Nothing? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Must promote to foreground within 5s of onStartCommand, regardless of the action -
        // do this unconditionally before any dao/engine work (which is async).
        startForegroundCompat()

        val id = intent?.getLongExtra(EXTRA_ID, -1L) ?: -1L
        if (id == -1L) return START_NOT_STICKY

        // Every branch schedules a stopIfIdle() check after its own work, so an action for an
        // id the engine doesn't (or no longer) track - e.g. PAUSE/CANCEL after the download
        // already finished - can't leave the service stranded in the foreground forever.
        // activeCount is only ever incremented synchronously (inside handleStart, before any
        // suspension), so by the time these launches actually run the count already reflects
        // this call's effect - a stray stopIfIdle can't race ahead of engine.start().
        when (intent?.action) {
            ACTION_START, ACTION_RESUME -> {
                handleStart(id)
                scope.launch { stopIfIdle() }
            }
            ACTION_PAUSE -> {
                engine.pause(id)
                scope.launch { stopIfIdle() }
            }
            ACTION_CANCEL -> {
                engine.cancel(id)
                scope.launch {
                    dao.setState(id, "CANCELLED")
                    stopIfIdle()
                }
            }
            else -> scope.launch { stopIfIdle() }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private fun handleStart(id: Long) {
        if (engine.isActive(id)) return // already running/starting - avoid double-counting

        activeCount.incrementAndGet()
        scope.launch {
            val entry = dao.getById(id)
            if (entry == null) {
                activeCount.decrementAndGet()
                stopIfIdle()
                return@launch
            }
            // v6.8 start-path race — fast path: a CANCEL that already landed on a still-queued
            // download must not be overwritten by a start. Bail before creating any file, so no
            // orphan is left behind (engine.cancel is a no-op until engine.start registers the id,
            // and nothing else deletes a file handleStart created).
            if (entry.state == "CANCELLED") {
                activeCount.decrementAndGet()
                stopIfIdle()
                return@launch
            }

            val dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            dir?.mkdirs()
            // RESUME/retry of a download that already resolved+persisted a filePath reuses it
            // verbatim, so it keeps writing the same file. Only a first start resolves a fresh,
            // collision-free name from entry.fileName and persists it - never re-derive from the
            // raw fileName on subsequent starts, or two same-named downloads could still collide.
            val createdThisRun = entry.filePath == null
            val dest = entry.filePath?.let { File(it) } ?: synchronized(nameLock) {
                val name = UniqueName.resolve(entry.fileName) { candidate -> File(dir, candidate).exists() }
                File(dir, name).apply { parentFile?.mkdirs(); createNewFile() }
            }
            if (createdThisRun) {
                dao.setFileInfo(id, dest.name, dest.absolutePath, entry.mimeType, entry.etag, entry.segments)
            }

            // v6.8: atomically claim RUNNING only if a CANCEL didn't win the race during the file
            // setup above (and the row isn't already DONE/gone). 0 rows updated → abort and delete
            // the file we just created, matching the engine's own cancel-deletes-file behaviour.
            if (dao.markRunningIfLive(id) == 0) {
                if (createdThisRun) runCatching { dest.delete() }
                activeCount.decrementAndGet()
                stopIfIdle()
                return@launch
            }
            engine.start(
                id = id,
                url = entry.url,
                destFile = dest,
                userAgent = null,
                priorEtag = entry.etag,
                priorTotal = entry.totalBytes,
                priorSegmentState = entry.segmentState,
                listener = listener,
            )
        }
    }

    /** Idempotent: safe to call speculatively from any action's completion path. */
    private fun stopIfIdle() {
        if (activeCount.get() <= 0) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private val listener = object : DownloadEngine.Listener {
        override fun onProgress(id: Long, downloaded: Long, total: Long, segmentState: String) {
            scope.launch { dao.setProgress(id, downloaded, total, segmentState) }
            updateProgressNotification(id, downloaded, total)
        }

        override fun onStateChanged(id: Long, state: String, error: String?) {
            // Notification is fire-and-forget and can stay synchronous. The activeCount
            // decrement + stopIfIdle for terminal states must NOT run until the dao.setState
            // write below has actually landed - otherwise stopIfIdle() can stopSelf() (and
            // cancel this service's scope) while that write is still in flight, dropping it.
            when (state) {
                "DONE", "FAILED", "CANCELLED" -> finalNotification(id, state)
                "PAUSED" -> pausedNotification(id)
            }
            scope.launch {
                if (state == "RUNNING") {
                    // v6.8: claim RUNNING atomically here too. In the narrow window where a CANCEL
                    // landed between handleStart's claim and this engine-emitted RUNNING (engine.cancel
                    // no-op'd because the generation wasn't registered yet), this must NOT overwrite
                    // CANCELLED. markRunningIfLive returns 0 then; cancel the now-registered generation
                    // so its own cancel path deletes the partial file, and let that CANCELLED emit flow.
                    if (dao.markRunningIfLive(id) == 0) {
                        engine.cancel(id)
                        return@launch
                    }
                } else {
                    dao.setState(id, state, error)
                }
                when (state) {
                    "DONE", "FAILED", "CANCELLED", "PAUSED" -> {
                        activeCount.decrementAndGet()
                        stopIfIdle()
                    }
                }
                when (state) {
                    "DONE" -> dao.resetAttempts(id)
                    "FAILED" -> {
                        dao.incrementAttempts(id)
                        val entry = dao.getById(id)
                        val attempts = entry?.attempts ?: Int.MAX_VALUE
                        val transient = error != null && TRANSIENT_ERROR_PREFIXES.any { error.startsWith(it) }
                        if (attempts < 3 && transient) {
                            DownloadScheduler.enqueueRetryOnReconnect(applicationContext, id, attempts)
                        }
                    }
                }
            }
        }

        override fun onFileInfo(id: Long, fileName: String, total: Long, etag: String?, segments: Int) {
            scope.launch {
                val entry = dao.getById(id)
                val existingPath = entry?.filePath
                // Never rebuild the path (or overwrite the persisted fileName) from the raw
                // fileName reported here once a filePath is already persisted - handleStart
                // already resolved a collision-free name for this download; re-deriving from
                // fileName again would risk re-colliding with another same-named download.
                val filePath = existingPath ?: run {
                    val dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                    val name = UniqueName.resolve(fileName) { candidate -> File(dir, candidate).exists() }
                    File(dir, name).absolutePath
                }
                dao.setFileInfo(
                    id = id,
                    fileName = if (existingPath != null) entry.fileName else fileName,
                    filePath = filePath,
                    mimeType = entry?.mimeType,
                    etag = etag,
                    segments = segments,
                )
                // total isn't a setFileInfo column; fold it in via setProgress, preserving
                // whatever's already been downloaded (segmentState included).
                dao.setProgress(id, entry?.downloadedBytes ?: 0L, total, entry?.segmentState)
            }
        }
    }

    // ---- notifications ----

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(CHANNEL_ID, "Downloads", NotificationManager.IMPORTANCE_LOW)
        manager.createNotificationChannel(channel)
    }

    private fun startForegroundCompat() {
        val notification = summaryNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(SUMMARY_NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(SUMMARY_NOTIFICATION_ID, notification)
        }
    }

    private fun summaryNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Downloads")
            .setOngoing(true)
            .build()

    private fun updateProgressNotification(id: Long, downloaded: Long, total: Long) {
        val percent = if (total > 0) ((downloaded * 100) / total).toInt() else 0
        val speedText = if (total > 0) "$percent%" else "Downloading…"
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Downloading")
            .setContentText(speedText)
            .setProgress(100, percent, total <= 0)
            .setOngoing(true)
            .addAction(0, "Pause", pendingIntentFor(ACTION_PAUSE, id))
            .addAction(0, "Cancel", pendingIntentFor(ACTION_CANCEL, id))
            .build()
        notify(id, notification)
    }

    private fun finalNotification(id: Long, state: String) {
        val title = when (state) {
            "DONE" -> "Download complete"
            "CANCELLED" -> "Download cancelled"
            else -> "Download failed"
        }
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(title)
            .setOngoing(false)
            .setAutoCancel(true)
            .build()
        notify(id, notification)
    }

    private fun pausedNotification(id: Long) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Download paused")
            .setOngoing(false)
            .addAction(0, "Resume", pendingIntentFor(ACTION_RESUME, id))
            .addAction(0, "Cancel", pendingIntentFor(ACTION_CANCEL, id))
            .build()
        notify(id, notification)
    }

    private fun notify(id: Long, notification: Notification) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(id.toInt(), notification)
    }

    private fun pendingIntentFor(action: String, id: Long): PendingIntent {
        val intent = intentFor(this, action, id)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getService(this, (action + id).hashCode(), intent, flags)
    }

    companion object {
        const val ACTION_START = "com.udaytank.browse.download.action.START"
        const val ACTION_PAUSE = "com.udaytank.browse.download.action.PAUSE"
        const val ACTION_RESUME = "com.udaytank.browse.download.action.RESUME"
        const val ACTION_CANCEL = "com.udaytank.browse.download.action.CANCEL"
        const val EXTRA_ID = "com.udaytank.browse.download.extra.ID"
        private const val CHANNEL_ID = "downloads"
        private const val SUMMARY_NOTIFICATION_ID = Int.MAX_VALUE

        /**
         * Error simpleName prefixes (see DownloadEngine's "SimpleName: message" FAILED payload)
         * that indicate a transient network problem worth auto-retrying on reconnect. Anything
         * else (404, disk-full IOException, etc.) is treated as permanent - retrying it in a
         * loop every time CONNECTED is satisfied would never succeed.
         */
        private val TRANSIENT_ERROR_PREFIXES = listOf(
            "UnknownHostException",
            "SocketTimeoutException",
            "ConnectException",
            "SocketException",
            "NoRouteToHostException",
        )

        fun intentFor(context: Context, action: String, id: Long): Intent =
            Intent(context, DownloadService::class.java).apply {
                this.action = action
                putExtra(EXTRA_ID, id)
            }
    }
}
