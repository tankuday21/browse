package com.udaytank.browse.browser

import com.udaytank.browse.data.SearchEngine
import java.net.URLEncoder

object UrlInput {

    /**
     * Turns raw address-bar input into a URL the WebView can load.
     * Decide: is it already a URL, a bare domain, or a search?
     */
    fun toLoadableUrl(input: String, searchUrl: String = SearchEngine.GOOGLE.queryUrl): String {
        val text = input.trim() // removes spaces from both ends

        return when {
            // Case 1: already a full URL → return unchanged
            text.startsWith("http://") || text.startsWith("https://") -> text

            // Case 2: no spaces AND has a dot → it's a domain, add https://
            !text.contains(' ') && text.contains('.') -> "https://$text"

            // Case 3: everything else → search for it. Custom engines (v5.8) use the %s
            // template convention (substituted everywhere it appears); built-ins keep their
            // append-style prefixes.
            else -> {
                val encoded = URLEncoder.encode(text, "UTF-8")
                if (searchUrl.contains("%s")) searchUrl.replace("%s", encoded) else searchUrl + encoded
            }
        }
    }

    /**
     * True when [toLoadableUrl] would treat [input] as a URL rather than a search — the
     * clipboard chip (A6) only appears for copied text that would actually navigate.
     */
    fun isUrlLike(input: String): Boolean {
        val text = input.trim()
        return text.startsWith("http://") || text.startsWith("https://") ||
            (text.isNotEmpty() && !text.contains(' ') && text.contains('.'))
    }
}
