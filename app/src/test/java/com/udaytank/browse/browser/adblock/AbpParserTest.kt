package com.udaytank.browse.browser.adblock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AbpParserTest {

    private fun single(line: String): NetworkRule =
        AbpParser.parse(line).network.single()

    private fun none(line: String) {
        val parsed = AbpParser.parse(line)
        assertTrue("expected '$line' to produce no rules", parsed.network.isEmpty())
        assertTrue(parsed.cosmetic.isEmpty())
    }

    // ------------------------------------------------------------ comments & noise

    @Test
    fun `comments headers and blank lines are ignored`() {
        val parsed = AbpParser.parse("[Adblock Plus 2.0]\n! comment\n\n||tracker.io^")
        assertEquals(1, parsed.network.size)
        assertEquals("tracker.io^", parsed.network[0].pattern)
    }

    // ------------------------------------------------------------ hosts format

    @Test
    fun `hosts-format lines become domain-anchored rules`() {
        val parsed = AbpParser.parse(
            "0.0.0.0 ads.example.com\n127.0.0.1 tracker.io\n::1 bad.host.net"
        )
        assertEquals(3, parsed.network.size)
        parsed.network.forEach { rule ->
            assertTrue(rule.domainAnchor)
            assertFalse(rule.isException)
            assertTrue(rule.pattern.endsWith("^"))
        }
        assertEquals("ads.example.com^", parsed.network[0].pattern)
    }

    @Test
    fun `hosts-format localhost entries are skipped`() {
        none("127.0.0.1 localhost")
        none("0.0.0.0 broadcasthost")
        none("::1 localhost")
        none("127.0.0.1 localhost.localdomain")
    }

    @Test
    fun `hosts-format trailing comments and multiple hosts are handled`() {
        val parsed = AbpParser.parse("0.0.0.0 ad1.example.com ad2.example.com # my comment")
        assertEquals(listOf("ad1.example.com^", "ad2.example.com^"), parsed.network.map { it.pattern })
    }

    // ------------------------------------------------------------ network anchors

    @Test
    fun `domain anchor and exception flags are parsed`() {
        val block = single("||doubleclick.net^")
        assertTrue(block.domainAnchor)
        assertFalse(block.isException)
        assertEquals("doubleclick.net^", block.pattern)

        val exception = single("@@||good.example.com^")
        assertTrue(exception.isException)
        assertTrue(exception.domainAnchor)
    }

    @Test
    fun `start and end anchors are parsed`() {
        val start = single("|https://exact.example.com/")
        assertTrue(start.startAnchor)
        assertFalse(start.domainAnchor)

        val end = single("banner.gif|")
        assertTrue(end.endAnchor)

        val both = single("|https://a.com/pixel.gif|")
        assertTrue(both.startAnchor)
        assertTrue(both.endAnchor)
        assertEquals("https://a.com/pixel.gif", both.pattern)
    }

    // ------------------------------------------------------------ options

    @Test
    fun `type third-party and domain options combine`() {
        val rule = single("||ads.example.com^\$script,third-party,domain=news.com|~sports.news.com")
        assertEquals(typeBit(ResourceType.SCRIPT), rule.includeTypes)
        assertEquals(true, rule.thirdParty)
        assertEquals(listOf("news.com"), rule.includeDomains)
        assertEquals(listOf("sports.news.com"), rule.excludeDomains)
    }

    @Test
    fun `type aliases map correctly`() {
        assertEquals(typeBit(ResourceType.STYLESHEET), single("||a.com^\$css").includeTypes)
        assertEquals(typeBit(ResourceType.XHR), single("||a.com^\$xhr").includeTypes)
        assertEquals(typeBit(ResourceType.SUBDOCUMENT), single("||a.com^\$frame").includeTypes)
        assertEquals(typeBit(ResourceType.OTHER), single("||a.com^\$object").includeTypes)
        assertEquals(
            typeBit(ResourceType.IMAGE) or typeBit(ResourceType.MEDIA) or typeBit(ResourceType.FONT),
            single("||a.com^\$image,media,font").includeTypes,
        )
    }

    @Test
    fun `negated types go to excludeTypes`() {
        val rule = single("||a.com^\$~script,~image")
        assertEquals(0, rule.includeTypes)
        assertEquals(typeBit(ResourceType.SCRIPT) or typeBit(ResourceType.IMAGE), rule.excludeTypes)
    }

    @Test
    fun `first-party aliases set thirdParty false`() {
        assertEquals(false, single("||a.com^\$first-party").thirdParty)
        assertEquals(false, single("||a.com^\$1p").thirdParty)
        assertEquals(false, single("||a.com^\$~third-party").thirdParty)
        assertEquals(true, single("||a.com^\$3p").thirdParty)
        assertNull(single("||a.com^").thirdParty)
    }

    @Test
    fun `match-case and important are tolerated`() {
        assertEquals("a.com^", single("||a.com^\$match-case").pattern)
        assertEquals("a.com^", single("||a.com^\$important").pattern)
    }

    // ------------------------------------------------------------ skip cases

    @Test
    fun `unsupported and unknown options skip the whole rule`() {
        none("||a.com^\$popup")
        none("||a.com^\$csp=script-src")
        none("||a.com^\$redirect=noopjs")
        none("||a.com^\$redirect-rule=noopjs")
        none("||a.com^\$removeparam")
        none("||a.com^\$removeparam=utm_source")
        none("||a.com^\$replace=/x/y/")
        none("||a.com^\$header=set-cookie")
        none("||a.com^\$denyallow=good.com")
        none("||a.com^\$method=get")
        none("||a.com^\$to=evil.com")
        none("||a.com^\$from=page.com")
        none("||a.com^\$websocket")
        none("||a.com^\$totally-made-up-option")
        none("||a.com^\$badfilter")
    }

    @Test
    fun `regex rules are skipped`() {
        none("/banner[0-9]+/")
        none("@@/adv?ert/")
    }

    @Test
    fun `pure document block rules are skipped but document exceptions kept`() {
        none("||a.com^\$document")
        none("||a.com^\$doc")
        val exception = single("@@||trusted.com^\$document")
        assertTrue(exception.isException)
        assertEquals(typeBit(ResourceType.DOCUMENT), exception.includeTypes)
    }

    @Test
    fun `document combined with other types is kept for blocks`() {
        val rule = single("||a.com^\$document,script")
        assertEquals(typeBit(ResourceType.DOCUMENT) or typeBit(ResourceType.SCRIPT), rule.includeTypes)
    }

    @Test
    fun `dollar in path is not treated as options when tail is not option-like`() {
        val rule = single("||ex.com/api\$/price")
        assertEquals("ex.com/api\$/price", rule.pattern)
        assertEquals(0, rule.includeTypes)
        assertNull(rule.thirdParty)
    }

    @Test
    fun `options split happens on the last unescaped dollar`() {
        val rule = single("||ex.com/a\$b\$script")
        assertEquals("ex.com/a\$b", rule.pattern)
        assertEquals(typeBit(ResourceType.SCRIPT), rule.includeTypes)
    }

    @Test
    fun `unconstrained match-everything patterns are skipped`() {
        none("*")
        none("*\$third-party")
        // But constrained ones survive:
        assertEquals(typeBit(ResourceType.SCRIPT), single("*\$script,domain=bad.com").includeTypes)
    }

    @Test
    fun `wildcard domains in domain option skip the rule`() {
        none("||a.com^\$domain=example.*")
    }

    // ------------------------------------------------------------ cosmetic rules

    @Test
    fun `generic and domain-scoped cosmetic rules are parsed`() {
        val parsed = AbpParser.parse("##.ad-banner\nexample.com,~m.example.com##.promo")
        assertEquals(2, parsed.cosmetic.size)
        val generic = parsed.cosmetic[0]
        assertEquals(".ad-banner", generic.selector)
        assertTrue(generic.includeDomains.isEmpty())
        assertFalse(generic.isException)
        val scoped = parsed.cosmetic[1]
        assertEquals(listOf("example.com"), scoped.includeDomains)
        assertEquals(listOf("m.example.com"), scoped.excludeDomains)
    }

    @Test
    fun `cosmetic exceptions are parsed`() {
        val parsed = AbpParser.parse("example.com#@#.ok-banner\n#@#.everywhere-ok")
        assertTrue(parsed.cosmetic.all { it.isException })
        assertEquals(listOf("example.com"), parsed.cosmetic[0].includeDomains)
        assertTrue(parsed.cosmetic[1].includeDomains.isEmpty())
    }

    @Test
    fun `procedural snippet and scriptlet cosmetics are skipped`() {
        none("example.com#?#div:-abp-has(.ad)")
        none("example.com#\$#abort-on-property-read alert")
        none("example.com#%#window.ads=false")
        none("example.com##+js(nowebrtc)")
        none("##.ad:has(> img)")
        none("##div:xpath(//div)")
        none("##.x:style(position: absolute)")
        none("##.x { display: none }")
    }

    @Test
    fun `overlong selectors are skipped`() {
        none("##." + "x".repeat(260))
    }

    @Test
    fun `wildcard cosmetic domains skip the rule`() {
        none("example.*##.ad")
    }

    // ------------------------------------------------------------ bundled list smoke test

    @Test
    fun `bundled easylist parses and keeps the bulk of its rules`() {
        val file = File("src/main/assets/adblock/easylist.txt")
        if (!file.exists()) return // running outside the module dir; covered on CI/local
        val text = file.readText()
        val totalCandidates = text.lineSequence()
            .map { it.trim() }
            .count { it.isNotEmpty() && !it.startsWith("!") && !it.startsWith("[") }
        val parsed = AbpParser.parse(text)
        val kept = parsed.network.size + parsed.cosmetic.size
        println(
            "easylist: $totalCandidates candidate lines -> ${parsed.network.size} network + " +
                "${parsed.cosmetic.size} cosmetic rules kept (${totalCandidates - kept} skipped)"
        )
        assertTrue("kept $kept of $totalCandidates", kept > totalCandidates / 2)
        assertNotEquals(0, parsed.network.size)
        assertNotEquals(0, parsed.cosmetic.size)
    }

    @Test
    fun `bundled annoyance list parses`() {
        val file = File("src/main/assets/adblock/annoyance-cookies.txt")
        if (!file.exists()) return
        val text = file.readText()
        val parsed = AbpParser.parse(text)
        println(
            "annoyance: ${parsed.network.size} network + ${parsed.cosmetic.size} cosmetic rules kept"
        )
        assertTrue(parsed.cosmetic.isNotEmpty())
    }
}
