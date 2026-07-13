package com.udaytank.browse.media

import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState

/**
 * Thin wrapper over the platform [MediaSession] that surfaces the foreground media tab on the
 * lock screen with a title and Previous / Play-Pause / Next transport. Deliberately uses the
 * platform session (not androidx.media / media-compat, which isn't a dependency and can't be
 * added here) — the exact proven pattern [com.udaytank.browse.reading.ReadAloudService] already
 * ships for its read-aloud session, so no new Gradle dep is needed.
 *
 * The browser can't know a page's real "next track", so the transport callbacks don't act on
 * any WebView media API: they invoke the supplied lambdas, which [MediaHoldService] wires to
 * injected-JS commands that drive the page's own player (see [com.udaytank.browse.browser.MediaControl]).
 *
 * This session is independent of ReadAloudService's — the two never share state and, since each
 * only becomes the active session while its own foreground service is up, they don't fight.
 */
class BrowserMediaSession(
    context: Context,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onStop: () -> Unit,
    onSeek: (positionMs: Long) -> Unit,
) {
    private val session = MediaSession(context, "AndromedaMedia").apply {
        setCallback(object : MediaSession.Callback() {
            // Play and Pause both toggle: the JS command flips based on the element's own
            // paused state, and the session's reported STATE_* tells the OS which icon to show.
            override fun onPlay() = onPlayPause()
            override fun onPause() = onPlayPause()
            override fun onSkipToNext() = onNext()
            override fun onSkipToPrevious() = onPrevious()
            override fun onStop() = onStop()
            override fun onSeekTo(pos: Long) = onSeek(pos)
        })
        isActive = true
    }

    /** Token for binding a [android.app.Notification.MediaStyle] to this session. */
    val token: MediaSession.Token get() = session.sessionToken

    /**
     * Pushes the current page/video title, playing state, and timeline to the lock screen.
     *
     * [positionMs]/[durationMs] are -1 when the page doesn't expose a finite timeline (live
     * streams, or before metadata loads) — then we report an unknown position so the OS shows
     * transport controls without a bogus scrubber. When they ARE known, we publish position +
     * a playback speed of 1.0 while playing, and the OS *extrapolates* the scrubber forward
     * from the report time — so the elapsed/total time ticks live between our periodic updates.
     */
    fun update(title: String, playing: Boolean, positionMs: Int, durationMs: Int) {
        val metadata = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, title.ifBlank { "Playing in background" })
        if (durationMs > 0) metadata.putLong(MediaMetadata.METADATA_KEY_DURATION, durationMs.toLong())
        session.setMetadata(metadata.build())

        var actions = PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
            PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_STOP or
            PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS
        if (durationMs > 0) actions = actions or PlaybackState.ACTION_SEEK_TO

        val position = if (positionMs >= 0) positionMs.toLong() else PlaybackState.PLAYBACK_POSITION_UNKNOWN
        session.setPlaybackState(
            PlaybackState.Builder()
                .setActions(actions)
                .setState(
                    if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                    position,
                    if (playing) 1f else 0f,
                )
                .build()
        )
    }

    fun release() {
        session.isActive = false
        session.release()
    }
}
