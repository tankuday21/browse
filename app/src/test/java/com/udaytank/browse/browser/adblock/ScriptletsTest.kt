package com.udaytank.browse.browser.adblock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScriptletsTest {

    @Test
    fun `youtube hosts get a non-empty scriptlet`() {
        assertTrue(Scriptlets.scriptFor("youtube.com").isNotEmpty())
        assertTrue(Scriptlets.scriptFor("www.youtube.com").isNotEmpty())
        assertTrue(Scriptlets.scriptFor("m.youtube.com").isNotEmpty())
        assertTrue(Scriptlets.scriptFor("music.youtube.com").isNotEmpty())
    }

    @Test
    fun `deep subdomains suffix-match youtube`() {
        assertTrue(Scriptlets.scriptFor("accounts.music.youtube.com").isNotEmpty())
    }

    @Test
    fun `host matching is case-insensitive`() {
        assertTrue(Scriptlets.scriptFor("WWW.YouTube.com").isNotEmpty())
    }

    @Test
    fun `unrelated and null hosts get nothing`() {
        assertEquals("", Scriptlets.scriptFor("example.com"))
        assertEquals("", Scriptlets.scriptFor("google.com"))
        assertEquals("", Scriptlets.scriptFor(null))
        assertEquals("", Scriptlets.scriptFor(""))
    }

    @Test
    fun `lookalike hosts get nothing`() {
        assertEquals("", Scriptlets.scriptFor("notyoutube.com"))
        assertEquals("", Scriptlets.scriptFor("youtube.com.evil.example"))
        assertEquals("", Scriptlets.scriptFor("myyoutube.com"))
    }

    @Test
    fun `youtube script carries the idempotence flag and both prune hooks`() {
        val script = Scriptlets.scriptFor("www.youtube.com")
        // Idempotence guard so double injection (document-start + fallback) is harmless.
        assertTrue(script.contains("__andromedaYt"))
        // Part A: JSON.parse hook and the fetch-path Response.json hook.
        assertTrue(script.contains("JSON.parse"))
        assertTrue(script.contains("Response.prototype.json"))
        // Runtime kill switch published by WebViewHolder at page start.
        assertTrue(script.contains("__andromedaAdblockOff"))
    }

    @Test
    fun `youtube script has no Kotlin interpolation or console output`() {
        val script = Scriptlets.YOUTUBE_SCRIPT
        // A '$' would mean raw-string interpolation silently altered the JS.
        assertTrue(!script.contains('$'))
        assertTrue(!script.contains("console."))
    }
}
