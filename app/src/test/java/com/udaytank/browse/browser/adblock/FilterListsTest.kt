package com.udaytank.browse.browser.adblock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterListsTest {

    @Test
    fun `four ad lists, all enabled by default`() {
        assertEquals(
            listOf("easylist", "easyprivacy", "adguard-mobile", "peter-lowe"),
            FilterLists.ADS.map { it.id },
        )
        assertEquals(FilterLists.ADS.map { it.id }.toSet(), FilterLists.DEFAULT_ENABLED_IDS)
    }

    @Test
    fun `ids are unique across ads and annoyance`() {
        val ids = FilterLists.ADS.map { it.id } + FilterLists.ANNOYANCE.id
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `asset paths follow the id convention the loader and updater share`() {
        for (def in FilterLists.ADS + FilterLists.ANNOYANCE) {
            assertEquals("adblock/${def.id}.txt", def.assetPath)
        }
    }

    @Test
    fun `update urls are https`() {
        for (def in FilterLists.ADS + FilterLists.ANNOYANCE) {
            assertTrue(def.id, def.updateUrl.startsWith("https://"))
        }
    }

    @Test
    fun `annoyance list is not part of the toggleable ad set`() {
        assertFalse(FilterLists.ANNOYANCE.id in FilterLists.DEFAULT_ENABLED_IDS)
    }
}
