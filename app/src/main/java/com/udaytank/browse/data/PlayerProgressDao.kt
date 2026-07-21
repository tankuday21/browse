package com.udaytank.browse.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PlayerProgressDao {
    @Query("SELECT * FROM player_progress WHERE filePath = :filePath LIMIT 1")
    suspend fun get(filePath: String): PlayerProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: PlayerProgressEntity)

    @Query("DELETE FROM player_progress WHERE filePath = :filePath")
    suspend fun delete(filePath: String)

    @Query("DELETE FROM player_progress")
    suspend fun clearAll()
}
