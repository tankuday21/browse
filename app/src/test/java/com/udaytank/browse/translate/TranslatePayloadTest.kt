package com.udaytank.browse.translate

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslatePayloadTest {

    @Test
    fun `parse collected reads the ordered strings`() {
        assertEquals(listOf("Hola", "mundo"), TranslatePayload.parseCollected("""["Hola","mundo"]"""))
    }

    @Test
    fun `parse collected is lenient - malformed yields empty`() {
        assertEquals(emptyList<String>(), TranslatePayload.parseCollected("not json"))
        assertEquals(emptyList<String>(), TranslatePayload.parseCollected(null))
        assertEquals(emptyList<String>(), TranslatePayload.parseCollected("{}"))
    }

    @Test
    fun `apply payload escapes quotes, backslashes, and newlines so the page parses it back exactly`() {
        val tricky = listOf("She said \"hi\"", "back\\slash", "line1\nline2")
        val payload = TranslatePayload.buildApplyPayload(tricky)
        // The payload must be valid JSON that round-trips to the exact originals.
        val parsed = JSONArray(payload)
        assertEquals(tricky.size, parsed.length())
        assertEquals("She said \"hi\"", parsed.getString(0))
        assertEquals("back\\slash", parsed.getString(1))
        assertEquals("line1\nline2", parsed.getString(2))
    }

    @Test
    fun `apply payload neutralizes angle brackets so a translated script tag cannot break out`() {
        val payload = TranslatePayload.buildApplyPayload(listOf("</script><b>x</b>"))
        assertFalse("raw < must not appear in the injected literal", payload.contains("<"))
        assertTrue(payload.contains("\\u003C"))
        // Still valid JSON that decodes back to the original text once the page parses it.
        assertEquals("</script><b>x</b>", JSONArray(payload).getString(0))
    }

    @Test
    fun `detection sample joins non-blank text and caps length`() {
        val sample = TranslatePayload.detectionSample(listOf("  ", "Bonjour", "le", "monde"), maxChars = 12)
        assertTrue(sample.length <= 12)
        assertTrue(sample.startsWith("Bonjour"))
    }
}
