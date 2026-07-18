package com.udaytank.browse

import com.google.zxing.BinaryBitmap
import com.google.zxing.LuminanceSource
import com.google.zxing.MultiFormatReader
import com.google.zxing.common.BitMatrix
import com.google.zxing.common.HybridBinarizer
import com.udaytank.browse.browser.QrGenerate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QrGenerateTest {

    /** Adapts a BitMatrix into the luminance source the decoder expects (set module = black). */
    private class MatrixLuminanceSource(private val matrix: BitMatrix) :
        LuminanceSource(matrix.width, matrix.height) {
        override fun getRow(y: Int, row: ByteArray?): ByteArray {
            val out = row?.takeIf { it.size >= width } ?: ByteArray(width)
            for (x in 0 until width) out[x] = if (matrix.get(x, y)) 0 else 0xFF.toByte()
            return out
        }

        override fun getMatrix(): ByteArray {
            val out = ByteArray(width * height)
            for (y in 0 until height) for (x in 0 until width) {
                out[y * width + x] = if (matrix.get(x, y)) 0 else 0xFF.toByte()
            }
            return out
        }
    }

    private fun decode(matrix: BitMatrix): String =
        MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(MatrixLuminanceSource(matrix)))).text

    @Test
    fun `encode-decode round trip preserves the url`() {
        val url = "https://example.com/some/page?q=hello&lang=en#section-2"
        val matrix = QrGenerate.encode(url)!!
        assertEquals(url, decode(matrix))
    }

    @Test
    fun `long urls survive the round trip`() {
        val url = "https://example.com/search?" + (1..40).joinToString("&") { "param$it=value$it" }
        val matrix = QrGenerate.encode(url)!!
        assertEquals(url, decode(matrix))
    }

    @Test
    fun `requested size is honored as a minimum`() {
        val matrix = QrGenerate.encode("https://example.com", size = 300)!!
        assertTrue(matrix.width >= 300)
        assertTrue(matrix.height >= 300)
    }

    @Test
    fun `blank input yields null`() {
        assertNull(QrGenerate.encode(""))
        assertNull(QrGenerate.encode("   "))
    }

    @Test
    fun `over-capacity input yields null instead of throwing`() {
        // QR version 40 tops out around 3kB of bytes — 8kB cannot encode.
        assertNull(QrGenerate.encode("x".repeat(8000)))
    }

    @Test
    fun `the requested quiet zone is actually blank`() {
        // The margin is load-bearing for scannability: the exported PNG relies on margin=4
        // (no white card around it), so assert the border modules really are unset.
        val matrix = QrGenerate.encode("https://example.com", size = 1, margin = 4)!!
        for (i in 0 until matrix.width) {
            for (m in 0 until 4) {
                assertTrue(!matrix.get(i, m)) // top rows
                assertTrue(!matrix.get(i, matrix.height - 1 - m)) // bottom rows
                assertTrue(!matrix.get(m, i)) // left columns
                assertTrue(!matrix.get(matrix.width - 1 - m, i)) // right columns
            }
        }
        // And the margin-4 code still decodes — at a realistic pixel scale: a 1px-per-module
        // image with a wide white border falls below HybridBinarizer's adaptive threshold
        // minimum (40px blocks) and fails decode for reasons unrelated to the code itself.
        val scaled = QrGenerate.encode("https://example.com", size = 200, margin = 4)!!
        assertEquals("https://example.com", decode(scaled))
    }
}
