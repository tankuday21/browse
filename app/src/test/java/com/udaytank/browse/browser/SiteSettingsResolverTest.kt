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
            globalForceDark = true, globalDesktop = false, override = null,
        )
        assertEquals(100, effective.textZoom)
        assertTrue(effective.forceDark)
        assertFalse(effective.desktopMode)
    }

    @Test
    fun `all-unset override behaves exactly like no override`() {
        val override = SiteSettingsEntity(host = "a.com") // all fields default to -1
        val effective = SiteSettingsResolver.resolve(
            globalForceDark = false, globalDesktop = true, override = override,
        )
        assertEquals(100, effective.textZoom)
        assertFalse(effective.forceDark)
        assertTrue(effective.desktopMode)
    }

    @Test
    fun `explicit off beats global on`() {
        val override = SiteSettingsEntity(host = "a.com", forceDark = 0, desktopMode = 0)
        val effective = SiteSettingsResolver.resolve(
            globalForceDark = true, globalDesktop = true, override = override,
        )
        assertFalse(effective.forceDark)
        assertFalse(effective.desktopMode)
    }

    @Test
    fun `explicit on beats global off`() {
        val override = SiteSettingsEntity(host = "a.com", forceDark = 1, desktopMode = 1)
        val effective = SiteSettingsResolver.resolve(
            globalForceDark = false, globalDesktop = false, override = override,
        )
        assertTrue(effective.forceDark)
        assertTrue(effective.desktopMode)
    }

    @Test
    fun `unset text zoom resolves to 100 percent`() {
        val override = SiteSettingsEntity(host = "a.com", textZoom = -1)
        val effective = SiteSettingsResolver.resolve(
            globalForceDark = false, globalDesktop = false, override = override,
        )
        assertEquals(100, effective.textZoom)
    }

    @Test
    fun `explicit text zoom is used as-is`() {
        val override = SiteSettingsEntity(host = "a.com", textZoom = 150)
        val effective = SiteSettingsResolver.resolve(
            globalForceDark = false, globalDesktop = false, override = override,
        )
        assertEquals(150, effective.textZoom)
    }
}
