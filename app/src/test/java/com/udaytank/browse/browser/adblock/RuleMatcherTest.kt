package com.udaytank.browse.browser.adblock

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleMatcherTest {

    private fun rule(line: String): NetworkRule = AbpParser.parse(line).network.single()

    private fun ctx(
        url: String,
        pageHost: String? = "news.com",
        type: ResourceType = ResourceType.OTHER,
        thirdParty: Boolean = true,
    ): RequestContext {
        val host = url.substringAfter("://").substringBefore('/').substringBefore(':').lowercase()
        return RequestContext(
            url = url,
            urlLower = url.lowercase(),
            requestHost = host,
            pageHost = pageHost,
            type = type,
            thirdParty = thirdParty,
        )
    }

    private fun matches(line: String, url: String, c: RequestContext = ctx(url)): Boolean =
        RuleMatcher.matches(rule(line), c)

    // ------------------------------------------------------------ anchors

    @Test
    fun `domain anchor matches the host and its subdomains`() {
        assertTrue(matches("||ads.example.com^", "https://ads.example.com/banner.gif"))
        assertTrue(matches("||ads.example.com^", "https://eu.ads.example.com/banner.gif"))
        assertTrue(matches("||example.com^", "http://sub.example.com/x"))
    }

    @Test
    fun `domain anchor rejects lookalike hosts`() {
        assertFalse(matches("||ads.example.com^", "https://evilads.example.com/banner.gif"))
        assertFalse(matches("||example.com^", "https://notexample.com/x"))
        assertFalse(matches("||ads.com^", "https://ads.community.net/x"))
    }

    @Test
    fun `domain anchor can continue into a path`() {
        assertTrue(matches("||example.com/tracker/", "https://example.com/tracker/px.gif"))
        assertFalse(matches("||example.com/tracker/", "https://example.com/blog/"))
    }

    @Test
    fun `start anchor pins the url prefix`() {
        assertTrue(matches("|https://exact.com/", "https://exact.com/anything"))
        assertFalse(matches("|https://exact.com/", "https://other.com/?u=https://exact.com/"))
    }

    @Test
    fun `end anchor pins the url suffix`() {
        assertTrue(matches("banner.gif|", "https://x.com/img/banner.gif"))
        assertFalse(matches("banner.gif|", "https://x.com/img/banner.gif?cache=1"))
    }

    // ------------------------------------------------------------ separators & wildcards

    @Test
    fun `caret matches separators including the end of the url`() {
        assertTrue(matches("||example.com^", "https://example.com/x")) // '/'
        assertTrue(matches("||example.com^", "https://example.com")) // end of url
        assertTrue(matches("||example.com^", "https://example.com:8080/x")) // ':'
        assertTrue(matches("swf^", "https://x.com/movie.swf?ad=1")) // '?'
        assertFalse(matches("||example.com^", "https://example.company/x")) // letter is no separator
        assertFalse(matches("swf^", "https://x.com/movie.swfx")) // 'x' is no separator
    }

    @Test
    fun `wildcards match any span`() {
        assertTrue(matches("/banner/*/ad.", "https://x.com/banner/2026/big/ad.png"))
        assertFalse(matches("/banner/*/ad.", "https://x.com/banner/ad-free.png"))
        assertTrue(matches("||cdn.com^*.gif", "https://cdn.com/a/b/c.gif"))
    }

    @Test
    fun `matching is case-insensitive via lowercased url`() {
        assertTrue(matches("/AdBanner.", "https://X.COM/adbanner.png".lowercase()))
        assertTrue(matches("/adbanner.", "https://x.com/AdBanner.png"))
    }

    // ------------------------------------------------------------ options

    @Test
    fun `domain option includes and excludes with subdomain chains`() {
        val line = "||ads.net^\$domain=news.com|~sports.news.com"
        val url = "https://ads.net/x"
        assertTrue(RuleMatcher.matches(rule(line), ctx(url, pageHost = "news.com")))
        assertTrue(RuleMatcher.matches(rule(line), ctx(url, pageHost = "m.news.com")))
        assertFalse(RuleMatcher.matches(rule(line), ctx(url, pageHost = "sports.news.com")))
        assertFalse(RuleMatcher.matches(rule(line), ctx(url, pageHost = "live.sports.news.com")))
        assertFalse(RuleMatcher.matches(rule(line), ctx(url, pageHost = "other.com")))
        assertFalse(RuleMatcher.matches(rule(line), ctx(url, pageHost = null)))
    }

    @Test
    fun `exclude-only domain option matches everywhere else`() {
        val line = "||ads.net^\$domain=~quiet.com"
        val url = "https://ads.net/x"
        assertTrue(RuleMatcher.matches(rule(line), ctx(url, pageHost = "news.com")))
        assertFalse(RuleMatcher.matches(rule(line), ctx(url, pageHost = "quiet.com")))
        assertFalse(RuleMatcher.matches(rule(line), ctx(url, pageHost = "m.quiet.com")))
    }

    @Test
    fun `third-party option both ways`() {
        val url = "https://ads.net/x"
        assertTrue(RuleMatcher.matches(rule("||ads.net^\$third-party"), ctx(url, thirdParty = true)))
        assertFalse(RuleMatcher.matches(rule("||ads.net^\$third-party"), ctx(url, thirdParty = false)))
        assertTrue(RuleMatcher.matches(rule("||ads.net^\$~third-party"), ctx(url, thirdParty = false)))
        assertFalse(RuleMatcher.matches(rule("||ads.net^\$~third-party"), ctx(url, thirdParty = true)))
    }

    @Test
    fun `type masks include and exclude`() {
        val url = "https://ads.net/lib.js"
        assertTrue(
            RuleMatcher.matches(rule("||ads.net^\$script"), ctx(url, type = ResourceType.SCRIPT))
        )
        assertFalse(
            RuleMatcher.matches(rule("||ads.net^\$script"), ctx(url, type = ResourceType.IMAGE))
        )
        assertFalse(
            RuleMatcher.matches(rule("||ads.net^\$~script"), ctx(url, type = ResourceType.SCRIPT))
        )
        assertTrue(
            RuleMatcher.matches(rule("||ads.net^\$~script"), ctx(url, type = ResourceType.IMAGE))
        )
        assertTrue(
            RuleMatcher.matches(
                rule("||ads.net^\$script,image"),
                ctx(url, type = ResourceType.IMAGE),
            )
        )
    }

    @Test
    fun `regex metacharacters in patterns are literal`() {
        assertTrue(matches("/ad?id=", "https://x.com/ad?id=7"))
        assertFalse(matches("/ad?id=", "https://x.com/aid=7")) // '?' must not become regex-optional
        assertTrue(matches("/a(b)+c", "https://x.com/a(b)+c/d"))
    }
}
