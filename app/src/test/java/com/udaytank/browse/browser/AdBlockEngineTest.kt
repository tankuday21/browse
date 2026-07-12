package com.udaytank.browse.browser

import com.udaytank.browse.browser.adblock.AbpParser
import com.udaytank.browse.browser.adblock.ParsedList
import com.udaytank.browse.browser.adblock.ResourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdBlockEngineTest {

    private fun engine(rules: String = "||doubleclick.net^\n||ads.example.com^") =
        AdBlockEngine().apply { load(AbpParser.parse(rules)) }

    @Test
    fun `exact domain match blocks`() {
        assertTrue(engine().shouldBlock("doubleclick.net", pageHost = "news.com"))
    }

    @Test
    fun `subdomains of a blocked domain are blocked`() {
        assertTrue(engine().shouldBlock("stats.g.doubleclick.net", pageHost = "news.com"))
    }

    @Test
    fun `unrelated domains pass through`() {
        assertFalse(engine().shouldBlock("images.news.com", pageHost = "news.com"))
    }

    @Test
    fun `suffix that is not a subdomain does not match`() {
        // evildoubleclick.net is NOT a subdomain of doubleclick.net
        assertFalse(engine().shouldBlock("evildoubleclick.net", pageHost = "news.com"))
    }

    @Test
    fun `disabled engine blocks nothing`() {
        val e = engine().apply { updatePolicy(enabled = false, siteAllowlist = emptySet()) }
        assertFalse(e.shouldBlock("doubleclick.net", pageHost = "news.com"))
    }

    @Test
    fun `exception rules are never blocked`() {
        val e = engine("||example.com^\n@@||good.example.com^")
        assertTrue(e.shouldBlock("bad.example.com", pageHost = "news.com"))
        assertFalse(e.shouldBlock("good.example.com", pageHost = "news.com"))
    }

    @Test
    fun `site on the user allowlist gets all its requests through`() {
        val e = engine().apply { updatePolicy(enabled = true, siteAllowlist = setOf("news.com")) }
        assertFalse(e.shouldBlock("doubleclick.net", pageHost = "news.com"))
        assertFalse(e.shouldBlock("doubleclick.net", pageHost = "m.news.com"))
        assertTrue(e.shouldBlock("doubleclick.net", pageHost = "other.com"))
    }

    @Test
    fun `null hosts never block`() {
        assertFalse(engine().shouldBlock(null, pageHost = "news.com"))
        assertTrue(engine().shouldBlock("doubleclick.net", pageHost = null))
    }

    @Test
    fun `full-context overload blocks by url pattern and type`() {
        val e = engine("/adframe.\$script")
        assertTrue(
            e.shouldBlock(
                url = "https://cdn.site.com/js/adframe.js",
                requestHost = "cdn.site.com",
                pageHost = "site.com",
                type = ResourceType.SCRIPT,
                mainFrame = false,
            )
        )
        assertFalse(
            e.shouldBlock(
                url = "https://cdn.site.com/js/adframe.js",
                requestHost = "cdn.site.com",
                pageHost = "site.com",
                type = ResourceType.IMAGE,
                mainFrame = false,
            )
        )
    }

    @Test
    fun `main frame documents are never blocked`() {
        val e = engine()
        assertFalse(
            e.shouldBlock(
                url = "https://doubleclick.net/landing",
                requestHost = "doubleclick.net",
                pageHost = null,
                type = ResourceType.DOCUMENT,
                mainFrame = true,
            )
        )
    }

    @Test
    fun `third-party option is honored through the compat overload`() {
        // Compat overload computes party-ness from the two hosts (eTLD+1 comparison).
        val e = engine("||tracker.com^\$third-party")
        assertTrue(e.shouldBlock("tracker.com", pageHost = "news.com"))
        assertFalse(e.shouldBlock("cdn.tracker.com", pageHost = "tracker.com"))
    }

    @Test
    fun `multiple lists load together`() {
        val e = AdBlockEngine()
        e.load(
            listOf(
                AbpParser.parse("||ads-one.com^"),
                AbpParser.parse("||ads-two.com^"),
            )
        )
        assertTrue(e.shouldBlock("ads-one.com", pageHost = "news.com"))
        assertTrue(e.shouldBlock("ads-two.com", pageHost = "news.com"))
    }

    @Test
    fun `cosmetic script is host-specific and empty when allowlisted or disabled`() {
        val e = engine("##.ad-banner\nnews.com##.sticky-promo")
        val onNews = e.cosmeticInjectionScript("news.com")
        assertTrue(".ad-banner" in onNews)
        assertTrue(".sticky-promo" in onNews)
        val elsewhere = e.cosmeticInjectionScript("other.com")
        assertTrue(".ad-banner" in elsewhere)
        assertFalse(".sticky-promo" in elsewhere)

        e.updatePolicy(enabled = true, siteAllowlist = setOf("news.com"))
        assertEquals("", e.cosmeticInjectionScript("news.com"))
        assertEquals("", e.cosmeticInjectionScript("m.news.com"))

        e.updatePolicy(enabled = false, siteAllowlist = emptySet())
        assertEquals("", e.cosmeticInjectionScript("other.com"))
    }

    @Test
    fun `empty engine passes everything`() {
        val e = AdBlockEngine().apply { load(ParsedList(emptyList(), emptyList())) }
        assertFalse(e.shouldBlock("doubleclick.net", pageHost = "news.com"))
        assertEquals("", e.cosmeticInjectionScript("news.com"))
    }
}
