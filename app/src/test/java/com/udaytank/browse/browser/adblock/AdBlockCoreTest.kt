package com.udaytank.browse.browser.adblock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdBlockCoreTest {

    private fun core(vararg lists: String): AdBlockCore =
        AdBlockCore(lists.map { AbpParser.parse(it) })

    private fun ctx(
        url: String,
        pageHost: String? = "news.com",
        type: ResourceType = ResourceType.OTHER,
        thirdParty: Boolean = true,
    ): RequestContext {
        val host = url.substringAfter("://").substringBefore('/').lowercase()
        return RequestContext(url, url.lowercase(), host, pageHost, type, thirdParty)
    }

    // ------------------------------------------------------------ network decisions

    @Test
    fun `matching block rule blocks`() {
        val c = core("||doubleclick.net^")
        assertTrue(c.decide(ctx("https://stats.doubleclick.net/px.gif")))
        assertFalse(c.decide(ctx("https://images.news.com/logo.png")))
    }

    @Test
    fun `exception always wins over a block`() {
        val c = core("||ads.example.com^\n@@||ads.example.com/acceptable/")
        assertTrue(c.decide(ctx("https://ads.example.com/banner.gif")))
        assertFalse(c.decide(ctx("https://ads.example.com/acceptable/one.gif")))
    }

    @Test
    fun `rules from multiple lists merge`() {
        val c = core("||list-one-ads.com^", "||list-two-ads.com^\n@@||list-one-ads.com/ok/")
        assertTrue(c.decide(ctx("https://list-one-ads.com/x")))
        assertTrue(c.decide(ctx("https://list-two-ads.com/x")))
        assertFalse(c.decide(ctx("https://list-one-ads.com/ok/x"))) // cross-list exception
    }

    @Test
    fun `document exception allowlists the whole page`() {
        val c = core("||ads.net^\n@@||trusted.com^\$document")
        assertTrue(c.decide(ctx("https://ads.net/x", pageHost = "news.com")))
        assertFalse(c.decide(ctx("https://ads.net/x", pageHost = "trusted.com")))
        assertFalse(c.decide(ctx("https://ads.net/x", pageHost = "shop.trusted.com")))
        assertTrue(c.isPageAllowlisted("trusted.com"))
        assertFalse(c.isPageAllowlisted("news.com"))
    }

    // ------------------------------------------------------------ cosmetics

    @Test
    fun `cssFor composes generic and host-specific selectors`() {
        val c = core("##.ad-generic\nnews.com##.news-promo\nother.com##.other-promo")
        val css = c.cssFor("news.com")
        assertTrue(".ad-generic" in css)
        assertTrue(".news-promo" in css)
        assertFalse(".other-promo" in css)
        // Subdomains inherit the parent's specific rules.
        assertTrue(".news-promo" in c.cssFor("m.news.com"))
        assertTrue(css.endsWith("{display:none!important;}"))
    }

    @Test
    fun `cosmetic exceptions remove selectors per host and globally`() {
        val c = core("##.ad\n##.tracker\nnews.com#@#.ad\n#@#.tracker")
        val onNews = c.cssFor("news.com")
        assertFalse(".ad," in onNews || onNews.contains(".ad{"))
        assertFalse(".tracker" in onNews)
        val elsewhere = c.cssFor("other.com")
        assertTrue(".ad" in elsewhere)
        assertFalse(".tracker" in elsewhere) // generic exception applies everywhere
    }

    @Test
    fun `excluded domains skip generic-with-exclusion rules`() {
        val c = core("~quiet.com##.ad")
        assertTrue(".ad" in c.cssFor("news.com"))
        assertEquals("", c.cssFor("quiet.com"))
        assertEquals("", c.cssFor("m.quiet.com"))
    }

    @Test
    fun `document exception silences cosmetics too`() {
        val c = core("##.ad\n@@||trusted.com^\$document")
        assertTrue(".ad" in c.cssFor("news.com"))
        assertEquals("", c.cssFor("trusted.com"))
    }

    @Test
    fun `selectors are chunked into groups of 500`() {
        val many = (1..501).joinToString("\n") { "##.ad-selector-$it" }
        val css = core(many).cssFor("news.com")
        assertEquals(2, Regex("\\{display:none!important;}").findAll(css).count())
        // No selector lost in the chunking:
        assertTrue(".ad-selector-1," in css || ".ad-selector-1{" in css)
        assertTrue(".ad-selector-501," in css || ".ad-selector-501{" in css)
    }

    @Test
    fun `cssFor is cached per host`() {
        val c = core("##.ad")
        assertTrue(c.cssFor("news.com") === c.cssFor("news.com"))
    }

    @Test
    fun `empty core neither blocks nor hides`() {
        assertFalse(AdBlockCore.EMPTY.decide(ctx("https://ads.net/x")))
        assertEquals("", AdBlockCore.EMPTY.cssFor("news.com"))
    }
}
