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

    /**
     * A new chooser request: supersede any stale pending callback (resolved null), store
     * [callback], then run [launch]. A false return from [launch] (picker couldn't open)
     * resolves [callback] null right away — the input must not be left stuck.
     */
    fun begin(callback: (R?) -> Unit, launch: () -> Boolean) {
        // Drain the slot (clear-and-capture, mirroring finish()) until it stays empty: a
        // reentrant begin() from inside a stale callback's null-resolution re-fills it, and
        // storing over that registration would orphan it — a callback that never resolves.
        while (true) {
            val stale = pending ?: break
            pending = null
            stale.invoke(null)
        }
        pending = callback
        if (!launch()) {
            pending = null
            callback(null)
        }
    }

    /** The picker returned: resolve-and-clear the pending callback; no-op if nothing pending. */
    fun finish(result: R?) {
        val callback = pending ?: return
        pending = null
        callback(result)
    }
}
