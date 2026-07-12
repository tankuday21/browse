package com.udaytank.browse.reading

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.Toast
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.udaytank.browse.BrowseApplication
import com.udaytank.browse.MainActivity
import com.udaytank.browse.browser.HtmlText
import com.udaytank.browse.browser.TtsChunker
import com.udaytank.browse.browser.TtsQueue
import com.udaytank.browse.data.ReadingListDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * Foreground (mediaPlayback) service that reads articles aloud with the platform
 * [TextToSpeech] engine, exposed through a platform [MediaSession] so headset/
 * lock-screen transport controls work. Two sources:
 *
 *  - a saved reading-list row ([ACTION_PLAY_ARTICLE], loaded from Room + [ArticleStore]);
 *  - the live reader page, handed over via the static [pendingContent] holder +
 *    [ACTION_PLAY_PENDING] (same single-instance bridge as MediaHoldService's
 *    controller — and, per that class's leak lesson, nulled the moment it is read
 *    and again in [onDestroy]).
 *
 * Podcast mode ([ACTION_PLAY_ALL_UNREAD]) walks a [TtsQueue] over the unread list
 * (oldest first); each *finished* article is marked read, and the service stops
 * itself when the queue runs dry. Text is flattened by [HtmlText], split by
 * [TtsChunker], and spoken chunk-by-chunk: [UtteranceProgressListener.onDone]
 * (a binder thread) hops to the main thread and advances [chunkIndex]. Pause
 * simply stops the engine and keeps the index; resume re-speaks the current
 * chunk. TTS init failure is a toast + stopSelf, never a crash.
 */
