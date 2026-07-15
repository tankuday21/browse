package com.udaytank.browse.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class OrbitTokensTest {
    @Test fun darkSurfacesAreDistinctLayers() {
        val s = darkOrbit.surfaces
        assertEquals(Color(0xFF070716), s.base)
        assertEquals(Color(0xFF111228), s.surface)
        assertEquals(Color(0xFF1A1B3C), s.elevated)
        // each layer must differ so nothing is flat-on-flat
        assertEquals(3, setOf(s.base, s.surface, s.elevated).size)
    }
    @Test fun accentGradientIsThreeStopBlueVioletCyan() {
        assertEquals(
            listOf(Color(0xFF2C5BE6), Color(0xFF7A5CFF), Color(0xFF46D0F5)),
            darkOrbit.accent.gradient,
        )
    }
    @Test fun lightSchemeSharesAccentButInvertsText() {
        assertEquals(darkOrbit.accent.gradient, lightOrbit.accent.gradient)
        assert(lightOrbit.text.primary != darkOrbit.text.primary)
    }
    @Test fun displayAndBodyAreDistinctFamilies() {
        assert(Display != Body)
    }
}
