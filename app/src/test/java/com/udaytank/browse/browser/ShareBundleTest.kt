package com.udaytank.browse.browser

import org.junit.Assert.assertEquals
import org.junit.Test

class ShareBundleTest {

    @Test
    fun `empty selection is the wildcard`() {
        assertEquals("*/*", ShareBundle.commonMimeType(emptyList()))
    }

    @Test
    fun `a single mime is preserved exactly`() {
        assertEquals("image/png", ShareBundle.commonMimeType(listOf("image/png")))
    }

    @Test
    fun `identical mimes collapse to that exact type`() {
        assertEquals("image/jpeg", ShareBundle.commonMimeType(listOf("image/jpeg", "image/jpeg")))
    }

    @Test
    fun `same top level differing subtype widens to the top-level wildcard`() {
        assertEquals("image/*", ShareBundle.commonMimeType(listOf("image/png", "image/jpeg")))
    }

    @Test
    fun `mixed top levels fall back to the full wildcard`() {
        assertEquals("*/*", ShareBundle.commonMimeType(listOf("image/png", "video/mp4")))
    }

    @Test
    fun `any null or blank entry forces the wildcard`() {
        assertEquals("*/*", ShareBundle.commonMimeType(listOf("image/png", null)))
        assertEquals("*/*", ShareBundle.commonMimeType(listOf("image/png", "  ")))
    }

    @Test
    fun `a malformed entry with no single slash forces the wildcard`() {
        assertEquals("*/*", ShareBundle.commonMimeType(listOf("image/png", "garbage")))
        assertEquals("*/*", ShareBundle.commonMimeType(listOf("application/x/y", "image/png")))
    }
}
