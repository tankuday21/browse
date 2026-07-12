package com.udaytank.browse.browser

import com.udaytank.browse.data.TabEntity
import com.udaytank.browse.data.TabGroupEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class TabOrderPolicyTest {
    private fun tab(id: Long, pos: Int, group: Long? = null, pinned: Boolean = false) =
        TabEntity(id = id, url = "u$id", title = "t$id", position = pos,
            isActive = false, groupId = group, pinned = pinned)

    @Test fun `pinned tabs come first regardless of position`() {
        val tabs = listOf(tab(1, 0), tab(2, 1, pinned = true), tab(3, 2))
        assertEquals(listOf(2L, 1L, 3L), TabOrderPolicy.ordered(tabs, emptyList()).map { it.id })
    }

    @Test fun `grouped tabs cluster together in group order`() {
        val groups = listOf(
            TabGroupEntity(id = 10, name = "B", color = 0, position = 1),
            TabGroupEntity(id = 20, name = "A", color = 1, position = 0),
        )
        val tabs = listOf(tab(1, 0, group = 10), tab(2, 1), tab(3, 2, group = 20), tab(4, 3, group = 10))
        // group 20 (position 0) first, then group 10 (position 1), then ungrouped
        assertEquals(listOf(3L, 1L, 4L, 2L), TabOrderPolicy.ordered(tabs, groups).map { it.id })
    }

    @Test fun `tab in an unknown group is treated as ungrouped`() {
        val tabs = listOf(tab(1, 0, group = 999), tab(2, 1))
        assertEquals(listOf(1L, 2L), TabOrderPolicy.ordered(tabs, emptyList()).map { it.id })
    }
}
