package com.udaytank.browse.browser.adblock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RegistrableDomainTest {

    @Test
    fun `plain tld keeps last two labels`() {
        assertEquals("example.com", RegistrableDomain.of("example.com"))
        assertEquals("example.com", RegistrableDomain.of("www.example.com"))
        assertEquals("doubleclick.net", RegistrableDomain.of("stats.g.doubleclick.net"))
    }

    @Test
    fun `multi-part suffixes keep three labels`() {
        assertEquals("example.co.uk", RegistrableDomain.of("example.co.uk"))
        assertEquals("example.co.uk", RegistrableDomain.of("shop.example.co.uk"))
        assertEquals("example.com.au", RegistrableDomain.of("mail.example.com.au"))
        assertEquals("example.co.in", RegistrableDomain.of("a.b.example.co.in"))
        assertEquals("example.co.jp", RegistrableDomain.of("www.example.co.jp"))
    }

    @Test
    fun `short hosts are returned as-is`() {
        assertEquals("localhost", RegistrableDomain.of("localhost"))
        assertEquals("co.uk", RegistrableDomain.of("co.uk"))
    }

    @Test
    fun `third-party detection compares registrable domains`() {
        assertFalse(RegistrableDomain.isThirdParty("cdn.news.com", "news.com"))
        assertFalse(RegistrableDomain.isThirdParty("a.news.com", "b.news.com"))
        assertTrue(RegistrableDomain.isThirdParty("doubleclick.net", "news.com"))
        assertTrue(RegistrableDomain.isThirdParty("news.com.evil.org", "news.com"))
        assertFalse(RegistrableDomain.isThirdParty("shop.example.co.uk", "example.co.uk"))
        assertTrue(RegistrableDomain.isThirdParty("other.co.uk", "example.co.uk"))
    }

    @Test
    fun `unknown page host counts as third-party`() {
        assertTrue(RegistrableDomain.isThirdParty("ads.net", null))
        assertTrue(RegistrableDomain.isThirdParty("ads.net", ""))
    }
}
