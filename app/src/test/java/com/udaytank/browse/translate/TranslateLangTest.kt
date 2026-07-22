package com.udaytank.browse.translate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslateLangTest {

    @Test
    fun `normalize strips region and lowercases`() {
        assertEquals("en", TranslateLang.normalize("en-US"))
        assertEquals("pt", TranslateLang.normalize("PT_BR"))
        assertEquals("", TranslateLang.normalize(null))
    }

    @Test
    fun `default target is the device language when supported, else English`() {
        assertEquals("es", TranslateLang.defaultTarget("es-ES"))
        assertEquals("hi", TranslateLang.defaultTarget("hi"))
        assertEquals("en", TranslateLang.defaultTarget("xx")) // unsupported
        assertEquals("en", TranslateLang.defaultTarget(null))
    }

    @Test
    fun `needsTranslation only when both supported and different`() {
        assertTrue(TranslateLang.needsTranslation("es", "en"))
        assertTrue(TranslateLang.needsTranslation("es-ES", "en-US")) // region-tagged
        assertFalse(TranslateLang.needsTranslation("en", "en")) // same
        assertFalse(TranslateLang.needsTranslation("und", "en")) // undetected
        assertFalse(TranslateLang.needsTranslation(null, "en")) // unknown
        assertFalse(TranslateLang.needsTranslation("xx", "en")) // unsupported source
        assertFalse(TranslateLang.needsTranslation("es", "zz")) // unsupported target
    }

    @Test
    fun `display name falls back to the code then Unknown`() {
        assertEquals("Spanish", TranslateLang.displayName("es-ES"))
        assertEquals("xx", TranslateLang.displayName("xx"))
        assertEquals("Unknown", TranslateLang.displayName(null))
    }
}
