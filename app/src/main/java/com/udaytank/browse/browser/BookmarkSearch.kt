package com.udaytank.browse.browser

import com.udaytank.browse.data.Bookmark

/** Pure, case-insensitive substring search over an Orbit's bookmarks (v6.12). */
object BookmarkSearch {

    /** True if [query] (trimmed) is a case-insensitive substring of the title or URL; blank matches all. */
    fun matches(bookmark: Bookmark, query: String): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return true
        return bookmark.title.contains(q, ignoreCase = true) ||
            bookmark.url.contains(q, ignoreCase = true)
    }

    /** Filter preserving input order; a blank query returns the list unchanged. */
    fun filter(bookmarks: List<Bookmark>, query: String): List<Bookmark> {
        val q = query.trim()
        if (q.isEmpty()) return bookmarks
        return bookmarks.filter { matches(it, q) }
    }
}
