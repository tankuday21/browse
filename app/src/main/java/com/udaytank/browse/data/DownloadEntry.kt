package com.udaytank.browse.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Our record of a download; the system DownloadManager owns the transfer itself. */
@Entity(tableName = "downloads")
data class DownloadEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val downloadId: Long,
    val fileName: String,
    val url: String,
    val createdAt: Long,
)
