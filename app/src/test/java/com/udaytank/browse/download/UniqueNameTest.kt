package com.udaytank.browse.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UniqueNameTest {

    @Test
    fun `untaken name is returned as-is`() {
        val result = UniqueName.resolve("image.jpg") { false }
        assertEquals("image.jpg", result)
    }

    @Test
    fun `taken once appends (1) before the extension`() {
        val taken = setOf("image.jpg")
        val result = UniqueName.resolve("image.jpg") { it in taken }
        assertEquals("image (1).jpg", result)
    }

    @Test
    fun `taken twice appends (2)`() {
        val taken = setOf("image.jpg", "image (1).jpg")
        val result = UniqueName.resolve("image.jpg") { it in taken }
        assertEquals("image (2).jpg", result)
    }

    @Test
    fun `extensionless name still gets a suffix`() {
        val taken = setOf("README")
        val result = UniqueName.resolve("README") { it in taken }
        assertEquals("README (1)", result)
    }

    // Multi-dot naming choice: the extension is defined as everything after the LAST dot
    // (matching how Windows Explorer / most file managers treat "duplicate" names), so
    // "archive.tar.gz" -> "archive.tar (1).gz", not "archive (1).tar.gz".
    @Test
    fun `multi-dot name inserts suffix before the last extension only`() {
        val taken = setOf("archive.tar.gz")
        val result = UniqueName.resolve("archive.tar.gz") { it in taken }
        assertEquals("archive.tar (1).gz", result)
    }

    @Test
    fun `name already matching the (n) pattern still resolves`() {
        val taken = setOf("photo (3).png")
        val result = UniqueName.resolve("photo (3).png") { it in taken }
        assertEquals("photo (3) (1).png", result)
    }

    @Test
    fun `caps at 999 then falls back to a timestamp suffix`() {
        val taken = (1..999).map { "image ($it).jpg" }.toSet() + "image.jpg"
        val result = UniqueName.resolve("image.jpg") { it in taken }
        // Must still be unique (not one of the exhausted 1..999 candidates) and preserve the
        // extension; the fallback suffix (a millis timestamp) has far more than 3 digits.
        assertTrue(result.matches(Regex("""image \(\d{4,}\)\.jpg""")))
    }
}
