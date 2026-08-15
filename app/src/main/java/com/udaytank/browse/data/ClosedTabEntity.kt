package com.udaytank.browse.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "closed_tabs")
data class ClosedTabEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String,
    val closedAt: Long,
    /**
     * Owning Orbit (v6.16). Nullable to match `tabs`/`history`/`bookmarks`. A NULL row matches no
     * `WHERE orbitId = :orbitId`, so an unattributed entry is invisible everywhere rather than
     * visible in the wrong Orbit — fail-closed by construction.
     */
    val orbitId: Long? = null,
)
