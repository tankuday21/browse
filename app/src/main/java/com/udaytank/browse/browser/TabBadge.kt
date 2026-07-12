package com.udaytank.browse.browser

object TabBadge {
    /** Command Bar tab counter; two chars max — 100+ tabs collapse to "∞". */
    fun label(count: Int): String = when {
        count >= 100 -> "∞"
        count < 0 -> "0"
        else -> count.toString()
    }
}
