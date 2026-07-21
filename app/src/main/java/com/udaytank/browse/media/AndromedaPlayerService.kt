package com.udaytank.browse.media

import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
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

    override fun onCreate() {
        super.onCreate()
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
                    val path = exo.currentMediaItem?.mediaId ?: return
                    saveProgress(path, positionMs = 0, durationMs = 0, finished = true)
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
    }
}
