package com.udaytank.browse.browser

import com.udaytank.browse.data.TabEntity

object TabSearchFilter {
    fun filter(tabs: List<TabEntity>, query: String): List<TabEntity> {
        val q = query.trim()
        if (q.isEmpty()) return tabs
        return tabs.filter {
            it.title.contains(q, ignoreCase = true) || it.url.contains(q, ignoreCase = true)
        }
    }
}
