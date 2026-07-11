package com.udaytank.browse.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpsUpgradeTest {

    @Test
    fun `http urls upgrade to https`() {
        assertEquals("https://example.com/x", HttpsUpgrade.upgrade("http://example.com/x"))
    }

    @Test
    fun `https urls do not upgrade`() {
        assertNull(HttpsUpgrade.upgrade("https://example.com"))
    }

    @Test
    fun `shouldUpgrade only when https-only is on and url is http`() {
        assertTrue(HttpsUpgrade.shouldUpgrade("http://a.com", httpsOnly = true))
        assertFalse(HttpsUpgrade.shouldUpgrade("http://a.com", httpsOnly = false))
        assertFalse(HttpsUpgrade.shouldUpgrade("https://a.com", httpsOnly = true))
    }
}
