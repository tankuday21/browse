package com.udaytank.browse.browser

import org.junit.Assert.assertEquals
import org.junit.Test

class UrlInputTest {

    @Test
    fun `full url with scheme is returned unchanged`() {
        assertEquals("https://bbc.com", UrlInput.toLoadableUrl("https://bbc.com"))
        assertEquals("http://example.org/page", UrlInput.toLoadableUrl("http://example.org/page"))
    }

    @Test
    fun `domain without scheme gets https prefix`() {
        assertEquals("https://bbc.com", UrlInput.toLoadableUrl("bbc.com"))
        assertEquals("https://en.wikipedia.org/wiki/Kotlin", UrlInput.toLoadableUrl("en.wikipedia.org/wiki/Kotlin"))
    }

    @Test
    fun `words with spaces become a search query`() {
        assertEquals(
            "https://www.google.com/search?q=best+pizza",
            UrlInput.toLoadableUrl("best pizza")
        )
    }

    @Test
    fun `single word without a dot is a search`() {
        assertEquals(
            "https://www.google.com/search?q=pizza",
            UrlInput.toLoadableUrl("pizza")
        )
    }

    @Test
    fun `surrounding whitespace is ignored`() {
        assertEquals("https://bbc.com", UrlInput.toLoadableUrl("  bbc.com  "))
    }

    @Test
    fun `special characters in searches are url-encoded`() {
        assertEquals(
            "https://www.google.com/search?q=what+is+2%2B2",
            UrlInput.toLoadableUrl("what is 2+2")
        )
    }
}
