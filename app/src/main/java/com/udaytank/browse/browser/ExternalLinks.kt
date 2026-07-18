package com.udaytank.browse.browser

/**
 * Classifies in-page navigations by scheme (v4.9): what the WebView renders itself vs. what must
 * be dispatched to another app as an Intent vs. what gets swallowed. Pure string logic — the
 * Intent construction lives in [IntentHardening] / the WebViewHolder, which owns the Context.
 */
object ExternalLinks {

    sealed interface LinkAction {
        /** http(s) and engine-internal schemes — the WebView loads it (HTTPS-Only still applies). */
        data object LoadInPage : LinkAction

        /** Swallow the navigation entirely (unsafe scheme — files, content providers, script). */
        data object Ignore : LinkAction

        /** An external scheme (mailto, tel, upi, market, …) — ACTION_VIEW it. */
        data class OpenApp(val url: String) : LinkAction

        /** An intent:// URI — parse, harden ([IntentHardening]), launch (fallback URL on failure). */
        data class IntentUri(val url: String) : LinkAction
    }

    /** Schemes the engine renders itself. */
    private val internalSchemes = setOf("http", "https", "about", "blob", "data")

    /**
     * Schemes a page must NEVER open through us, gesture or not: local files, other apps'
     * content providers, script execution. Also re-checked against the DATA scheme a page
     * smuggles inside an intent:// URI — see [IntentHardening].
     */
    val unsafeSchemes = setOf("file", "content", "javascript")

    /** RFC 3986 scheme shape; anything else isn't a scheme and goes back to the engine. */
    private val schemeShape = Regex("[a-zA-Z][a-zA-Z0-9+.-]*")

    fun classify(url: String): LinkAction {
        val colon = url.indexOf(':')
        if (colon <= 0) return LinkAction.LoadInPage // schemeless — let the engine sort it out
        val scheme = url.substring(0, colon).lowercase()
        if (!schemeShape.matches(scheme)) return LinkAction.LoadInPage // "host:8080/x" etc.
        return when {
            scheme in internalSchemes -> LinkAction.LoadInPage
            scheme in unsafeSchemes -> LinkAction.Ignore
            scheme == "intent" -> LinkAction.IntentUri(url)
            else -> LinkAction.OpenApp(url)
        }
    }

    /**
     * Whether an external-app launch needs the user's explicit confirmation first:
     * - no user gesture — a redirect chain or scripted navigation must never auto-bounce the
     *   user into another app; a confirm prompt (instead of a silent swallow) keeps legitimate
     *   tap→302→upi: payment handoffs recoverable when WebView loses the gesture bit;
     * - incognito — launching another app leaks the browsing context out of incognito.
     * A gesture-backed tap in a normal tab launches directly, Chrome-style.
     */
    fun needsConfirm(hasGesture: Boolean, incognito: Boolean): Boolean = !hasGesture || incognito

    /**
     * Validates an intent:// URI's `browser_fallback_url` extra before we load it in the page:
     * only http(s) — a fallback must not itself be a scheme that re-enters app-launching (or
     * worse, javascript:).
     */
    fun safeFallbackUrl(raw: String?): String? {
        val url = raw?.trim().orEmpty()
        val ok = url.startsWith("https://", ignoreCase = true) || url.startsWith("http://", ignoreCase = true)
        return if (ok) url else null
    }
}
