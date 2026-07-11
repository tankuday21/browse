package com.udaytank.browse.browser

import com.udaytank.browse.data.TabEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TabClosePolicyTest {

    private fun tab(id: Long, position: Int) =
        TabEntity(id = id, url = "https://a.com", title = "t", position = position, isActive = false)

    private val three = listOf(tab(1, 0), tab(2, 1), tab(3, 2))

    @Test
    fun `closing a background tab keeps the current active tab`() {
        assertEquals(2L, TabClosePolicy.nextActiveId(three, closingId = 3, activeId = 2))
    }

    @Test
    fun `closing the active tab activates its right neighbor`() {
        assertEquals(3L, TabClosePolicy.nextActiveId(three, closingId = 2, activeId = 2))
    }

    @Test
    fun `closing the active last tab falls back to the left neighbor`() {
        assertEquals(2L, TabClosePolicy.nextActiveId(three, closingId = 3, activeId = 3))
    }

    @Test
    fun `closing the only tab leaves nothing to activate`() {
        assertNull(TabClosePolicy.nextActiveId(listOf(tab(1, 0)), closingId = 1, activeId = 1))
    }
}
