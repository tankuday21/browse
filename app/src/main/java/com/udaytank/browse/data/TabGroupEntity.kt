package com.udaytank.browse.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tab_groups")
data class TabGroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Index into Orbit's group palette (0..5), not an ARGB value. */
    val color: Int,
    val position: Int,
)
