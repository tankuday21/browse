package com.udaytank.browse.browser

/** HTTPS-only mode: decides whether a URL should be upgraded before loading. */
object HttpsUpgrade {

    /** Returns the https:// form of an http:// url, or null if no upgrade applies. */
    fun upgrade(url: String): String? =
        if (url.startsWith("http://")) "https://" + url.removePrefix("http://") else null

    /** True when the url is plaintext http and HTTPS-only mode is on. */
    fun shouldUpgrade(url: String, httpsOnly: Boolean): Boolean =
        httpsOnly && url.startsWith("http://")
}
