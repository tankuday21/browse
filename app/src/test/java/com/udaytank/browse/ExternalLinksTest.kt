package com.udaytank.browse

import com.udaytank.browse.browser.ExternalLinks
import com.udaytank.browse.browser.ExternalLinks.LinkAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalLinksTest {

    // --- classify: in-page schemes ---

    @Test
    fun `http and https load in page`() {
        assertEquals(LinkAction.LoadInPage, ExternalLinks.classify("https://example.com"))
        assertEquals(LinkAction.LoadInPage, ExternalLinks.classify("http://example.com"))
    }

    @Test
    fun `engine-internal schemes load in page`() {
        assertEquals(LinkAction.LoadInPage, ExternalLinks.classify("about:blank"))
        assertEquals(LinkAction.LoadInPage, ExternalLinks.classify("blob:https://x/abc"))
        assertEquals(LinkAction.LoadInPage, ExternalLinks.classify("data:text/html,hi"))
    }

    @Test
    fun `schemeless input loads in page`() {
        assertEquals(LinkAction.LoadInPage, ExternalLinks.classify("example.com/path"))
        assertEquals(LinkAction.LoadInPage, ExternalLinks.classify(":oddball"))
    }

    @Test
    fun `non-scheme colon shapes load in page`() {
        // A leading digit can't start a scheme (RFC 3986) — goes back to the engine. Note
        // "example.com:8080" is NOT here: dotted schemes are syntactically valid (reverse-DNS
        // custom schemes like com.example.app: exist), and shouldOverrideUrlLoading only ever
        // hands us absolute URIs anyway.
        assertEquals(LinkAction.LoadInPage, ExternalLinks.classify("1weird:thing"))
        assertEquals(LinkAction.LoadInPage, ExternalLinks.classify("-dash:thing"))
    }

    // --- classify: unsafe schemes ---

    @Test
    fun `unsafe schemes are always ignored`() {
        assertEquals(LinkAction.Ignore, ExternalLinks.classify("file:///etc/hosts"))
        assertEquals(LinkAction.Ignore, ExternalLinks.classify("content://com.app/secret"))
        assertEquals(LinkAction.Ignore, ExternalLinks.classify("javascript:alert(1)"))
    }

    // --- classify: external schemes ---

    @Test
    fun `external schemes map to OpenApp`() {
        assertEquals(LinkAction.OpenApp("mailto:hi@example.com"), ExternalLinks.classify("mailto:hi@example.com"))
        assertEquals(LinkAction.OpenApp("tel:+123"), ExternalLinks.classify("tel:+123"))
        assertEquals(LinkAction.OpenApp("upi://pay?pa=x@bank"), ExternalLinks.classify("upi://pay?pa=x@bank"))
        assertEquals(LinkAction.OpenApp("market://details?id=app"), ExternalLinks.classify("market://details?id=app"))
    }

    @Test
    fun `intent scheme gets its own action`() {
        assertEquals(
            LinkAction.IntentUri("intent://scan/#Intent;scheme=zxing;end"),
            ExternalLinks.classify("intent://scan/#Intent;scheme=zxing;end"),
        )
    }

    @Test
    fun `scheme matching is case-insensitive`() {
        assertEquals(LinkAction.LoadInPage, ExternalLinks.classify("HTTPS://example.com"))
        assertEquals(LinkAction.Ignore, ExternalLinks.classify("JavaScript:alert(1)"))
        assertEquals(LinkAction.OpenApp("MAILTO:hi@example.com"), ExternalLinks.classify("MAILTO:hi@example.com"))
    }

    // --- needsConfirm ---

    @Test
    fun `gesture-backed normal tab launches without confirm`() {
        assertFalse(ExternalLinks.needsConfirm(hasGesture = true, incognito = false))
    }

    @Test
    fun `no gesture always confirms`() {
        // Never a silent swallow (breaks tap→302→upi payment handoffs) and never an
        // auto-launch (redirect hijack) — the user decides.
        assertTrue(ExternalLinks.needsConfirm(hasGesture = false, incognito = false))
        assertTrue(ExternalLinks.needsConfirm(hasGesture = false, incognito = true))
    }

    @Test
    fun `incognito always confirms even with a gesture`() {
        assertTrue(ExternalLinks.needsConfirm(hasGesture = true, incognito = true))
    }

    // --- safeFallbackUrl ---

    @Test
    fun `http and https fallbacks pass`() {
        assertEquals("https://play.google.com/x", ExternalLinks.safeFallbackUrl("https://play.google.com/x"))
        assertEquals("http://example.com", ExternalLinks.safeFallbackUrl(" http://example.com "))
    }

    @Test
    fun `non-http fallbacks are rejected`() {
        assertNull(ExternalLinks.safeFallbackUrl("intent://again#Intent;end"))
        assertNull(ExternalLinks.safeFallbackUrl("javascript:alert(1)"))
        assertNull(ExternalLinks.safeFallbackUrl("file:///etc/hosts"))
        assertNull(ExternalLinks.safeFallbackUrl(""))
        assertNull(ExternalLinks.safeFallbackUrl(null))
        assertNull(ExternalLinks.safeFallbackUrl("https:/missing-slash"))
    }
}
