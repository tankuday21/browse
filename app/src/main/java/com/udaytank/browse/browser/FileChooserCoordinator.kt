package com.udaytank.browse.browser

/**
 * Owns the exactly-once lifecycle of a WebView file-chooser callback (v4.8). The WebView's
 * ValueCallback must be invoked exactly once — never invoking it leaves the page's input stuck,
 * invoking it twice throws — so every transition here resolves or drops deliberately:
 *
 * - [begin] resolves a stale pending callback with null before storing the new one (a page can
 *   re-trigger the chooser while one is in flight);
 * - a [begin] whose launch fails resolves the new callback null immediately;
 * - [finish] with nothing pending (e.g. a picker result redelivered after activity recreation
 *   built a fresh coordinator) is dropped — the page that owned the callback died with the
 *   old activity, so there is nothing to resolve;
 * - [finish] clears the slot before invoking, so a reentrant [begin] can't double-resolve.
 *
 * Generic over the result payload so the state machine unit-tests on the JVM without
 * android.net.Uri.
 */
class FileChooserCoordinator<R : Any> {

    private var pending: ((R?) -> Unit)? = null
    private var generation = 0

    /**
     * A new chooser request: supersede any stale pending callback (resolved null), store
     * [callback], then run [launch] with this request's GENERATION — the caller keeps it with
     * whatever per-launch state it owns (v5.3: the camera-capture temp file) and hands it back
     * to [finish], which drops results from superseded launches. A false return from [launch]
     * (picker couldn't open) resolves [callback] null right away — the input must not be left
     * stuck.
     */
    fun begin(callback: (R?) -> Unit, launch: (generation: Int) -> Boolean) {
        // Drain the slot (clear-and-capture, mirroring finish()) until it stays empty: a
        // reentrant begin() from inside a stale callback's null-resolution re-fills it, and
        // storing over that registration would orphan it — a callback that never resolves.
        while (true) {
            val stale = pending ?: break
            pending = null
            stale.invoke(null)
        }
        pending = callback
        if (!launch(++generation)) {
            pending = null
            callback(null)
        }
    }

    /**
     * The picker returned. Resolves-and-clears the pending callback ONLY when [generation]
     * matches the current request — a stale result from a superseded launch (its callback was
     * already resolved null) must not consume the NEW request's callback or state (v5.3: doing
     * so deleted the new capture file and silently dropped the user's photo). No-op when
     * nothing is pending (e.g. redelivery after process death).
     */
    fun finish(generation: Int, result: R?) {
        if (generation != this.generation) return
        val callback = pending ?: return
        pending = null
        callback(result)
    }
}
