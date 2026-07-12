package com.udaytank.browse.browser

import com.udaytank.browse.data.TabEntity

object TabGroupPolicy {
    /**
     * Auto-islands: a tab opened FROM another tab inherits that tab's group.
     * Joins existing groups only — never creates one (owner-approved decision, spec §3 P1).
     */
    fun groupForNewTab(parent: TabEntity?, autoIslands: Boolean): Long? =
        if (autoIslands) parent?.groupId else null
}
