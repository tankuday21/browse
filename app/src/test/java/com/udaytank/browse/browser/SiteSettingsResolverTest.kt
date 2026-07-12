package com.udaytank.browse.browser

import com.udaytank.browse.data.SiteSettingsEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SiteSettingsResolverTest {

    @Test
    fun `null override passes globals through with default zoom`() {
        val effective = SiteSettingsResolver.resolve(
            globalForceDark = true, globalDesktop = false, globalTextZoom = 100, override = null,
        )
        assertEquals(100, effective.textZoom)
        assertTrue(effective.forceDark)
        assertFalse(effective.desktopMode)
    }

    @Test
    fun `all-unset override behaves exactly like no override`() {
        val override = SiteSettingsEntity(host = "a.com") // all fields default to -1
        val effective = SiteSettingsResolver.resolve(
            globalForceDark = false, globalDesktop = true, globalTextZoom = 100, override = override,
        )
        assertEquals(100, effective.textZoom)
        assertFalse(effective.forceDark)
        assertTrue(effective.desktopMode)
    }

    @Test
    fun `explicit off beats global on`() {
        val override = SiteSettingsEntity(host = "a.com", forceDark = 0, desktopMode = 0)
        val effective = SiteSettingsResolver.resolve(
            globalForceDark = true, globalDesktop = true, globalTextZoom = 100, override = override,
        )
        assertFalse(effective.forceDark)
        assertFalse(effective.desktopMode)
    }

    @Test
    fun `explicit on beats global off`() {
        val override = SiteSettingsEntity(host = "a.com", forceDark = 1, desktopMode = 1)
        val effective = SiteSettingsResolver.resolve(
            globalForceDark = false, globalDesktop = false, globalTextZoom = 100, override = override,
        )
        assertTrue(effective.forceDark)
        assertTrue(effective.desktopMode)
    }

    @Test
    fun `unset text zoom falls back to the global text scale`() {
        val override = SiteSettingsEntity(host = "a.com", textZoom = -1)
        val effective = SiteSettingsResolver.resolve(
            globalForceDark = false, globalDesktop = false, globalTextZoom = 140, override = override,
        )
        assertEquals(140, effective.textZoom)
    }

    @Test
    fun `no override row also falls back to the global text scale`() {
        val effective = SiteSettingsResolver.resolve(
            globalForceDark = false, globalDesktop = false, globalTextZoom = 80, override = null,
        )
        assertEquals(80, effective.textZoom)
    }

    @Test
    fun `explicit text zoom is used as-is`() {
        val override = SiteSettingsEntity(host = "a.com", textZoom = 150)
        val effective = SiteSettingsResolver.resolve(
            globalForceDark = false, globalDesktop = false, globalTextZoom = 100, override = override,
        )
        assertEquals(150, effective.textZoom)
    }

    @Test
    fun `explicit site text zoom beats a different global scale`() {
        val override = SiteSettingsEntity(host = "a.com", textZoom = 150)
        val effective = SiteSettingsResolver.resolve(
            globalForceDark = false, globalDesktop = false, globalTextZoom = 70, override = override,
        )
        assertEquals(150, effective.textZoom)
    }
}
