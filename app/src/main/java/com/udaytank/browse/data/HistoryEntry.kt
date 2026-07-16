package com.udaytank.browse.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "history")
data class HistoryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String,
    val visitedAt: Long,
    /**
     * The Orbit this visit belongs to (v4.3, Orbits Phase 2). Nullable to match the `tabs`
     * column style and to absorb the migration backfill; every non-incognito write sets it,
     * and incognito never records history at all. Reads filter on it so one Orbit's history
     * never surfaces in another's list, quick dials, or suggestions.
     */
    val orbitId: Long? = null,
)
