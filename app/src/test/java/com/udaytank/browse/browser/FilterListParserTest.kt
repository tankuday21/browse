package com.udaytank.browse.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterListParserTest {

    @Test
    fun `domain anchor rules are collected`() {
        val list = FilterListParser.parse("||doubleclick.net^\n||ads.example.com^")
        assertEquals(setOf("doubleclick.net", "ads.example.com"), list.blockedDomains)
    }

    @Test
    fun `exception rules are collected separately`() {
        val list = FilterListParser.parse("||ads.example.com^\n@@||good-ads.example.com^")
        assertTrue("ads.example.com" in list.blockedDomains)
        assertTrue("good-ads.example.com" in list.allowedDomains)
        assertFalse("good-ads.example.com" in list.blockedDomains)
    }

    @Test
    fun `comments headers and blank lines are ignored`() {
        val list = FilterListParser.parse("[Adblock Plus 2.0]\n! comment\n\n||tracker.io^")
        assertEquals(setOf("tracker.io"), list.blockedDomains)
    }

    @Test
    fun `generic cosmetic rules are collected, domain-specific ones skipped`() {
        val list = FilterListParser.parse("example.com##.ad-banner\n##.adsbox\n||real.ads^")
        assertEquals(setOf("real.ads"), list.blockedDomains)
        assertTrue(".adsbox" in list.cosmeticSelectors)
        assertFalse(".ad-banner" in list.cosmeticSelectors) // domain-specific, skipped in v1
    }

    @Test
    fun `procedural cosmetic selectors are rejected`() {
        val list = FilterListParser.parse("##.ad:has(> img)\n##div:-abp-contains(ad)\n##.clean")
        assertEquals(setOf(".clean"), list.cosmeticSelectors)
    }

    @Test
    fun `rules with paths or options are skipped in v1`() {
        val list = FilterListParser.parse(
            "||example.com/ads/banner\n||example.com^\$third-party\n||plain.com^"
        )
        assertEquals(setOf("plain.com"), list.blockedDomains)
    }

    @Test
    fun `domains are lowercased and validated`() {
        val list = FilterListParser.parse("||AdServer.COM^\n||not_a domain^\n||nodot^")
        assertEquals(setOf("adserver.com"), list.blockedDomains)
    }

    @Test
    fun `rule without trailing caret still counts as a domain`() {
        val list = FilterListParser.parse("||ads.example.org")
        assertEquals(setOf("ads.example.org"), list.blockedDomains)
    }
}
