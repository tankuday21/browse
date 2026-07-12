package com.udaytank.browse.browser

/**
 * Chrome-style auto-hide hysteresis for the bottom command bar (pure logic, unit-testable).
 *
 * Fed raw scroll events ([onScroll] with the page's absolute scrollY and the delta since the
 * last event) and answers one question: should the bar be visible right now?
 *
 *  - Hides only after a CUMULATIVE downward scroll greater than [hideThresholdPx]
 *    (a density-corrected ~24dp, set by the UI layer), so a stray finger wobble never hides it.
 *  - Shows again after cumulative upward scroll greater than [showThresholdPx] — deliberately
 *    tiny, so "even slightly" scrolling up brings the bar back, like Chrome.
 *  - Always visible within [topThresholdPx] of the page top.
 *  - Accumulation resets whenever the scroll direction flips, so long slow scrolls with small
 *    counter-movements don't creep across a threshold.
 *
 * Not thread-safe by design: scroll callbacks and resets all arrive on the UI thread.
 */
class BarScrollPolicy(
    var hideThresholdPx: Int = DEFAULT_HIDE_THRESHOLD_PX,
    private val showThresholdPx: Int = DEFAULT_SHOW_THRESHOLD_PX,
    private val topThresholdPx: Int = DEFAULT_TOP_THRESHOLD_PX,
) {
    /** Current answer; starts visible. */
    var visible: Boolean = true
        private set

    /** Signed running scroll distance since the last direction flip (+down / -up). */
    private var accumulated = 0

    /**
     * Feeds one scroll event; returns the (possibly updated) visibility.
     * [dy] > 0 = the page moved down (content scrolled up), matching
     * `scrollY - oldScrollY` from View.OnScrollChangeListener.
     */
    fun onScroll(scrollY: Int, dy: Int): Boolean {
        if (scrollY <= topThresholdPx) {
            accumulated = 0
            visible = true
            return true
        }
        if (dy > 0) {
            if (accumulated < 0) accumulated = 0 // direction flip: restart the count
            accumulated += dy
            if (accumulated > hideThresholdPx) visible = false
        } else if (dy < 0) {
            if (accumulated > 0) accumulated = 0 // direction flip: restart the count
            accumulated += dy
            if (-accumulated > showThresholdPx) visible = true
        }
        return visible
    }

    /** Forces the bar visible and forgets accumulated scroll (tab switch, nav start, etc.). */
    fun reset() {
        accumulated = 0
        visible = true
    }

    companion object {
        /** Fallback ≈24dp at a common ~2.75x density; the UI layer overrides with a real px value. */
        const val DEFAULT_HIDE_THRESHOLD_PX = 66
        const val DEFAULT_SHOW_THRESHOLD_PX = 8
        const val DEFAULT_TOP_THRESHOLD_PX = 8
    }
}
