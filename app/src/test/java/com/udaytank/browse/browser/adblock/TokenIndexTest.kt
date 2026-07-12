package com.udaytank.browse.browser.adblock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TokenIndexTest {

    private fun rule(line: String): NetworkRule = AbpParser.parse(line).network.single()

    private fun ctx(url: String, type: ResourceType = ResourceType.OTHER): RequestContext {
        val host = url.substringAfter("://").substringBefore('/').lowercase()
        return RequestContext(url, url.lowercase(), host, "news.com", type, true)
    }

    // ------------------------------------------------------------ tokenization

    @Test
    fun `url tokens are lowercase runs of length at least 4`() {
        val tokens = TokenIndex.tokensOf("https://ads.example.com/big-banner.gif?x=1234")
        assertTrue("https" in tokens)
        assertTrue("example" in tokens)
        assertTrue("banner" in tokens)
        assertTrue("1234" in tokens)
        assertFalse("ads" in tokens) // too short
        assertFalse("gif" in tokens) // too short
        assertFalse("x" in tokens)
    }

    @Test
    fun `duplicate tokens are reported once`() {
        val tokens = TokenIndex.tokensOf("https://track.com/track/track.js")
        assertEquals(1, tokens.count { it == "track" })
    }

    // ------------------------------------------------------------ rule token choice

    @Test
    fun `longest boundary-safe token is chosen`() {
        assertEquals("doubleclick", TokenIndex.bestToken(rule("||stats.doubleclick.net^")))
        // "banner" bounded by '/' on both sides is usable even without anchors.
        assertEquals("banner", TokenIndex.bestToken(rule("/banner/img.")))
    }

    @Test
    fun `tokens touching a wildcard or an open pattern edge are unusable`() {
        // Unanchored bare word: could match mid-token ("megabanner") -> not indexable.
        assertNull(TokenIndex.bestToken(rule("banner")))
        // Left edge on a wildcard is unsafe, right edge open is unsafe.
        assertNull(TokenIndex.bestToken(rule("*banner")))
        // End anchor makes the right edge safe.
        assertEquals("banner", TokenIndex.bestToken(rule("/banner|")))
    }

    @Test
    fun `short-token rules fall into tailRules`() {
        val index = TokenIndex(listOf(rule("||t.co^"), rule("||ads.io^")))
        assertEquals(2, index.tailRules.size)
        assertEquals(0, index.indexedTokenCount)
    }

    // ------------------------------------------------------------ candidate retrieval

    @Test
    fun `candidates come from matching tokens plus the tail`() {
        val doubleclick = rule("||doubleclick.net^")
        val banner = rule("/bigbanner.")
        val shorty = rule("||t.co^")
        val index = TokenIndex(listOf(doubleclick, banner, shorty))

        val forDoubleclick = index.candidates("https://stats.doubleclick.net/px")
        assertTrue(doubleclick in forDoubleclick)
        assertTrue(shorty in forDoubleclick) // tail rules are always candidates
        assertFalse(banner in forDoubleclick)

        val forBanner = index.candidates("https://cdn.com/bigbanner.gif")
        assertTrue(banner in forBanner)
        assertFalse(doubleclick in forBanner)
    }

    @Test
    fun `findMatch returns a matching rule and respects context`() {
        val index = TokenIndex(listOf(rule("||doubleclick.net^\$script")))
        val url = "https://ad.doubleclick.net/lib.js"
        val tokens = TokenIndex.tokensOf(url)
        assertNotNull(index.findMatch(ctx(url, ResourceType.SCRIPT), tokens))
        assertNull(index.findMatch(ctx(url, ResourceType.IMAGE), tokens))
        assertNull(index.findMatch(ctx("https://clean.site.com/lib.js", ResourceType.SCRIPT),
            TokenIndex.tokensOf("https://clean.site.com/lib.js")))
    }

    @Test
    fun `tail rules still match urls that share no tokens with them`() {
        val index = TokenIndex(listOf(rule("||t.co^")))
        val url = "https://t.co/redirect"
        assertNotNull(index.findMatch(ctx(url), TokenIndex.tokensOf(url)))
    }
}
