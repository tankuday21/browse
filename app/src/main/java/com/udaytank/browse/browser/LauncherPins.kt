package com.udaytank.browse.browser

/**
 * Pure logic for "Add to Home screen" launcher pins (v5.7). The Bitmap/Icon/Intent plumbing
 * lives in MainActivity; this is the JVM-testable part.
 */
object LauncherPins {

    /** Launchers truncate around here; clamping ourselves keeps the ellipsis predictable. */
    private const val MAX_LABEL = 24

    /**
     * The pin's label: the page title when it's meaningful — non-blank and not just the URL
     * echoed back (many pages title themselves with their own address) — else the host with
     * any `www.` prefix dropped.
     */
    fun shortcutLabel(title: String?, url: String, host: String): String {
        val cleanTitle = title?.trim().orEmpty()
        val meaningful = cleanTitle.isNotEmpty() &&
            !cleanTitle.equals(url, ignoreCase = true) &&
            !cleanTitle.equals(host, ignoreCase = true)
        val base = if (meaningful) cleanTitle else host.removePrefix("www.")
        return if (base.length <= MAX_LABEL) base else base.take(MAX_LABEL - 1).trimEnd() + "…"
    }
}
