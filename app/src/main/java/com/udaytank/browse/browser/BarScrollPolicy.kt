package com.udaytank.browse.browser

/** The bottom command bar's two shrink states: full-size or a slim collapsed pill. */
enum class BarState { Full, Slim }

/**
 * Chrome-style shrink hysteresis for the bottom command bar (pure logic, unit-testable).
 *
 * Fed raw scroll events ([onScroll] with the page's absolute scrollY and the delta since the
 * last event) and answers one question: should the bar be [BarState.Full] or [BarState.Slim]
 * right now?
 *
 *  - Shrinks to [BarState.Slim] only after a CUMULATIVE downward scroll at or beyond
 *    [shrinkThresholdPx] (a density-corrected value set by the UI layer), so a stray finger
 *    wobble never shrinks it.
 *  - Expands back to [BarState.Full] after cumulative upward scroll at or beyond
 *    [expandThresholdPx] — deliberately tiny, so "even slightly" scrolling up brings the bar
 *    back, like Chrome.
 *  - Always [BarState.Full] within [expandThresholdPx] of the page top.
 *  - Accumulation resets whenever the scroll direction flips, so long slow scrolls with small
 *    counter-movements don't creep across a threshold.
 *
 * Not thread-safe by design: scroll callbacks and resets all arrive on the UI thread.
 */
class BarScrollPolicy(
    private val shrinkThresholdPx: Int = DEFAULT_SHRINK_THRESHOLD_PX,
    private val expandThresholdPx: Int = DEFAULT_EXPAND_THRESHOLD_PX,
) {
    /** Current answer; starts full. */
    var state: BarState = BarState.Full
        private set

    /** Signed running scroll distance since the last direction flip (+down / -up). */
    private var accumulated = 0

    /**
     * Feeds one scroll event; returns the (possibly updated) [BarState].
     * [dy] > 0 = the page moved down (content scrolled up), matching
     * `scrollY - oldScrollY` from View.OnScrollChangeListener.
     */
    fun onScroll(scrollY: Int, dy: Int): BarState {
        if (scrollY <= expandThresholdPx) {
            accumulated = 0
            state = BarState.Full
            return state
        }
        if (dy > 0) {
            if (accumulated < 0) accumulated = 0 // direction flip: restart the count
            accumulated += dy
            if (accumulated >= shrinkThresholdPx) state = BarState.Slim
        } else if (dy < 0) {
            if (accumulated > 0) accumulated = 0 // direction flip: restart the count
            accumulated += dy
            if (-accumulated >= expandThresholdPx) state = BarState.Full
        }
        return state
    }

    /** Forces the bar full-size and forgets accumulated scroll (tab switch, nav start, etc.). */
    fun reset() {
        accumulated = 0
        state = BarState.Full
    }

    companion object {
        /** Fallback ≈24dp at a common ~2.75x density; the UI layer overrides with a real px value. */
        const val DEFAULT_SHRINK_THRESHOLD_PX = 60
        const val DEFAULT_EXPAND_THRESHOLD_PX = 8
    }
}
