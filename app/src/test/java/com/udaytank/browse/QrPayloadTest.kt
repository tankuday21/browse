package com.udaytank.browse

import com.udaytank.browse.browser.QrPayload
import com.udaytank.browse.browser.QrPayload.Payload
import org.junit.Assert.assertEquals
import org.junit.Test

class QrPayloadTest {

    @Test
    fun `http and https urls pass through as web`() {
        assertEquals(Payload.Web("https://example.com/x"), QrPayload.classify("https://example.com/x"))
        assertEquals(Payload.Web("HTTP://EXAMPLE.com"), QrPayload.classify("  HTTP://EXAMPLE.com  "))
    }

    @Test
    fun `bare domains upgrade to https`() {
        assertEquals(Payload.Web("https://example.com"), QrPayload.classify("example.com"))
        assertEquals(Payload.Web("https://example.com/menu?t=4"), QrPayload.classify("example.com/menu?t=4"))
        assertEquals(Payload.Web("https://example.com:8080/x"), QrPayload.classify("example.com:8080/x"))
    }

    @Test
    fun `app schemes dispatch as app`() {
        assertEquals(Payload.App("upi://pay?pa=x@bank"), QrPayload.classify("upi://pay?pa=x@bank"))
        assertEquals(Payload.App("mailto:hi@example.com"), QrPayload.classify("mailto:hi@example.com"))
        assertEquals(Payload.App("market://details?id=app"), QrPayload.classify("market://details?id=app"))
        assertEquals(
            Payload.App("intent://scan/#Intent;scheme=zxing;end"),
            QrPayload.classify("intent://scan/#Intent;scheme=zxing;end"),
        )
    }

    @Test
    fun `unsafe schemes are display-only text, never opened`() {
        assertEquals(Payload.Text("javascript:alert(1)"), QrPayload.classify("javascript:alert(1)"))
        assertEquals(Payload.Text("file:///etc/hosts"), QrPayload.classify("file:///etc/hosts"))
        assertEquals(Payload.Text("content://com.app/x"), QrPayload.classify("content://com.app/x"))
    }

    @Test
    fun `wifi and contact-card formats are text, not dead-end app dispatches`() {
        assertEquals(
            Payload.Text("WIFI:S:mynet;T:WPA;P:pass;;"),
            QrPayload.classify("WIFI:S:mynet;T:WPA;P:pass;;"),
        )
        assertEquals(
            Payload.Text("BEGIN:VCARD\nFN:Uday\nEND:VCARD"),
            QrPayload.classify("BEGIN:VCARD\nFN:Uday\nEND:VCARD"),
        )
        assertEquals(Payload.Text("MECARD:N:Uday;;"), QrPayload.classify("MECARD:N:Uday;;"))
    }

    @Test
    fun `plain text stays text`() {
        assertEquals(Payload.Text("hello world"), QrPayload.classify("hello world"))
        assertEquals(Payload.Text("just-a-token"), QrPayload.classify("just-a-token"))
        assertEquals(Payload.Text(""), QrPayload.classify("   "))
    }
}
