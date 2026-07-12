package com.udaytank.browse.browser

import com.udaytank.browse.data.TabEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TabGroupPolicyTest {
    private fun parent(group: Long?) = TabEntity(
        id = 1, url = "u", title = "t", position = 0, isActive = true, groupId = group)

    @Test fun `child joins parent's group when auto-islands on`() {
        assertEquals(7L, TabGroupPolicy.groupForNewTab(parent(7L), autoIslands = true))
    }

    @Test fun `no group when parent ungrouped`() {
        assertNull(TabGroupPolicy.groupForNewTab(parent(null), autoIslands = true))
    }

    @Test fun `no group when auto-islands off`() {
        assertNull(TabGroupPolicy.groupForNewTab(parent(7L), autoIslands = false))
    }

    @Test fun `no group without a parent`() {
        assertNull(TabGroupPolicy.groupForNewTab(null, autoIslands = true))
    }
}
