package com.udaytank.browse.browser.adblock

import org.junit.Assert.assertEquals
import org.junit.Test

class RequestTypingTest {

    private fun infer(
        path: String? = "/thing",
        headers: Map<String, String> = emptyMap(),
        mainFrame: Boolean = false,
    ) = RequestTyping.infer(path, headers, mainFrame)

    @Test
    fun `main frame is always a document`() {
        assertEquals(ResourceType.DOCUMENT, infer(path = "/ads/banner.js", mainFrame = true))
        assertEquals(
            ResourceType.DOCUMENT,
            infer(headers = mapOf("Sec-Fetch-Dest" to "image"), mainFrame = true),
        )
    }

    @Test
    fun `sec-fetch-dest wins over every other signal`() {
        // Extension says script, Accept says image — the fetch destination is what counts.
        assertEquals(
            ResourceType.IMAGE,
            infer(
                path = "/tracker.js",
                headers = mapOf("Sec-Fetch-Dest" to "image", "Accept" to "text/css"),
            ),
        )
        assertEquals(
            ResourceType.SCRIPT,
            infer(path = "/style.css", headers = mapOf("Sec-Fetch-Dest" to "script")),
        )
    }

    @Test
    fun `sec-fetch-dest maps all supported destinations`() {
        val cases = mapOf(
            "script" to ResourceType.SCRIPT,
            "style" to ResourceType.STYLESHEET,
            "image" to ResourceType.IMAGE,
            "font" to ResourceType.FONT,
            "video" to ResourceType.MEDIA,
            "audio" to ResourceType.MEDIA,
            "iframe" to ResourceType.SUBDOCUMENT,
            "document" to ResourceType.SUBDOCUMENT, // non-main-frame document = embedded frame
            "empty" to ResourceType.XHR,
        )
        for ((dest, expected) in cases) {
            assertEquals(dest, expected, infer(headers = mapOf("Sec-Fetch-Dest" to dest)))
        }
    }

    @Test
    fun `unknown sec-fetch-dest falls through to the next signal`() {
        assertEquals(
            ResourceType.IMAGE,
            infer(path = "/pixel.gif", headers = mapOf("Sec-Fetch-Dest" to "report")),
        )
    }

    @Test
    fun `accept header classifies stylesheet image media font`() {
        assertEquals(
            ResourceType.STYLESHEET,
            infer(headers = mapOf("Accept" to "text/css,*/*;q=0.1")),
        )
        assertEquals(
            ResourceType.IMAGE,
            infer(headers = mapOf("Accept" to "image/avif,image/webp,*/*;q=0.8")),
        )
        assertEquals(ResourceType.MEDIA, infer(headers = mapOf("Accept" to "video/webm,*/*")))
        assertEquals(ResourceType.MEDIA, infer(headers = mapOf("Accept" to "audio/mpeg")))
        assertEquals(ResourceType.FONT, infer(headers = mapOf("Accept" to "font/woff2")))
    }

    @Test
    fun `header lookup is case-insensitive`() {
        assertEquals(
            ResourceType.STYLESHEET,
            infer(headers = mapOf("accept" to "text/css")),
        )
        assertEquals(
            ResourceType.IMAGE,
            infer(headers = mapOf("sec-fetch-dest" to "image")),
        )
    }

    @Test
    fun `url extension classifies script stylesheet image media font`() {
        assertEquals(ResourceType.SCRIPT, infer(path = "/lib/ads.js"))
        assertEquals(ResourceType.SCRIPT, infer(path = "/module.mjs"))
        assertEquals(ResourceType.STYLESHEET, infer(path = "/theme.css"))
        for (ext in listOf("png", "jpg", "jpeg", "gif", "webp", "svg", "ico", "avif")) {
            assertEquals(ext, ResourceType.IMAGE, infer(path = "/img/pic.$ext"))
        }
        for (ext in listOf("mp4", "webm", "m3u8", "mp3", "m4a", "ts")) {
            assertEquals(ext, ResourceType.MEDIA, infer(path = "/media/clip.$ext"))
        }
        for (ext in listOf("woff2", "woff", "ttf", "otf")) {
            assertEquals(ext, ResourceType.FONT, infer(path = "/fonts/face.$ext"))
        }
    }

    @Test
    fun `extension matching is case-insensitive`() {
        assertEquals(ResourceType.IMAGE, infer(path = "/BANNER.PNG"))
        assertEquals(ResourceType.SCRIPT, infer(path = "/Ads.JS"))
    }

    @Test
    fun `x-requested-with marks xhr`() {
        assertEquals(
            ResourceType.XHR,
            infer(path = "/api/data", headers = mapOf("X-Requested-With" to "XMLHttpRequest")),
        )
    }

    @Test
    fun `accept beats extension`() {
        // A CSS-accepting fetch for a .php URL is a stylesheet load.
        assertEquals(
            ResourceType.STYLESHEET,
            infer(path = "/style.php", headers = mapOf("Accept" to "text/css,*/*;q=0.1")),
        )
    }

    @Test
    fun `nothing recognizable is OTHER`() {
        assertEquals(ResourceType.OTHER, infer(path = "/api/data"))
        assertEquals(ResourceType.OTHER, infer(path = null))
        assertEquals(
            ResourceType.OTHER,
            infer(path = "/page", headers = mapOf("Accept" to "text/html,application/xhtml+xml")),
        )
    }
}
