package com.udaytank.browse.media

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

/**
 * Foreground (mediaPlayback) service that keeps the process alive - and gives the user a visible,
 * dismissable notification with Play/Pause and Stop controls - while a single opted-in tab keeps
 * playing audio/video after the app leaves the foreground.
 *
 * This is deliberately minimal: no MediaSession, no lock-screen artwork, no queue. The notification
 * exists purely so background playback is never silent/invisible to the user, and so they always
 * have a one-tap way to stop it. It does NOT prevent the OS or an OEM battery manager from killing
 * the process anyway - that's an accepted, documented limitation of this experimental feature.
 *
 * Wiring back into the WebView is intentionally simple rather than "correct": the service holds a
 * static [controller] callback that [MainActivity] sets before calling [start], and invokes it on
 * the main thread when the user taps the notification's Play/Pause action. This only works because
 * there is a single MainActivity instance driving a single foreground tab at a time - acceptable
 * for an experimental, per-site opt-in feature, but not a pattern to copy for anything richer
 * (a real MediaSession + MediaController would be the correct approach for that).
 */
class MediaHoldService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE -> {
                Handler(Looper.getMainLooper()).post { controller?.invoke() }
                return START_STICKY
            }
            ACTION_STOP -> {
                onStopped?.invoke()
                controller = null
                onStopped = null
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                val host = intent?.getStringExtra(EXTRA_HOST) ?: ""
                startForegroundCompat(buildNotification(host))
                return START_STICKY
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Best-effort: if the service is torn down by the system rather than via ACTION_STOP,
        // still clear the keep-alive flag so the tab isn't stuck exempt from pausing forever.
        onStopped?.invoke()
        // Release both static callbacks so this service instance (and the WebViewHolder/tabId
        // it closed over) isn't leaked past its own lifecycle.
        controller = null
        onStopped = null
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(CHANNEL_ID, "Media playback", NotificationManager.IMPORTANCE_LOW)
        manager.createNotificationChannel(channel)
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(host: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(host)
            .setContentText("Playing in background")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, "Play/Pause", pendingIntentFor(ACTION_TOGGLE))
            .addAction(0, "Stop", pendingIntentFor(ACTION_STOP))
            .build()

    private fun pendingIntentFor(action: String): PendingIntent {
        val intent = Intent(this, MediaHoldService::class.java).setAction(action)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getService(this, action.hashCode(), intent, flags)
    }

    companion object {
        private const val ACTION_TOGGLE = "com.udaytank.browse.media.action.TOGGLE"
        private const val ACTION_STOP = "com.udaytank.browse.media.action.STOP"
        private const val EXTRA_HOST = "com.udaytank.browse.media.extra.HOST"
        private const val CHANNEL_ID = "media"
        private const val NOTIFICATION_ID = 0x6D656469 // arbitrary, stable, distinct from other services

        /**
         * Invoked on the main thread when the user taps Play/Pause in the notification. Set by
         * MainActivity immediately before calling [start]; documented as a static single-instance
         * bridge rather than a proper callback registration - see class doc.
         */
        var controller: (() -> Unit)? = null

        /** Invoked when the service stops (either via the Stop action or the OS tearing it down). */
        var onStopped: (() -> Unit)? = null

        fun start(context: Context, tabId: Long, host: String) {
            val intent = Intent(context, MediaHoldService::class.java).putExtra(EXTRA_HOST, host)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, MediaHoldService::class.java).setAction(ACTION_STOP))
        }
    }
}
