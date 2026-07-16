package com.udaytank.browse.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "bookmarks", indices = [Index(value = ["url", "orbitId"], unique = true)])
data class Bookmark(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String,
    val createdAt: Long,
    /** Null = top level; otherwise the folder name this bookmark lives in. */
    val folder: String? = null,
    /**
     * The Orbit this bookmark belongs to (v4.4, Orbits Phase 3). The unique index is on
     * `(url, orbitId)` so the same URL can be bookmarked independently in different Orbits.
     * Nullable to absorb the migration backfill; every write sets it.
     */
    val orbitId: Long? = null,
)
