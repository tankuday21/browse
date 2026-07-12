package com.udaytank.browse

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.udaytank.browse.browser.AdBlockEngine
import com.udaytank.browse.browser.FilterListParser
import com.udaytank.browse.data.BrowseDatabase
import com.udaytank.browse.data.DataStoreSettingsRepository
import com.udaytank.browse.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

class BrowseApplication : Application() {

    val adBlockEngine = AdBlockEngine()

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            val text = assets.open("adblock/easylist.txt").bufferedReader().use { it.readText() }
            adBlockEngine.load(FilterListParser.parse(text))
        }
        appScope.launch {
            // The download engine lives in this process: at process start no download
            // can actually be running, so any row still marked RUNNING is an orphan
            // from a killed process. Flip it to PAUSED so the user can resume it.
            database.downloadDao().getActive()
                .filter { it.state == "RUNNING" }
                .forEach { database.downloadDao().setState(it.id, "PAUSED") }
        }
    }
    val database: BrowseDatabase by lazy {
        Room.databaseBuilder(this, BrowseDatabase::class.java, "browse.db")
            .addMigrations(
                BrowseDatabase.MIGRATION_1_2,
                BrowseDatabase.MIGRATION_2_3,
                BrowseDatabase.MIGRATION_3_4,
                BrowseDatabase.MIGRATION_4_5,
                BrowseDatabase.MIGRATION_5_6,
                BrowseDatabase.MIGRATION_6_7,
            )
            .build()
    }

    val settingsRepository: SettingsRepository by lazy {
        DataStoreSettingsRepository(settingsDataStore)
    }
}
