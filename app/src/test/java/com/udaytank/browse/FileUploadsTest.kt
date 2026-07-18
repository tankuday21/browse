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

    // --- captureMode (v5.3) ---

    @Test
    fun `no camera permission means no camera regardless of capture`() {
        assertEquals(
            FileUploads.CaptureMode.None,
            FileUploads.captureMode(emptyList(), captureEnabled = true, cameraAvailable = false),
        )
    }

    @Test
    fun `non-image accepts get no camera`() {
        assertEquals(
            FileUploads.CaptureMode.None,
            FileUploads.captureMode(listOf("application/pdf"), captureEnabled = true, cameraAvailable = true),
        )
    }

    @Test
    fun `capture attribute with images opens the camera directly`() {
        assertEquals(
            FileUploads.CaptureMode.Direct,
            FileUploads.captureMode(listOf("image/*"), captureEnabled = true, cameraAvailable = true),
        )
        assertEquals(
            FileUploads.CaptureMode.Direct,
            FileUploads.captureMode(emptyList(), captureEnabled = true, cameraAvailable = true),
        )
    }

    @Test
    fun `image accepts without capture offer the camera in the chooser`() {
        assertEquals(
            FileUploads.CaptureMode.Offer,
            FileUploads.captureMode(listOf("image/png"), captureEnabled = false, cameraAvailable = true),
        )
        assertEquals(
            FileUploads.CaptureMode.Offer,
            FileUploads.captureMode(emptyList(), captureEnabled = false, cameraAvailable = true),
        )
        assertEquals(
            FileUploads.CaptureMode.Offer,
            FileUploads.captureMode(listOf("application/pdf", "image/jpeg"), captureEnabled = false, cameraAvailable = true),
        )
    }

    // --- resolveUploadResult (v5.3) ---

    @Test
    fun `picker result wins over capture`() {
        assertEquals(
            listOf("picked"),
            FileUploads.resolveUploadResult(listOf("picked"), capture = "cap", captureHasData = true),
        )
    }

    @Test
    fun `camera fallback applies only when the capture file has data`() {
        assertEquals(
            listOf("cap"),
            FileUploads.resolveUploadResult(picked = null, capture = "cap", captureHasData = true),
        )
        assertNull(FileUploads.resolveUploadResult(picked = null, capture = "cap", captureHasData = false))
        assertNull(FileUploads.resolveUploadResult<String>(picked = null, capture = null, captureHasData = true))
    }
}
