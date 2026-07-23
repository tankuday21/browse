package com.udaytank.browse.media

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.udaytank.browse.BrowseApplication
import com.udaytank.browse.data.PlayerProgressEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/**
 * The Andromeda Player's engine host (v6.0). A single [ExoPlayer] lives here inside a
 * [MediaSession] so audio keeps playing (with lock-screen/notification controls) when the UI is
 * backgrounded. The Compose player screen drives this through a `MediaController` bound to the
 * session — there is exactly ONE player, never a UI copy that would double up the audio.
 *
 * Each queued item's [androidx.media3.common.MediaItem.mediaId] is set to its file path, which is
 * how this service knows which file to persist resume progress for without any extra plumbing.
 */
@OptIn(UnstableApi::class)
class AndromedaPlayerService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var persistJob: Job? = null

    // v6.3 sleep timer.
    private var sleepTickerJob: Job? = null
    private var endOfTrackArmed = false

    override fun onCreate() {
        super.onCreate()
        sleepState.value = SleepState() // fresh session starts with no timer
        val exo = ExoPlayer.Builder(this)
            // Let ExoPlayer manage audio focus (pause when another app plays) and pause when
            // headphones are unplugged — the behavior a music player is expected to have.
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        player = exo
        mediaSession = MediaSession.Builder(this, exo).build()

        exo.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                // A finished clip clears its saved position so it restarts next time.
                if (state == Player.STATE_ENDED) {
                    exo.currentMediaItem?.mediaId?.let { path ->
                        saveProgress(path, positionMs = 0, durationMs = 0, finished = true)
                    }
                    // The queue ended while "End of track" was armed — the timer is satisfied.
                    // STATE_ENDED already halts playback; pause() is defensive in case a future
                    // repeat/loop mode ever changed that (STATE_ENDED wouldn't fire under repeat).
                    if (endOfTrackArmed) {
                        exo.pause()
                        disarmSleep()
                    }
                }
            }

            override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
                // "End of track": the current item finished and the player auto-advanced — pause
                // at that boundary and clear the timer.
                if (endOfTrackArmed && reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                    exo.pause()
                    disarmSleep()
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                // Persist immediately on pause (the 5 s ticker only runs while playing, so
                // without this a pause-then-kill loses up to 5 s of position — spec: save on pause).
                if (!isPlaying) captureProgress(exo)
            }
        })
        startProgressPersistence(exo)
    }

    /** Every few seconds, persist the current item's position so the player can resume it. */
    private fun startProgressPersistence(exo: ExoPlayer) {
        persistJob = scope.launch {
            while (true) {
                delay(PROGRESS_SAVE_INTERVAL_MS)
                if (exo.isPlaying) captureProgress(exo)
            }
        }
    }

    /** Snapshot the current item's position (finished items are cleared) and persist it. */
    private fun captureProgress(exo: ExoPlayer) {
        val path = exo.currentMediaItem?.mediaId ?: return
        val duration = exo.duration.takeIf { it != C.TIME_UNSET } ?: 0L
        val position = exo.currentPosition
        if (PlayerProgressPolicy.isFinished(position, duration)) {
            saveProgress(path, 0, 0, finished = true)
        } else {
            saveProgress(path, position, duration, finished = false)
        }
    }

    private fun saveProgress(filePath: String, positionMs: Long, durationMs: Long, finished: Boolean) {
        val dao = (application as BrowseApplication).database.playerProgressDao()
        scope.launch {
            withContext(Dispatchers.IO) {
                if (finished) {
                    dao.delete(filePath)
                } else {
                    dao.upsert(PlayerProgressEntity(filePath, positionMs, durationMs, System.currentTimeMillis()))
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_SET_SLEEP) {
            val preset = runCatching {
                SleepPreset.valueOf(intent.getStringExtra(EXTRA_PRESET) ?: SleepPreset.OFF.name)
            }.getOrDefault(SleepPreset.OFF)
            applySleep(preset)
            return START_NOT_STICKY
        }
        return super.onStartCommand(intent, flags, startId)
    }

    /** Applies a sleep preset: minute presets start a countdown; END_OF_TRACK arms the transition. */
    private fun applySleep(preset: SleepPreset) {
        sleepTickerJob?.cancel()
        endOfTrackArmed = false
        when (preset) {
            SleepPreset.OFF -> sleepState.value = SleepState()
            SleepPreset.END_OF_TRACK -> {
                endOfTrackArmed = true
                sleepState.value = SleepState(preset, 0)
            }
            else -> {
                val deadline = SleepTimer.deadline(preset, SystemClock.elapsedRealtime()) ?: return
                sleepTickerJob = scope.launch {
                    while (true) {
                        val remaining = SleepTimer.remainingSeconds(deadline, SystemClock.elapsedRealtime())
                        sleepState.value = SleepState(preset, remaining)
                        if (remaining <= 0) {
                            player?.pause()
                            sleepState.value = SleepState()
                            break
                        }
                        delay(1_000L)
                    }
                }
            }
        }
    }

    private fun disarmSleep() {
        endOfTrackArmed = false
        sleepState.value = SleepState()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Swiping the app away with nothing playing should not leave a zombie service/notification.
        val p = player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        persistJob?.cancel()
        sleepTickerJob?.cancel()
        sleepState.value = SleepState()
        // Final synchronous flush BEFORE cancelling the scope / releasing the player, so the
        // notification "stop" and app-kill paths don't lose the last position (spec: save on stop).
        player?.let { p ->
            val path = p.currentMediaItem?.mediaId
            if (path != null) {
                val duration = p.duration.takeIf { it != C.TIME_UNSET } ?: 0L
                val position = p.currentPosition
                val finished = PlayerProgressPolicy.isFinished(position, duration)
                val dao = (application as BrowseApplication).database.playerProgressDao()
                runBlocking(Dispatchers.IO) {
                    if (finished) dao.delete(path)
                    else dao.upsert(PlayerProgressEntity(path, position, duration, System.currentTimeMillis()))
                }
            }
        }
        scope.cancel()
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        player = null
        super.onDestroy()
    }

    companion object {
        private const val PROGRESS_SAVE_INTERVAL_MS = 5_000L

        private const val ACTION_SET_SLEEP = "com.udaytank.browse.media.action.SET_SLEEP"
        private const val EXTRA_PRESET = "com.udaytank.browse.media.extra.SLEEP_PRESET"

        /** Live sleep-timer state for the Andromeda Player UI (in-process; one service instance).
         *  Mutable handle is private — only the service writes it; the UI reads [sleepStateFlow]. */
        private val sleepState = MutableStateFlow(SleepState())
        val sleepStateFlow: StateFlow<SleepState> = sleepState.asStateFlow()

        /**
         * UI entry point: set (or clear, with OFF) the sleep timer on the running player service.
         * Plain startService (not startForegroundService) — this is only ever called from the
         * player screen while the service is already playing/foreground, so there's no
         * startForeground() obligation to satisfy.
         */
        fun setSleep(context: Context, preset: SleepPreset) {
            val intent = Intent(context, AndromedaPlayerService::class.java)
                .setAction(ACTION_SET_SLEEP)
                .putExtra(EXTRA_PRESET, preset.name)
            runCatching { context.startService(intent) }
        }
    }
}
