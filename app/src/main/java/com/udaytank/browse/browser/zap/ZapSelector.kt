package com.udaytank.browse.browser.zap

/**
 * Sanitizes CSS selectors coming from the in-page Element Zapper picker before they're persisted
 * and re-injected. Defensive: the selector is later interpolated into an injected JS string, so a
 * malformed/hostile value must never break out of that context or the page. Pure + unit-tested.
 */
object ZapSelector {
    const val MAX_LEN = 512

    /**
     * Returns a trimmed selector, or null if unusable: blank, too long, containing control
     * characters, or containing '<' (never valid in a CSS selector — a defense against ever
     * landing in an HTML context). Valid CSS punctuation ('>' child combinator, '"' in attribute
     * selectors, '\' escapes) is KEPT — injection safety comes from JSON-encoding the selector
     * when it's interpolated into the injected script, not from banning legal characters.
     */
    fun sanitize(raw: String?): String? {
        val s = raw?.trim().orEmpty()
        if (s.isEmpty() || s.length > MAX_LEN) return null
        if (s.any { it.isISOControl() }) return null
        if (s.contains('<')) return null
        return s
    }
}
