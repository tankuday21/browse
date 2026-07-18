package com.udaytank.browse.browser

/**
 * Classifies in-page navigations by scheme (v4.9): what the WebView renders itself vs. what must
 * be dispatched to another app as an Intent vs. what gets swallowed. Pure string logic — the
 * Intent construction and hardening live in the WebViewHolder, which owns the Context.
 */
object ExternalLinks {

    sealed interface LinkAction {
        /** http(s) and engine-internal schemes — the WebView loads it (HTTPS-Only still applies). */
        data object LoadInPage : LinkAction

        /** Swallow the navigation: unsafe scheme, or an external scheme without a user gesture. */
        data object Ignore : LinkAction

        /** A gesture-backed external scheme (mailto, tel, upi, market, …) — ACTION_VIEW it. */
        data class OpenApp(val url: String) : LinkAction

        /** A gesture-backed intent:// URI — parse, harden, launch (fallback URL on failure). */
        data class IntentUri(val url: String) : LinkAction
    }

    /** Schemes the engine renders itself. */
    private val internalSchemes = setOf("http", "https", "about", "blob", "data")

    /**
     * Schemes a page must NEVER open through us, gesture or not: local files, other apps'
     * content providers, script execution.
     */
    private val unsafeSchemes = setOf("file", "content", "javascript")

    /**
     * [hasGesture] is the redirect-hijack gate: only a real user tap may bounce the user into
     * another app — a scripted navigation or redirect chain gets swallowed instead.
     */
    fun classify(url: String, hasGesture: Boolean): LinkAction {
        val colon = url.indexOf(':')
        if (colon <= 0) return LinkAction.LoadInPage // schemeless — let the engine sort it out
        val scheme = url.substring(0, colon).lowercase()
        return when {
            scheme in internalSchemes -> LinkAction.LoadInPage
            scheme in unsafeSchemes -> LinkAction.Ignore
            !hasGesture -> LinkAction.Ignore
            scheme == "intent" -> LinkAction.IntentUri(url)
            else -> LinkAction.OpenApp(url)
        }
    }

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
