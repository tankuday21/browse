package com.udaytank.browse.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UrlHostsTest {

    @Test
    fun `valid https url returns lowercase host`() {
        assertEquals("bbc.com", UrlHosts.of("https://BBC.com/news"))
    }

    @Test
    fun `garbage input returns null`() {
        assertNull(UrlHosts.of("not a url at all"))
    }

    @Test
    fun `null input returns null`() {
        assertNull(UrlHosts.of(null))
    }
}
