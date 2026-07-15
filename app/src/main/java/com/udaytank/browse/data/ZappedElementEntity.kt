package com.udaytank.browse.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A user-hidden ("zapped") page element (v4.0 Element Zapper). [selector] is a CSS selector
 * re-applied at document start on every visit to [host]; [label] is a human summary (e.g.
 * "div.ad-banner") shown in the manage sheet. Per-host; indexed by host for fast lookup.
 */
@Entity(
    tableName = "zapped_elements",
    indices = [Index(value = ["host"])],
)
data class ZappedElementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val host: String,
    val selector: String,
    val label: String,
    val createdAt: Long,
)
