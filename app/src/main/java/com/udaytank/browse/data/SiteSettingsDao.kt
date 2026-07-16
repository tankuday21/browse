package com.udaytank.browse.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SiteSettingsDao {
    @Query("SELECT * FROM site_settings WHERE host = :host")
    suspend fun getByHost(host: String): SiteSettingsEntity?

    @Query("SELECT * FROM site_settings")
    fun observeAll(): Flow<List<SiteSettingsEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SiteSettingsEntity)

    @Query("DELETE FROM site_settings WHERE host = :host")
    suspend fun deleteByHost(host: String)

    /** Global wipe (Black Hole panic-wipe). */
    @Query("DELETE FROM site_settings")
    suspend fun clearAll()
}
