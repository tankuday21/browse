package com.udaytank.browse.browser.adblock

/**
 * Infers a request's [ResourceType] from what WebView's shouldInterceptRequest exposes: the
 * URL path and the request headers. WebView never tells us the type directly, so this is a
 * best-effort ladder, most-reliable signal first:
 *
 *  1. main frame -> DOCUMENT (the caller knows this from WebResourceRequest.isForMainFrame)
 *  2. `Sec-Fetch-Dest` header — set by modern Chromium for every request, names the true
 *     destination
 *  3. `Accept` header — subresource fetches advertise what they expect back
 *  4. URL file extension
 *  5. `X-Requested-With: XMLHttpRequest` — classic AJAX marker
 *  6. OTHER
 *
 * Pure and Android-free so it can be unit-tested directly.
 */
object RequestTyping {

    /**
     * @param path the URL's path component (no query/fragment), e.g. "/ads/banner.js"; null ok.
     * @param headers the request headers as WebView hands them over (case preserved).
     * @param mainFrame WebResourceRequest.isForMainFrame.
     */
    fun infer(path: String?, headers: Map<String, String>, mainFrame: Boolean): ResourceType {
        if (mainFrame) return ResourceType.DOCUMENT

        header(headers, "Sec-Fetch-Dest")?.lowercase()?.let { dest ->
            fromSecFetchDest(dest)?.let { return it }
        }

        val accept = header(headers, "Accept")?.lowercase().orEmpty()
        when {
            accept.contains("text/css") -> return ResourceType.STYLESHEET
            accept.contains("image/") -> return ResourceType.IMAGE
            accept.contains("video/") || accept.contains("audio/") -> return ResourceType.MEDIA
            accept.contains("font/") -> return ResourceType.FONT
        }

        val p = path?.lowercase().orEmpty()
        when {
            endsWithAny(p, FONT_EXTENSIONS) -> return ResourceType.FONT
            endsWithAny(p, SCRIPT_EXTENSIONS) -> return ResourceType.SCRIPT
            p.endsWith(".css") -> return ResourceType.STYLESHEET
            endsWithAny(p, IMAGE_EXTENSIONS) -> return ResourceType.IMAGE
            endsWithAny(p, MEDIA_EXTENSIONS) -> return ResourceType.MEDIA
        }

        if (header(headers, "X-Requested-With") == "XMLHttpRequest") return ResourceType.XHR

        return ResourceType.OTHER
    }

    /** Sec-Fetch-Dest values we can map; anything else falls through to the next signal. */
    private fun fromSecFetchDest(dest: String): ResourceType? = when (dest) {
        "script", "worker", "sharedworker", "serviceworker" -> ResourceType.SCRIPT
        "style" -> ResourceType.STYLESHEET
        "image" -> ResourceType.IMAGE
        "font" -> ResourceType.FONT
        "video", "audio", "track" -> ResourceType.MEDIA
        // A non-main-frame "document" destination is by definition an embedded frame.
        "iframe", "frame", "document", "embed", "object" -> ResourceType.SUBDOCUMENT
        "empty" -> ResourceType.XHR
        else -> null
    }

    /** Case-insensitive header lookup — WebView usually preserves case, sites vary. */
    private fun header(headers: Map<String, String>, name: String): String? {
        headers[name]?.let { return it }
        return headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value
    }

    private fun endsWithAny(path: String, suffixes: List<String>): Boolean =
        suffixes.any { path.endsWith(it) }

    private val SCRIPT_EXTENSIONS = listOf(".js", ".mjs")
    private val FONT_EXTENSIONS = listOf(".woff2", ".woff", ".ttf", ".otf")
    private val IMAGE_EXTENSIONS =
        listOf(".png", ".jpg", ".jpeg", ".gif", ".webp", ".svg", ".ico", ".avif")
    private val MEDIA_EXTENSIONS = listOf(".mp4", ".webm", ".m3u8", ".mp3", ".m4a", ".ts")
}
