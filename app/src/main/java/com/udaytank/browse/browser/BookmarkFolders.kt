package com.udaytank.browse.browser

import com.udaytank.browse.data.Bookmark

/**
 * Pure grouping/normalization for bookmark folders (v6.10). A folder is just the `folder` string on
 * a [Bookmark]; a folder "exists" exactly while at least one bookmark references it (no first-class
 * folder objects). Top level = a null/blank folder.
 */
object BookmarkFolders {

    /** Trim; a blank/whitespace-only name means "top level" (null). */
    fun normalize(name: String?): String? = name?.trim()?.ifBlank { null }

    /** Distinct folder names present, case-insensitively sorted. Bookmarks with no folder are ignored. */
    fun folders(bookmarks: List<Bookmark>): List<String> =
        bookmarks.mapNotNull { normalize(it.folder) }
            .distinct()
            .sortedBy { it.lowercase() }

    /**
     * Display sections: each named folder (case-insensitive alpha) paired with its bookmarks, then a
     * trailing `null`-key section for top-level (folderless) bookmarks. Empty sections are omitted;
     * bookmarks keep their incoming order within a section (newest-first as the DAO returns them).
     */
    fun sections(bookmarks: List<Bookmark>): List<Pair<String?, List<Bookmark>>> {
        val named = folders(bookmarks).map { folder ->
            folder to bookmarks.filter { normalize(it.folder)?.equals(folder, ignoreCase = false) == true }
        }
        val topLevel = bookmarks.filter { normalize(it.folder) == null }
        return if (topLevel.isEmpty()) named else named + (null to topLevel)
    }
}
