package com.udaytank.browse.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
    fun `custom search engine url is used`() {
        assertEquals(
            "https://duckduckgo.com/?q=pizza",
            UrlInput.toLoadableUrl("pizza", com.udaytank.browse.data.SearchEngine.DUCKDUCKGO.queryUrl)
        )
    }

    @Test
    fun `special characters in searches are url-encoded`() {
        assertEquals(
            "https://www.google.com/search?q=what+is+2%2B2",
            UrlInput.toLoadableUrl("what is 2+2")
        )
    }

    @Test
    fun `url-like text is detected`() {
        assertTrue(UrlInput.isUrlLike("https://bbc.com"))
        assertTrue(UrlInput.isUrlLike("http://example.org/page"))
        assertTrue(UrlInput.isUrlLike("bbc.com"))
        assertTrue(UrlInput.isUrlLike("  en.wikipedia.org/wiki/Kotlin  "))
    }

    @Test
    fun `search-like text is not url-like`() {
        assertFalse(UrlInput.isUrlLike("best pizza"))
        assertFalse(UrlInput.isUrlLike("pizza"))
        assertFalse(UrlInput.isUrlLike("what is 2.5 plus 2"))
        assertFalse(UrlInput.isUrlLike(""))
        assertFalse(UrlInput.isUrlLike("   "))
    }
}
