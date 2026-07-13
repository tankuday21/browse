package com.udaytank.browse.ui

import android.content.Context
import android.view.View
import android.webkit.WebView

/**
 * A [WebView] that can keep its media renderer awake while the app is backgrounded or the
 * screen is locked.
 *
 * Chromium WebView pauses HTML5 `<video>`/`<audio>` the instant the framework reports the
 * window as non-visible — `onWindowVisibilityChanged(GONE)` fires on screen lock and app
 * background *regardless* of whether [onPause] was called. That, not `onPause`, is what
 * silences background playback. While [keepAliveInBackground] is set we swallow the signal
 * and keep reporting [View.VISIBLE] to the renderer, so playback continues; the lock-screen
 * controls come from the MediaSession in `MediaHoldService`. The flag is cleared the moment
 * the app returns to the foreground or background playback stops, restoring the normal
 * pause-on-background behaviour for every other tab and session.
 */
class KeepAliveWebView(context: Context) : WebView(context) {

    @Volatile
    var keepAliveInBackground: Boolean = false

    override fun onWindowVisibilityChanged(visibility: Int) {
        if (keepAliveInBackground && visibility != View.VISIBLE) {
            super.onWindowVisibilityChanged(View.VISIBLE)
        } else {
            super.onWindowVisibilityChanged(visibility)
        }
    }
}
