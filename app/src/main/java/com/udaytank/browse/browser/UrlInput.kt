package com.udaytank.browse.browser

import java.net.URLEncoder

object UrlInput {

    private const val SEARCH_URL = "https://www.google.com/search?q="

    /**
     * Turns raw address-bar input into a URL the WebView can load.
     * Decide: is it already a URL, a bare domain, or a search?
     */
    fun toLoadableUrl(input: String): String {
        val text = input.trim() // removes spaces from both ends

        return when {
            // Case 1: already a full URL → return unchanged
            text.startsWith("http://") || text.startsWith("https://") -> text

            // Case 2: no spaces AND has a dot → it's a domain, add https://
            !text.contains(' ') && text.contains('.') -> "https://$text"

            // Case 3: everything else → search for it
            else -> SEARCH_URL + URLEncoder.encode(text, "UTF-8")
        }
    }
}
