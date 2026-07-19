package com.udaytank.browse.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Our record of a download; the system DownloadManager owns the transfer itself. */
@Entity(tableName = "downloads")
data class DownloadEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val downloadId: Long = -1,          // legacy system-DM id; -1 for engine downloads
    val fileName: String,
    val url: String,
    val createdAt: Long,
    val totalBytes: Long = -1,
    val downloadedBytes: Long = 0,
    val state: String = "DONE",
    val filePath: String? = null,
    val mimeType: String? = null,
    val etag: String? = null,
    val segments: Int = 1,
    val segmentState: String? = null,   // JSON: per-segment downloaded bytes
    val error: String? = null,
    val attempts: Int = 0,
    /** The Orbit this download belongs to (v5.5); null only for pre-migration edge rows. */
    val orbitId: Long? = null,
)
