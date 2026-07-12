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

        when (intent?.action) {
            ACTION_START, ACTION_RESUME -> handleStart(id)
            ACTION_PAUSE -> engine.pause(id)
            ACTION_CANCEL -> {
                engine.cancel(id)
                scope.launch { dao.setState(id, "CANCELLED") }
            }
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

            val dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            dir?.mkdirs()
            val destFile = File(dir, entry.fileName)

            dao.setState(id, "RUNNING")
            engine.start(
                id = id,
                url = entry.url,
                destFile = destFile,
                userAgent = null,
                priorEtag = entry.etag,
                priorTotal = entry.totalBytes,
                priorSegmentState = entry.segmentState,
                listener = listener,
            )
        }
    }

    private fun stopIfIdle() {
        if (activeCount.get() <= 0) stopSelf()
    }

    private val listener = object : DownloadEngine.Listener {
        override fun onProgress(id: Long, downloaded: Long, total: Long, segmentState: String) {
            scope.launch { dao.setProgress(id, downloaded, total, segmentState) }
            updateProgressNotification(id, downloaded, total)
        }

        override fun onStateChanged(id: Long, state: String, error: String?) {
            scope.launch { dao.setState(id, state, error) }
            when (state) {
                "DONE", "FAILED", "CANCELLED" -> {
                    finalNotification(id, state)
                    activeCount.decrementAndGet()
                    stopIfIdle()
                }
                "PAUSED" -> {
                    pausedNotification(id)
                    activeCount.decrementAndGet()
                    stopIfIdle()
                }
            }
        }

        override fun onFileInfo(id: Long, fileName: String, total: Long, etag: String?, segments: Int) {
            scope.launch {
                val entry = dao.getById(id)
                val dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                dao.setFileInfo(
                    id = id,
                    fileName = fileName,
                    filePath = entry?.filePath ?: File(dir, fileName).absolutePath,
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

        fun intentFor(context: Context, action: String, id: Long): Intent =
            Intent(context, DownloadService::class.java).apply {
                this.action = action
                putExtra(EXTRA_ID, id)
            }
    }
}
