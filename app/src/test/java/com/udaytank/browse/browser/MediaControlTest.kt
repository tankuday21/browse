package com.udaytank.browse.browser

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The [MediaControl] snippets are plain strings injected into pages, so they're testable without
 * a WebView: assert each is non-empty, defensive (try/catch), and carries the site-control
 * selectors the lock-screen transport relies on. The JS itself is separately syntax-checked with
 * node's `new Function(js)` (see the repo's media-lockscreen ledger note).
 */
class MediaControlTest {

    private val snippets = listOf(
        MediaControl.PLAY_PAUSE,
        MediaControl.PLAY,
        MediaControl.PAUSE,
        MediaControl.NEXT,
        MediaControl.PREVIOUS,
        MediaControl.MONITOR,
        MediaControl.seekTo(12_000L),
    )

    @Test
    fun `every snippet is non-empty and defensive`() {
        snippets.forEach { js ->
            assertTrue(js.isNotBlank())
            assertTrue("snippet must be wrapped in try/catch", js.contains("try{") || js.contains("try {"))
        }
    }

    @Test
    fun `monitor reports the media timeline for a live lock-screen scrubber`() {
        val monitor = MediaControl.MONITOR
        assertTrue("reports position", monitor.contains("currentTime"))
        assertTrue("reports duration", monitor.contains("duration"))
        // onMediaState now carries (title, playing, positionMs, durationMs).
        assertTrue("state callback carries the timeline", monitor.contains("onMediaState(title(),playing,pos,dur)"))
        assertTrue("refreshes on seek", monitor.contains("'seeked'"))
        assertTrue("refreshes on duration change", monitor.contains("'durationchange'"))
    }

    @Test
    fun `seekTo sets the media element current time from milliseconds`() {
        val js = MediaControl.seekTo(45_000L)
        assertTrue(js.contains("currentTime"))
        assertTrue(js.contains("45000/1000"))
    }

    @Test
    fun `play pause toggles video and audio elements`() {
        assertTrue(MediaControl.PLAY_PAUSE.contains("video,audio"))
        assertTrue(MediaControl.PLAY_PAUSE.contains(".play()"))
        assertTrue(MediaControl.PLAY_PAUSE.contains(".pause()"))
    }

    @Test
    fun `next targets youtube and youtube music controls`() {
        assertTrue(MediaControl.NEXT.contains(".ytp-next-button"))
        assertTrue(MediaControl.NEXT.contains("ytmusic-player-bar"))
        assertTrue(MediaControl.NEXT.contains("next-button"))
        assertTrue(MediaControl.NEXT.contains("[aria-label*=\"Next\"]"))
    }

    @Test
    fun `previous targets youtube and youtube music controls`() {
        assertTrue(MediaControl.PREVIOUS.contains(".ytp-prev-button"))
        assertTrue(MediaControl.PREVIOUS.contains("previous-button"))
        assertTrue(MediaControl.PREVIOUS.contains("[aria-label*=\"Previous\"]"))
    }

    @Test
    fun `monitor reports state and auto-advances on ended through the bridge`() {
        val monitor = MediaControl.MONITOR
        assertTrue(monitor.contains(MediaControl.BRIDGE_NAME))
        assertTrue("reports playback state", monitor.contains("onMediaState"))
        assertTrue("reports track end", monitor.contains("onEnded"))
        assertTrue("listens for the ended event", monitor.contains("'ended'"))
        assertTrue("auto-advance clicks the next control", monitor.contains(".ytp-next-button"))
        assertTrue("guards against double-injection", monitor.contains("__andromedaMediaMon"))
    }

    @Test
    fun `bridge name is the interface the page calls`() {
        assertTrue(MediaControl.BRIDGE_NAME == "AndromedaMedia")
    }
}
