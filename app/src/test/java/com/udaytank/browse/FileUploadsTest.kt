package com.udaytank.browse

import com.udaytank.browse.browser.FileUploads
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FileUploadsTest {

    private val mimeMap = mapOf(
        "jpg" to "image/jpeg",
        "jpeg" to "image/jpeg",
        "png" to "image/png",
        "pdf" to "application/pdf",
    )
    private val toMime: (String) -> String? = { mimeMap[it] }

    // --- normalizeAcceptTypes ---

    @Test
    fun `mime types pass through, extensions map`() {
        assertEquals(
            listOf("image/*", "application/pdf"),
            FileUploads.normalizeAcceptTypes(listOf("image/*", ".pdf"), toMime),
        )
    }

    @Test
    fun `comma-joined single entry is split`() {
        assertEquals(
            listOf("image/jpeg", "image/png"),
            FileUploads.normalizeAcceptTypes(listOf(".jpg, .png"), toMime),
        )
    }

    @Test
    fun `blanks are ignored`() {
        assertEquals(
            listOf("image/png"),
            FileUploads.normalizeAcceptTypes(listOf("", "  ", ".png"), toMime),
        )
    }

    @Test
    fun `any unmappable entry abandons the whole filter`() {
        // A partial filter would grey out exactly the type the page asked for (.dwg here) —
        // empty result means the caller opens an unfiltered picker instead.
        assertEquals(
            emptyList<String>(),
            FileUploads.normalizeAcceptTypes(listOf(".dwg", "image/png"), toMime),
        )
    }

    @Test
    fun `case folds and dedupes`() {
        assertEquals(
            listOf("image/jpeg"),
            FileUploads.normalizeAcceptTypes(listOf("IMAGE/JPEG", ".JPG", ".jpeg"), toMime),
        )
    }

    @Test
    fun `bare words without dot or slash abandon the filter`() {
        assertEquals(emptyList<String>(), FileUploads.normalizeAcceptTypes(listOf("jpg", ".png"), toMime))
    }

    @Test
    fun `empty accept list yields empty`() {
        assertEquals(emptyList<String>(), FileUploads.normalizeAcceptTypes(emptyList(), toMime))
    }

    // --- parseChooserResult ---

    @Test
    fun `cancel yields null even with data`() {
        assertNull(FileUploads.parseChooserResult(ok = false, single = "u1", clip = listOf("u2")))
    }

    @Test
    fun `single uri picked`() {
        assertEquals(listOf("u1"), FileUploads.parseChooserResult(ok = true, single = "u1", clip = emptyList()))
    }

    @Test
    fun `multi-select clip wins over single`() {
        assertEquals(
            listOf("a", "b"),
            FileUploads.parseChooserResult(ok = true, single = "x", clip = listOf("a", "b")),
        )
    }

    @Test
    fun `ok but nothing picked yields null`() {
        assertNull(FileUploads.parseChooserResult<String>(ok = true, single = null, clip = emptyList()))
    }
}
