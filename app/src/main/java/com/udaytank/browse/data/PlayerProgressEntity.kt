package com.udaytank.browse.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Andromeda Player resume position (v6.0), keyed by the file's path — the file IS the identity,
 * and a re-downloaded file at the same path legitimately resumes. Finished items clear their row.
 */
@Entity(tableName = "player_progress")
data class PlayerProgressEntity(
    @PrimaryKey val filePath: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
)
