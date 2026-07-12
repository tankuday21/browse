package com.udaytank.browse.browser

/**
 * Single source of truth for extracting a lowercase host from a URL. Both [MainActivity][com.udaytank.browse.MainActivity]
 * and [BrowserViewModel][com.udaytank.browse.BrowserViewModel] must agree on what "the host" of a
 * page is - divergent parsers (e.g. `android.net.Uri` vs `java.net.URI`) could let a site look
 * allowlisted to one and not the other, which is exactly the kind of mismatch the background-media
 * allowlist can't tolerate.
 */
object UrlHosts {
    fun of(url: String?): String? =
        url?.let { runCatching { java.net.URI(it).host }.getOrNull() }?.lowercase()
}
