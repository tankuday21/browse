package com.udaytank.browse.media

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.udaytank.browse.MainActivity

/**
 * Foreground (mediaPlayback) service that keeps the process alive while a single opted-in tab
 * keeps playing after the app leaves the foreground, and now carries a real [BrowserMediaSession]
 * so the OS shows lock-screen transport controls (Previous / Play-Pause / Next) bound to a
 * [Notification.MediaStyle] notification — the same platform-MediaSession pattern ReadAloudService
 * uses, so no new dependency is needed.
 *
 * Wiring back into the WebView keeps the deliberately simple static-bridge design this service
 * started with: [MainActivity] sets the [controller]/[onNext]/[onPrevious]/[onStopped] callbacks
 * before calling [start], and the transport callbacks invoke them on the main thread. This only
 * works because a single MainActivity drives a single foreground media tab at a time — acceptable
 * for this per-site opt-in feature. Every static bridge is nulled on destroy so the closed-over
 * WebViewHolder/tab can't leak past the service's lifecycle.
 *
 * It does NOT prevent an OEM battery manager or the OS (under memory pressure) from killing the
 * process anyway — an accepted, documented limit of this experimental feature.
 */
class MediaHoldService : Service() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var session: BrowserMediaSession? = null

    private var title = ""
    private var playing = true
    private var positionMs = -1
    private var durationMs = -1

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        session = BrowserMediaSession(
            context = this,
            // Transport callbacks hop to the main thread (like the original controller bridge)
            // because they end up calling WebView.evaluateJavascript, which is main-thread only.
            onPlayPause = { mainHandler.post { controller?.invoke() } },
            onNext = { mainHandler.post { onNext?.invoke() } },
            onPrevious = { mainHandler.post { onPrevious?.invoke() } },
            onStop = { stop(this) },
            onSeek = { pos -> mainHandler.post { onSeek?.invoke(pos) } },
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE -> {
                mainHandler.post { controller?.invoke() }
                return START_STICKY
            }
            ACTION_NEXT -> {
                mainHandler.post { onNext?.invoke() }
                return START_STICKY
            }
            ACTION_PREVIOUS -> {
                mainHandler.post { onPrevious?.invoke() }
                return START_STICKY
            }
            ACTION_UPDATE -> {
                title = intent.getStringExtra(EXTRA_TITLE) ?: title
                playing = intent.getBooleanExtra(EXTRA_PLAYING, playing)
                positionMs = intent.getIntExtra(EXTRA_POSITION, positionMs)
                durationMs = intent.getIntExtra(EXTRA_DURATION, durationMs)
                session?.update(title, playing, positionMs, durationMs)
                getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification())
                return START_STICKY
            }
            ACTION_STOP -> {
                onStopped?.invoke()
                clearBridges()
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                title = intent?.getStringExtra(EXTRA_TITLE)?.takeIf { it.isNotBlank() }
                    ?: intent?.getStringExtra(EXTRA_HOST) ?: ""
                playing = true
                session?.update(title, playing, positionMs, durationMs)
                startForegroundCompat(buildNotification())
                return START_STICKY
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Best-effort: if the system tears the service down rather than going via ACTION_STOP,
        // still clear the keep-alive flag so the tab isn't stuck exempt from pausing forever.
        onStopped?.invoke()
        session?.release()
        session = null
        clearBridges()
    }

    /** Release every static callback so this instance (and its captured WebViewHolder) can't leak. */
    private fun clearBridges() {
        controller = null
        onNext = null
        onPrevious = null
        onStopped = null
        onSeek = null
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

    /**
     * Media-style notification bound to the session token: Previous / Play-Pause / Next plus a
     * Stop action, with prev/play-pause/next shown in the compact (lock-screen) view.
     */
    private fun buildNotification(): Notification {
        val playPause = if (playing) {
            action(android.R.drawable.ic_media_pause, "Pause", ACTION_TOGGLE)
        } else {
            action(android.R.drawable.ic_media_play, "Play", ACTION_TOGGLE)
        }
        val builder = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title.ifBlank { "Playing in background" })
            .setContentText("Playing in background")
            .setOngoing(true)
            .setContentIntent(openAppIntent())
            .addAction(action(android.R.drawable.ic_media_previous, "Previous", ACTION_PREVIOUS))
            .addAction(playPause)
            .addAction(action(android.R.drawable.ic_media_next, "Next", ACTION_NEXT))
            .addAction(action(android.R.drawable.ic_delete, "Stop", ACTION_STOP))
        val style = Notification.MediaStyle()
            .setShowActionsInCompactView(0, 1, 2)
        session?.token?.let { style.setMediaSession(it) }
        builder.setStyle(style)
        return builder.build()
    }

    private fun action(icon: Int, title: String, intentAction: String): Notification.Action =
        Notification.Action.Builder(
            Icon.createWithResource(this, icon),
            title,
            pendingIntentFor(intentAction),
        ).build()

    private fun pendingIntentFor(action: String): PendingIntent {
        val intent = Intent(this, MediaHoldService::class.java).setAction(action)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getService(this, action.hashCode(), intent, flags)
    }

    private fun openAppIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(this, 0, intent, flags)
    }

    companion object {
        private const val ACTION_TOGGLE = "com.udaytank.browse.media.action.TOGGLE"
        private const val ACTION_NEXT = "com.udaytank.browse.media.action.NEXT"
        private const val ACTION_PREVIOUS = "com.udaytank.browse.media.action.PREVIOUS"
        private const val ACTION_UPDATE = "com.udaytank.browse.media.action.UPDATE"
        private const val ACTION_STOP = "com.udaytank.browse.media.action.STOP"
        private const val EXTRA_HOST = "com.udaytank.browse.media.extra.HOST"
        private const val EXTRA_TITLE = "com.udaytank.browse.media.extra.TITLE"
        private const val EXTRA_PLAYING = "com.udaytank.browse.media.extra.PLAYING"
        private const val EXTRA_POSITION = "com.udaytank.browse.media.extra.POSITION"
        private const val EXTRA_DURATION = "com.udaytank.browse.media.extra.DURATION"
        private const val CHANNEL_ID = "media"
        private const val NOTIFICATION_ID = 0x6D656469 // arbitrary, stable, distinct from other services

        /**
         * Invoked on the main thread for the lock-screen / notification Play-Pause. Set by
         * MainActivity immediately before [start]; a static single-instance bridge (see class doc).
         */
        var controller: (() -> Unit)? = null

        /** Lock-screen / notification Next. Set by MainActivity, invoked on the main thread. */
        var onNext: (() -> Unit)? = null

        /** Lock-screen / notification Previous. Set by MainActivity, invoked on the main thread. */
        var onPrevious: (() -> Unit)? = null

        /** Invoked when the service stops (Stop action, session Stop, or the OS tearing it down). */
        var onStopped: (() -> Unit)? = null

        /** Lock-screen scrubber drag → seek the page's media (ms). Invoked on the main thread. */
        var onSeek: ((positionMs: Long) -> Unit)? = null

        fun start(context: Context, tabId: Long, host: String, title: String) {
            val intent = Intent(context, MediaHoldService::class.java)
                .putExtra(EXTRA_HOST, host)
                .putExtra(EXTRA_TITLE, title)
            ContextCompat.startForegroundService(context, intent)
        }

        /** Pushes fresh title/playing/timeline state (from the page's JS monitor) to the session + notification. */
        fun updateState(context: Context, title: String, playing: Boolean, positionMs: Int, durationMs: Int) {
            val intent = Intent(context, MediaHoldService::class.java)
                .setAction(ACTION_UPDATE)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_PLAYING, playing)
                .putExtra(EXTRA_POSITION, positionMs)
                .putExtra(EXTRA_DURATION, durationMs)
            context.startService(intent)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, MediaHoldService::class.java).setAction(ACTION_STOP))
        }
    }
}
