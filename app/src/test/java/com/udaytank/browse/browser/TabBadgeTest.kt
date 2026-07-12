package com.udaytank.browse.browser

import org.junit.Assert.assertEquals
import org.junit.Test

class TabBadgeTest {
    @Test fun `shows plain count below 100`() {
        assertEquals("1", TabBadge.label(1))
        assertEquals("99", TabBadge.label(99))
    }

    @Test fun `shows infinity at 100 and beyond`() {
        assertEquals("∞", TabBadge.label(100))
        assertEquals("∞", TabBadge.label(250))
    }

    @Test fun `zero and negatives clamp to 0`() {
        assertEquals("0", TabBadge.label(0))
        assertEquals("0", TabBadge.label(-3))
    }
}
