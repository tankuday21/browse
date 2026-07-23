package com.udaytank.browse.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CredentialHostMatchTest {

    // --- registrableDomain ---

    @Test
    fun `simple two-label host is its own registrable domain`() {
        assertEquals("example.com", CredentialHostMatch.registrableDomain("example.com"))
    }

    @Test
    fun `subdomain reduces to registrable domain`() {
        assertEquals("example.com", CredentialHostMatch.registrableDomain("login.example.com"))
        assertEquals("example.com", CredentialHostMatch.registrableDomain("a.b.c.example.com"))
    }

    @Test
    fun `multi-label public suffix keeps one label in front`() {
        assertEquals("example.co.uk", CredentialHostMatch.registrableDomain("example.co.uk"))
        assertEquals("example.co.uk", CredentialHostMatch.registrableDomain("login.example.co.uk"))
        assertEquals("bbc.co.uk", CredentialHostMatch.registrableDomain("www.bbc.co.uk"))
    }

    @Test
    fun `bare public suffix has no registrable domain`() {
        assertNull(CredentialHostMatch.registrableDomain("co.uk"))
        assertNull(CredentialHostMatch.registrableDomain("com"))
    }

    @Test
    fun `single-label host has no registrable domain`() {
        assertNull(CredentialHostMatch.registrableDomain("localhost"))
    }

    @Test
    fun `blank and null have no registrable domain`() {
        assertNull(CredentialHostMatch.registrableDomain(null))
        assertNull(CredentialHostMatch.registrableDomain(""))
        assertNull(CredentialHostMatch.registrableDomain("   "))
    }

    @Test
    fun `ip literals have no registrable domain`() {
        assertNull(CredentialHostMatch.registrableDomain("192.168.0.1"))
        assertNull(CredentialHostMatch.registrableDomain("10.0.0.255"))
        assertNull(CredentialHostMatch.registrableDomain("[2001:db8::1]"))
        assertNull(CredentialHostMatch.registrableDomain("fe80::1"))
    }

    @Test
    fun `trailing dot and mixed case normalize`() {
        assertEquals("example.com", CredentialHostMatch.registrableDomain("Login.Example.Com."))
    }

    @Test
    fun `numeric-looking host that is not an ip still resolves`() {
        // Four parts but a segment out of range → not an IP, treat as a domain.
        assertEquals("0.999", CredentialHostMatch.registrableDomain("1.0.999"))
    }

    // --- sameSite: the security-critical cases ---

    @Test
    fun `bare domain and subdomain are same site both directions`() {
        assertTrue(CredentialHostMatch.sameSite("example.com", "login.example.com"))
        assertTrue(CredentialHostMatch.sameSite("login.example.com", "example.com"))
        assertTrue(CredentialHostMatch.sameSite("a.example.com", "b.example.com"))
    }

    @Test
    fun `suffix-append attack does not match`() {
        assertFalse(CredentialHostMatch.sameSite("example.com", "example.com.evil.net"))
    }

    @Test
    fun `lookalike prefixes do not match`() {
        assertFalse(CredentialHostMatch.sameSite("example.com", "notexample.com"))
        assertFalse(CredentialHostMatch.sameSite("example.com", "evilexample.com"))
    }

    @Test
    fun `different sites under a multi-label suffix do not match`() {
        assertFalse(CredentialHostMatch.sameSite("a.co.uk", "b.co.uk"))
        assertTrue(CredentialHostMatch.sameSite("x.a.co.uk", "a.co.uk"))
    }

    @Test
    fun `different owners on a site-hosting suffix do not match`() {
        assertFalse(CredentialHostMatch.sameSite("alice.github.io", "bob.github.io"))
        assertTrue(CredentialHostMatch.sameSite("alice.github.io", "www.alice.github.io"))
    }

    @Test
    fun `ip literals never match anything`() {
        assertFalse(CredentialHostMatch.sameSite("192.168.0.1", "192.168.0.1"))
        assertFalse(CredentialHostMatch.sameSite("example.com", "192.168.0.1"))
    }

    // --- rankHosts ---

    @Test
    fun `rankHosts puts exact host first and drops non-matches`() {
        val ranked = CredentialHostMatch.rankHosts(
            "login.example.com",
            listOf("example.com", "login.example.com", "other.org", "www.example.com"),
        )
        assertEquals(listOf("login.example.com", "example.com", "www.example.com"), ranked)
    }

    @Test
    fun `rankHosts keeps stable order within the non-exact tier`() {
        val ranked = CredentialHostMatch.rankHosts(
            "example.com",
            listOf("b.example.com", "a.example.com"),
        )
        assertEquals(listOf("b.example.com", "a.example.com"), ranked)
    }

    @Test
    fun `rankHosts returns empty when nothing matches`() {
        assertTrue(CredentialHostMatch.rankHosts("example.com", listOf("evil.net", "co.uk")).isEmpty())
    }
}
