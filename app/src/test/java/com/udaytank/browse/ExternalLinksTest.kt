package com.udaytank.browse

import com.udaytank.browse.browser.ExternalLinks
import com.udaytank.browse.browser.ExternalLinks.LinkAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExternalLinksTest {

    // --- classify: in-page schemes ---

    @Test
    fun `http and https load in page`() {
        assertEquals(LinkAction.LoadInPage, ExternalLinks.classify("https://example.com", hasGesture = true))
        assertEquals(LinkAction.LoadInPage, ExternalLinks.classify("http://example.com", hasGesture = false))
    }

    @Test
    fun `engine-internal schemes load in page`() {
        assertEquals(LinkAction.LoadInPage, ExternalLinks.classify("about:blank", hasGesture = false))
        assertEquals(LinkAction.LoadInPage, ExternalLinks.classify("blob:https://x/abc", hasGesture = true))
        assertEquals(LinkAction.LoadInPage, ExternalLinks.classify("data:text/html,hi", hasGesture = true))
    }

    @Test
    fun `schemeless input loads in page`() {
        assertEquals(LinkAction.LoadInPage, ExternalLinks.classify("example.com/path", hasGesture = true))
        assertEquals(LinkAction.LoadInPage, ExternalLinks.classify(":oddball", hasGesture = true))
    }

    // --- classify: unsafe schemes ---

    @Test
    fun `unsafe schemes are ignored even with a gesture`() {
        assertEquals(LinkAction.Ignore, ExternalLinks.classify("file:///etc/hosts", hasGesture = true))
        assertEquals(LinkAction.Ignore, ExternalLinks.classify("content://com.app/secret", hasGesture = true))
        assertEquals(LinkAction.Ignore, ExternalLinks.classify("javascript:alert(1)", hasGesture = true))
    }

    // --- classify: external schemes ---

    @Test
    fun `external schemes open the app on a gesture`() {
        assertEquals(
            LinkAction.OpenApp("mailto:hi@example.com"),
            ExternalLinks.classify("mailto:hi@example.com", hasGesture = true),
        )
        assertEquals(LinkAction.OpenApp("tel:+123"), ExternalLinks.classify("tel:+123", hasGesture = true))
        assertEquals(
            LinkAction.OpenApp("upi://pay?pa=x@bank"),
            ExternalLinks.classify("upi://pay?pa=x@bank", hasGesture = true),
        )
        assertEquals(
            LinkAction.OpenApp("market://details?id=app"),
            ExternalLinks.classify("market://details?id=app", hasGesture = true),
        )
    }

    @Test
    fun `external schemes without a gesture are swallowed`() {
        assertEquals(LinkAction.Ignore, ExternalLinks.classify("mailto:hi@example.com", hasGesture = false))
        assertEquals(LinkAction.Ignore, ExternalLinks.classify("intent://x#Intent;end", hasGesture = false))
        assertEquals(LinkAction.Ignore, ExternalLinks.classify("upi://pay?pa=x@bank", hasGesture = false))
    }

    @Test
    fun `intent scheme gets its own action on a gesture`() {
        assertEquals(
            LinkAction.IntentUri("intent://scan/#Intent;scheme=zxing;end"),
            ExternalLinks.classify("intent://scan/#Intent;scheme=zxing;end", hasGesture = true),
        )
    }

    @Test
    fun `scheme matching is case-insensitive`() {
        assertEquals(LinkAction.LoadInPage, ExternalLinks.classify("HTTPS://example.com", hasGesture = true))
        assertEquals(LinkAction.Ignore, ExternalLinks.classify("JavaScript:alert(1)", hasGesture = true))
        assertEquals(
            LinkAction.OpenApp("MAILTO:hi@example.com"),
            ExternalLinks.classify("MAILTO:hi@example.com", hasGesture = true),
        )
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
