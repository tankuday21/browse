package com.udaytank.browse.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reading_list")
data class ReadingListEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String,
    val addedAt: Long,
    val readAt: Long? = null,     // null = unread
    val filePath: String? = null, // offline cleaned-content HTML; null = online-only
)