class ReadAloudService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val dao: ReadingListDao by lazy { (application as BrowseApplication).database.readingListDao() }
    private val store: ArticleStore by lazy { ArticleStore(File(filesDir, "reading_list")) }

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var ttsFailed = false

    /** Play action deferred until the async TTS init lands (latest one wins). */
    private var pendingPlay: (() -> Unit)? = null

    private var chunks: List<String> = emptyList()
    private var chunkIndex = 0
    private var paused = false
    private var pausedByFocus = false
    private var speedIndex = 0

    /** Non-null while in podcast mode; null when reading a single article/page. */
    private var queue: TtsQueue? = null

    /** Reading-list row being spoken; null for a live page (no DB writes for those). */
    private var currentArticleId: Long? = null
    private var currentTitle = ""

    private var session: MediaSession? = null
    private var focusRequest: AudioFocusRequest? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        session = MediaSession(this, "ReadAloud").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() = resume()
                override fun onPause() = pause()
                override fun onSkipToNext() = skipToNextArticle()
                override fun onStop() = stopSelfCleanly()
            })
            isActive = true
        }
        // The constructor itself can throw when no engine is installed; treat that
        // exactly like an init failure (toast + stop) instead of crashing.
        tts = runCatching { TextToSpeech(this, ::onTtsInit) }
            .onFailure { ttsFailed = true }
            .getOrNull()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Promote to foreground within the 5s window regardless of the action.
        startForegroundCompat(buildNotification())
        if (ttsFailed) {
            failTts()
            return START_NOT_STICKY
        }
        when (intent?.action) {
            ACTION_PLAY_ARTICLE -> {
                val id = intent.getLongExtra(EXTRA_ARTICLE_ID, -1L)
                if (id == -1L) stopSelfCleanly() else whenTtsReady { playSavedArticle(id) }
            }
            ACTION_PLAY_PENDING -> {
                val content = pendingContent
                pendingContent = null // read-once: never outlive this command
                if (content == null) stopSelfCleanly()
                else whenTtsReady { playContent(content.first, content.second, articleId = null) }
            }
            ACTION_PLAY_ALL_UNREAD -> whenTtsReady { playAllUnread() }
            ACTION_TOGGLE_PAUSE -> if (paused) resume() else pause()
            ACTION_NEXT -> skipToNextArticle()
            ACTION_SPEED -> cycleSpeed()
            ACTION_STOP -> stopSelfCleanly()
            else -> stopSelfCleanly()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        abandonFocus()
        runCatching {
            tts?.stop()
            tts?.shutdown()
        }
        tts = null
        session?.release()
        session = null
        // Static holder + deferred callback nulled so nothing outlives the service
        // (MediaHoldService leak lesson).
        pendingPlay = null
        pendingContent = null
    }

    // ---- TTS ----

    private fun onTtsInit(status: Int) {
        mainHandler.post {
            if (status != TextToSpeech.SUCCESS) {
                ttsFailed = true
                failTts()
                return@post
            }
            // Best effort: fall back silently to the engine's own default voice when
            // the device locale isn't available - speaking anyway beats stopping.
            runCatching { tts?.language = Locale.getDefault() }
            tts?.setOnUtteranceProgressListener(progressListener)
            ttsReady = true
            pendingPlay?.invoke()
            pendingPlay = null
        }
    }

    private fun whenTtsReady(block: () -> Unit) {
        if (ttsReady) block() else pendingPlay = block
    }

    private fun failTts() {
        Toast.makeText(this, "Text-to-speech unavailable", Toast.LENGTH_SHORT).show()
        stopSelfCleanly()
    }

    private val progressListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) = Unit

        // Both arrive on a TTS binder thread - hop to main before touching state.
        // A pause() stops the engine (onStop, not onDone), so this only fires for
        // chunks that genuinely finished; the paused check covers the posting race.
        override fun onDone(utteranceId: String?) {
            mainHandler.post { if (!paused) advanceChunk() }
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) {
            mainHandler.post { if (!paused) advanceChunk() } // skip the bad chunk, keep reading
        }
    }

    private fun advanceChunk() {
        chunkIndex++
        if (chunkIndex < chunks.size) speakCurrentChunk() else onArticleFinished()
    }

    private fun speakCurrentChunk() {
        val engine = tts ?: return
        val chunk = chunks.getOrNull(chunkIndex) ?: run { onArticleFinished(); return }
        engine.setSpeechRate(SPEEDS[speedIndex])
        engine.speak(chunk, TextToSpeech.QUEUE_FLUSH, null, "andromeda-read-$chunkIndex")
    }

    // ---- playback sources ----

    private fun playContent(title: String, contentHtml: String, articleId: Long?) {
        currentTitle = title
        currentArticleId = articleId
        scope.launch {
            val prepared = withContext(Dispatchers.Default) {
                TtsChunker.chunk(HtmlText.strip(contentHtml), maxChunkLen())
            }
            if (prepared.isEmpty()) {
                onArticleFinished() // nothing speakable: podcast advances, single play stops
                return@launch
            }
            chunks = prepared
            chunkIndex = 0
            paused = false
            pausedByFocus = false
            requestFocus()
            updateSessionMetadata()
            updateSessionState(playing = true)
            updateNotification()
            speakCurrentChunk()
        }
    }

    private fun playSavedArticle(id: Long) {
        scope.launch {
            val entry = withContext(Dispatchers.IO) { dao.getById(id) }
            val html = entry?.filePath?.let { path -> withContext(Dispatchers.IO) { store.load(path) } }
            when {
                entry != null && html != null -> playContent(entry.title, html, id)
                // Online-only rows have nothing stored to read: skip them in podcast
                // mode (without marking read - they were never heard), stop otherwise.
                queue != null -> advanceQueue()
                else -> {
                    Toast.makeText(this@ReadAloudService, "No offline copy to read aloud", Toast.LENGTH_SHORT).show()
                    stopSelfCleanly()
                }
            }
        }
    }

    private fun playAllUnread() {
        scope.launch {
            val unread = withContext(Dispatchers.IO) { dao.getUnread() }
            if (unread.isEmpty()) {
                Toast.makeText(this@ReadAloudService, "No unread articles", Toast.LENGTH_SHORT).show()
                stopSelfCleanly()
                return@launch
            }
            queue = TtsQueue(unread.map { TtsQueue.Item(it.id, it.title) })
            playSavedArticle(queue!!.current!!.id)
        }
    }

    private fun onArticleFinished() {
        val finishedId = currentArticleId
        if (queue != null) {
            // Podcast mode: only an article actually read to the end counts as read.
            if (finishedId != null) {
                scope.launch(Dispatchers.IO) { dao.setReadAt(finishedId, System.currentTimeMillis()) }
            }
            advanceQueue()
        } else {
            stopSelfCleanly()
        }
    }

    /** Moves the podcast queue forward; queue dry means we are done. */
    private fun advanceQueue() {
        val nextItem = queue?.next()
        if (nextItem == null) stopSelfCleanly() else playSavedArticle(nextItem.id)
    }

    // ---- transport controls ----

    private fun pause() {
        if (paused) return
        paused = true
        tts?.stop() // keeps chunkIndex: resume re-speaks the current chunk
        updateSessionState(playing = false)
        updateNotification()
    }

    private fun resume() {
        if (!paused) return
        paused = false
        pausedByFocus = false
        requestFocus()
        updateSessionState(playing = true)
        updateNotification()
        speakCurrentChunk()
    }

    /** Podcast-only: jump to the next article without marking the current one read. */
    private fun skipToNextArticle() {
        if (queue == null) return
        tts?.stop()
        advanceQueue()
    }

    private fun cycleSpeed() {
        speedIndex = (speedIndex + 1) % SPEEDS.size
        updateSessionState(playing = !paused)
        updateNotification()
        // Re-speak the current chunk at the new rate (QUEUE_FLUSH replaces the old one).
        if (!paused) speakCurrentChunk()
    }

    private fun stopSelfCleanly() {
        paused = true // suppress in-flight onDone advances
        tts?.stop()
        abandonFocus()
        session?.isActive = false
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // ---- audio focus ----

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        mainHandler.post {
            when (change) {
                AudioManager.AUDIOFOCUS_LOSS,
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK,
                -> if (!paused) {
                    pause()
                    pausedByFocus = true
                }
                AudioManager.AUDIOFOCUS_GAIN -> if (pausedByFocus) resume()
            }
        }
    }

    private fun requestFocus() {
        if (focusRequest != null) return
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setOnAudioFocusChangeListener(focusListener, mainHandler)
            .build()
        // Result deliberately ignored: spoken text is the user's explicit request,
        // so speak even if another app declines to duck.
        getSystemService(AudioManager::class.java).requestAudioFocus(request)
        focusRequest = request
    }

    private fun abandonFocus() {
        focusRequest?.let { getSystemService(AudioManager::class.java).abandonAudioFocusRequest(it) }
        focusRequest = null
    }

    // ---- MediaSession state ----

    private fun updateSessionMetadata() {
        session?.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, currentTitle.ifBlank { "Read aloud" })
                .putString(MediaMetadata.METADATA_KEY_ARTIST, "Andromeda is reading")
                .build()
        )
    }

    private fun updateSessionState(playing: Boolean) {
        var actions = PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
            PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_STOP
        if (queue != null) actions = actions or PlaybackState.ACTION_SKIP_TO_NEXT
        val state = if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED
        // Spoken audio has no meaningful timeline: position stays 0, no scrubbing.
        session?.setPlaybackState(
            PlaybackState.Builder()
                .setActions(actions)
                .setState(state, 0L, if (playing) SPEEDS[speedIndex] else 0f)
                .build()
        )
    }

    // ---- notification ----

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(CHANNEL_ID, "Read aloud", NotificationManager.IMPORTANCE_LOW)
        manager.createNotificationChannel(channel)
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification() {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification())
    }

    /**
     * Media-style notification: article title + "Andromeda is reading", transport
     * actions (pause/play, next in podcast mode, speed cycle, stop). For incognito
     * live pages this only ever names the title the user explicitly played.
     */
    private fun buildNotification(): Notification {
        val playPause = if (paused) {
            action(android.R.drawable.ic_media_play, "Play", ACTION_TOGGLE_PAUSE)
        } else {
            action(android.R.drawable.ic_media_pause, "Pause", ACTION_TOGGLE_PAUSE)
        }
        val builder = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(currentTitle.ifBlank { "Read aloud" })
            .setContentText("Andromeda is reading")
            .setOngoing(true)
            .setContentIntent(openAppIntent())
            .addAction(playPause)
        if (queue != null) {
            builder.addAction(action(android.R.drawable.ic_media_next, "Next", ACTION_NEXT))
        }
        builder.addAction(action(android.R.drawable.ic_menu_rotate, speedLabel(), ACTION_SPEED))
        builder.addAction(action(android.R.drawable.ic_delete, "Stop", ACTION_STOP))
        val style = Notification.MediaStyle().setMediaSession(session?.sessionToken)
        style.setShowActionsInCompactView(0, if (queue != null) 1 else 2)
        builder.setStyle(style)
        return builder.build()
    }

    private fun speedLabel(): String {
        val speed = SPEEDS[speedIndex]
        return if (speed == speed.toInt().toFloat()) "${speed.toInt()}x" else "${speed}x"
    }

    private fun action(icon: Int, title: String, intentAction: String): Notification.Action =
        Notification.Action.Builder(
            Icon.createWithResource(this, icon),
            title,
            servicePendingIntent(intentAction),
        ).build()

    private fun servicePendingIntent(intentAction: String): PendingIntent {
        val intent = Intent(this, ReadAloudService::class.java).setAction(intentAction)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getService(this, intentAction.hashCode(), intent, flags)
    }

    private fun openAppIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(this, 0, intent, flags)
    }

    private fun maxChunkLen(): Int = minOf(TextToSpeech.getMaxSpeechInputLength(), 3900)

    companion object {
        const val ACTION_PLAY_ARTICLE = "com.udaytank.browse.reading.action.PLAY_ARTICLE"
        const val ACTION_PLAY_PENDING = "com.udaytank.browse.reading.action.PLAY_PENDING"
        const val ACTION_PLAY_ALL_UNREAD = "com.udaytank.browse.reading.action.PLAY_ALL_UNREAD"
        const val ACTION_TOGGLE_PAUSE = "com.udaytank.browse.reading.action.TOGGLE_PAUSE"
        const val ACTION_NEXT = "com.udaytank.browse.reading.action.NEXT"
        const val ACTION_SPEED = "com.udaytank.browse.reading.action.SPEED"
        const val ACTION_STOP = "com.udaytank.browse.reading.action.STOP"
        const val EXTRA_ARTICLE_ID = "com.udaytank.browse.reading.extra.ARTICLE_ID"

        private const val CHANNEL_ID = "read_aloud"
        private const val NOTIFICATION_ID = 0x52454144 // "READ": stable, distinct from other services

        /** Speech-rate cycle for the notification's speed action. */
        private val SPEEDS = floatArrayOf(1.0f, 1.25f, 1.5f, 0.75f)

        /**
         * Live-reader handoff: (title, content HTML) set by the UI right before it
         * starts the service with [ACTION_PLAY_PENDING]. Read exactly once, nulled
         * immediately after, and again in [onDestroy] - the same static
         * single-instance bridge (and leak rule) as MediaHoldService's controller.
         */
        @Volatile
        var pendingContent: Pair<String, String>? = null

        fun playArticle(context: Context, articleId: Long) {
            val intent = Intent(context, ReadAloudService::class.java)
                .setAction(ACTION_PLAY_ARTICLE)
                .putExtra(EXTRA_ARTICLE_ID, articleId)
            ContextCompat.startForegroundService(context, intent)
        }

        fun playPending(context: Context, title: String, contentHtml: String) {
            pendingContent = title to contentHtml
            val intent = Intent(context, ReadAloudService::class.java).setAction(ACTION_PLAY_PENDING)
            ContextCompat.startForegroundService(context, intent)
        }

        fun playAllUnread(context: Context) {
            val intent = Intent(context, ReadAloudService::class.java).setAction(ACTION_PLAY_ALL_UNREAD)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
