package com.udaytank.browse

import com.udaytank.browse.browser.CustomSearchEngine
import com.udaytank.browse.browser.SearchEngines
import com.udaytank.browse.browser.UrlInput
import com.udaytank.browse.data.SearchEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchEnginesTest {

    private val kagi = CustomSearchEngine("Kagi", "https://kagi.com/search?q=%s")
    private val startpage = CustomSearchEngine("Startpage", "https://www.startpage.com/sp/search?query=%s")

    // --- codec ---

    @Test
    fun `encode-decode round trip`() {
        val engines = listOf(kagi, startpage)
        assertEquals(engines, SearchEngines.decode(SearchEngines.encode(engines)))
    }

    @Test
    fun `empty list encodes blank and blank decodes empty`() {
        assertEquals("", SearchEngines.encode(emptyList()))
        assertEquals(emptyList<CustomSearchEngine>(), SearchEngines.decode(""))
    }

    @Test
    fun `malformed json decodes empty, never throws`() {
        assertEquals(emptyList<CustomSearchEngine>(), SearchEngines.decode("not json"))
        assertEquals(emptyList<CustomSearchEngine>(), SearchEngines.decode("{\"a\":1}"))
        // Partial garbage: valid entries survive, blank/malformed ones are dropped.
        val mixed = """[{"name":"Kagi","template":"https://kagi.com/search?q=%s"},{"name":""},"junk"]"""
        assertEquals(listOf(kagi), SearchEngines.decode(mixed))
    }

    // --- validate ---

    @Test
    fun `validation requires name, https, and the query marker`() {
        assertTrue(SearchEngines.validate("Kagi", "https://kagi.com/search?q=%s"))
        assertTrue(SearchEngines.validate("Local", "https://localhost/search?q=%s")) // self-hosted
        assertTrue(SearchEngines.validate("LAN", "https://192.168.1.10/search?q=%s")) // dotted IP
        assertFalse(SearchEngines.validate("  ", "https://kagi.com/search?q=%s")) // blank name
        assertFalse(SearchEngines.validate("Kagi", "http://kagi.com/search?q=%s")) // plaintext
        assertFalse(SearchEngines.validate("Kagi", "https://kagi.com/search?q=")) // no %s
        assertFalse(SearchEngines.validate("Kagi", "https://%s")) // no real host
        // The marker must never sit in the authority — searches would route to a term-derived host.
        assertFalse(SearchEngines.validate("Weird", "https://%s.example.com/search?q=%s"))
    }

    @Test
    fun `searching for the literal marker cannot re-substitute`() {
        // URLEncoder turns % into %25, so an encoded query can never contain "%s" itself.
        assertEquals(
            "https://kagi.com/search?q=%25s",
            UrlInput.toLoadableUrl("%s", "https://kagi.com/search?q=%s"),
        )
    }

    // --- resolve ---

    @Test
    fun `selected custom wins, vanished or blank selection falls back to the built-in`() {
        val customs = listOf(kagi)
        val resolvedCustom = SearchEngines.resolve(SearchEngine.GOOGLE, customs, "Kagi")
        assertEquals("Kagi", resolvedCustom.label)
        assertEquals(kagi.template, resolvedCustom.queryUrl)

        val vanished = SearchEngines.resolve(SearchEngine.DUCKDUCKGO, customs, "Gone")
        assertEquals(SearchEngine.DUCKDUCKGO.label, vanished.label)
        assertEquals(SearchEngine.DUCKDUCKGO.queryUrl, vanished.queryUrl)

        val blank = SearchEngines.resolve(SearchEngine.BING, customs, "")
        assertEquals(SearchEngine.BING.queryUrl, blank.queryUrl)
    }

    // --- template substitution in UrlInput ---

    @Test
    fun `percent-s templates substitute the encoded query`() {
        assertEquals(
            "https://kagi.com/search?q=hello+world",
            UrlInput.toLoadableUrl("hello world", "https://kagi.com/search?q=%s"),
        )
    }

    @Test
    fun `every occurrence of the marker is substituted`() {
        assertEquals(
            "https://x.com/s?q=cats&hl=cats",
            UrlInput.toLoadableUrl("cats", "https://x.com/s?q=%s&hl=%s"),
        )
    }

    @Test
    fun `append-style built-in prefixes are unchanged`() {
        assertEquals(
            SearchEngine.GOOGLE.queryUrl + "hello+world",
            UrlInput.toLoadableUrl("hello world", SearchEngine.GOOGLE.queryUrl),
        )
    }
}
