package com.udaytank.browse.browser

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Renders text as a QR BitMatrix (v5.4) — the encoder half of v5.2's scanner, same bundled
 * ZXing, fully on-device. Pure Java types (no android.graphics), so the JVM test can do a full
 * encode→decode round trip with the exact reader the scanner uses.
 */
object QrGenerate {

    /**
     * [size] is the requested pixel edge; ZXing returns a matrix AT LEAST that big (it rounds
     * up to whole modules). [margin] is the quiet zone in modules: 1 suffices on-screen where
     * the sheet's white card supplies breathing room; EXPORTED images need the spec's 4 —
     * recipients view them against dark chat backgrounds where a thin border fails scans.
     * Null for blank input or encoder failure (e.g. over-capacity text) — the caller shows
     * nothing rather than a broken code.
     */
    fun encode(text: String, size: Int = 512, margin: Int = 1): BitMatrix? {
        if (text.isBlank()) return null
        return try {
            QRCodeWriter().encode(
                text,
                BarcodeFormat.QR_CODE,
                size,
                size,
                mapOf(
                    // M survives a dirty/curved phone screen.
                    EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                    EncodeHintType.MARGIN to margin,
                ),
            )
        } catch (_: WriterException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}
