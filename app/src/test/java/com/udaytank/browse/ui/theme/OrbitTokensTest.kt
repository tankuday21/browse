package com.udaytank.browse.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class OrbitTokensTest {
    @Test fun darkSurfacesAreDistinctLayers() {
        val s = darkOrbit.surfaces
        assertEquals(Color(0xFF08081C), s.base)
        assertEquals(Color(0xFF12122E), s.surface)
        assertEquals(Color(0xFF181840), s.elevated)
        // each layer must differ so nothing is flat-on-flat
        assertEquals(3, setOf(s.base, s.surface, s.elevated).size)
    }
    @Test fun accentGradientIsBrandBlue() {
        assertEquals(listOf(Color(0xFF1E4FD8), Color(0xFF35C3F3)), darkOrbit.accent.gradient)
    }
    @Test fun lightSchemeSharesAccentButInvertsText() {
        assertEquals(darkOrbit.accent.gradient, lightOrbit.accent.gradient)
        assert(lightOrbit.text.primary != darkOrbit.text.primary)
    }
}
