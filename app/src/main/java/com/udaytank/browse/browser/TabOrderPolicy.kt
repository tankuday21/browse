package com.udaytank.browse.browser

import com.udaytank.browse.data.TabEntity
import com.udaytank.browse.data.TabGroupEntity

object TabOrderPolicy {
    /** Switcher display order: pinned → groups (by group position) → ungrouped. */
    fun ordered(tabs: List<TabEntity>, groups: List<TabGroupEntity>): List<TabEntity> {
        val knownGroups = groups.associateBy { it.id }
        val pinned = tabs.filter { it.pinned }.sortedBy { it.position }
        val rest = tabs.filterNot { it.pinned }
        val (grouped, ungrouped) = rest.partition { it.groupId != null && it.groupId in knownGroups }
        val clustered = grouped
            .sortedBy { it.position }
            .sortedBy { knownGroups.getValue(it.groupId!!).position }
        return pinned + clustered + ungrouped.sortedBy { it.position }
    }
}
