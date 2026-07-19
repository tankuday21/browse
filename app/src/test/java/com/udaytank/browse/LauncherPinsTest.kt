package com.udaytank.browse

import com.udaytank.browse.browser.LauncherPins
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherPinsTest {

    @Test
    fun `meaningful title is used and clamped`() {
        assertEquals(
            "Andromeda Docs",
            LauncherPins.shortcutLabel("Andromeda Docs", "https://example.com/docs", "example.com"),
        )
        val long = LauncherPins.shortcutLabel(
            "A very long page title that would overflow every launcher grid",
            "https://example.com", "example.com",
        )
        assertTrue(long.length <= 24)
        assertTrue(long.endsWith("…"))
    }

    @Test
    fun `url-echoed or host-echoed titles fall back to the host`() {
        assertEquals(
            "example.com",
            LauncherPins.shortcutLabel("https://example.com/docs", "https://example.com/docs", "example.com"),
        )
        assertEquals(
            "example.com",
            LauncherPins.shortcutLabel("EXAMPLE.COM", "https://example.com", "example.com"),
        )
    }

    @Test
    fun `blank title falls back to host without www`() {
        assertEquals(
            "example.com",
            LauncherPins.shortcutLabel("  ", "https://www.example.com", "www.example.com"),
        )
        assertEquals(
            "example.com",
            LauncherPins.shortcutLabel(null, "https://www.example.com", "www.example.com"),
        )
    }
}
