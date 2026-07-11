package com.udaytank.browse.browser

import com.udaytank.browse.data.TabEntity

object TabClosePolicy {

    /**
     * Which tab id should be active after [closingId] closes?
     * Null when no tabs will remain. Chrome rule: closing a background
     * tab changes nothing; closing the active tab prefers the right
     * neighbor, then the left.
     */
    fun nextActiveId(tabs: List<TabEntity>, closingId: Long, activeId: Long?): Long? {
        if (closingId != activeId) return activeId
        val remaining = tabs.filterNot { it.id == closingId }
        if (remaining.isEmpty()) return null
        val closedIndex = tabs.indexOfFirst { it.id == closingId }
        return (remaining.getOrNull(closedIndex) ?: remaining.last()).id
    }
}
