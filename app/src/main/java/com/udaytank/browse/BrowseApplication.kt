package com.udaytank.browse

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.udaytank.browse.data.BrowseDatabase
import com.udaytank.browse.data.DataStoreSettingsRepository
import com.udaytank.browse.data.SettingsRepository

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

class BrowseApplication : Application() {
    val database: BrowseDatabase by lazy {
        Room.databaseBuilder(this, BrowseDatabase::class.java, "browse.db")
            .addMigrations(BrowseDatabase.MIGRATION_1_2)
            .build()
    }

    val settingsRepository: SettingsRepository by lazy {
        DataStoreSettingsRepository(settingsDataStore)
    }
}
