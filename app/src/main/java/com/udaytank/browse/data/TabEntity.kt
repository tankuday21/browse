package com.udaytank.browse.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tabs")
data class TabEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String,
    val position: Int,
    val isActive: Boolean,
    val isIncognito: Boolean = false,
    val groupId: Long? = null,
    val pinned: Boolean = false,
    val locked: Boolean = false,
)
