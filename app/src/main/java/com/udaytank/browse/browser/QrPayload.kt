package com.udaytank.browse.browser

/**
 * Classifies a decoded QR payload (v5.2). Pure string logic, built on the same scheme rules as
 * v4.9's link handling so a QR can never do what a page link couldn't: unsafe schemes
 * (javascript/file/content) are DISPLAY-ONLY text, never opened.
 */
object QrPayload {

    sealed interface Payload {
        /** An http(s) URL (or a bare domain upgraded to https) — open in a tab. */
        data class Web(val url: String) : Payload

        /** A launchable app URI (upi:, mailto:, market:, intent://…) — dispatch, v4.9-hardened. */
        data class App(val url: String) : Payload

        /** Anything else — shown as text with copy/search actions only. */
        data class Text(val raw: String) : Payload
    }

    /**
     * QR data formats that LOOK like URI schemes but aren't launchable Android URIs — WiFi
     * network configs, contact cards, calendar events. Shown as text (copyable) rather than
     * dispatched into a guaranteed "no app can open this" dead end.
     */
    private val dataPrefixes = setOf("wifi", "mecard", "matmsg", "begin", "vcard")

    fun classify(raw: String): Payload {
        val text = raw.trim()
        if (text.isEmpty()) return Payload.Text(text)
        val isHttp = text.startsWith("http://", ignoreCase = true) ||
            text.startsWith("https://", ignoreCase = true)
        if (isHttp) {
            // A URL never contains interior whitespace; a payload like "https://x\nmore" is
            // structured data, not a URL — show it as text rather than loading a mangled URL.
            return if (text.any { it.isWhitespace() }) Payload.Text(text) else Payload.Web(text)
        }
        // QR text is arbitrary (unlike WebView navigations, which are always absolute URIs):
        // a DOTTED first segment is a domain, not a scheme — "example.com/menu" and even
        // "example.com:8080/x" must open as web, never dispatch as an app URI. Checked before
        // the scheme classifier. (Trade-off: a dotted reverse-DNS custom scheme reads as a
        // domain; vanishingly rare in printed QRs.)
        val bareDomain = !text.any { it.isWhitespace() } &&
            text.substringBefore('/').substringBefore(':').contains('.') &&
            UrlHosts.of("https://$text") != null
        if (bareDomain) return Payload.Web("https://$text")
        if (text.substringBefore(':').lowercase() in dataPrefixes) return Payload.Text(text)
        return when (ExternalLinks.classify(text)) {
            ExternalLinks.LinkAction.LoadInPage -> Payload.Text(text) // unschemed / engine-internal
            ExternalLinks.LinkAction.Ignore -> Payload.Text(text) // unsafe scheme: display-only
            is ExternalLinks.LinkAction.OpenApp -> Payload.App(text)
            is ExternalLinks.LinkAction.IntentUri -> Payload.App(text)
        }
    }
}
