package com.udaytank.browse.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One tile on the home page's shortcut grid (C1). User-curated: seeded once from the
 * bookmarks the old speed-dial showed (v8→v9 migration), then fully user-managed.
 */
@Entity(tableName = "home_shortcuts")
data class HomeShortcutEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String,
    /** 0-based slot in the grid; observeAll orders by this. */
    val position: Int,
    /**
     * The Orbit this tile belongs to (v4.4, Orbits Phase 3). The home grid is per-Orbit; a new
     * Orbit starts empty. Nullable to absorb the migration backfill; every write sets it.
     */
    val orbitId: Long? = null,
)
