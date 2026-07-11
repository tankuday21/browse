package com.udaytank.browse.browser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdBlockEngineTest {

    private fun engine(
        blocked: Set<String> = setOf("doubleclick.net", "ads.example.com"),
        allowed: Set<String> = emptySet(),
    ) = AdBlockEngine().apply { load(BlockList(blocked, allowed)) }

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
    fun `easylist exception domains are never blocked`() {
        val e = engine(
            blocked = setOf("example.com"),
            allowed = setOf("good.example.com"),
        )
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
}
