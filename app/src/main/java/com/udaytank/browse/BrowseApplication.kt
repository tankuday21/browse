package com.udaytank.browse

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.udaytank.browse.browser.AdBlockEngine
import com.udaytank.browse.browser.adblock.AbpParser
import com.udaytank.browse.browser.adblock.FilterListDef
import com.udaytank.browse.browser.adblock.FilterListUpdater
import com.udaytank.browse.browser.adblock.FilterLists
import com.udaytank.browse.data.BrowseDatabase
import com.udaytank.browse.data.DataStoreSettingsRepository
import com.udaytank.browse.data.FeedRepository
import com.udaytank.browse.data.SettingsRepository
import com.udaytank.browse.data.WeatherRepository
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

class BrowseApplication : Application() {

    val adBlockEngine = AdBlockEngine()

    /**
     * Second filter engine for annoyances (cookie-consent banners, D2), fed by a snapshot of
     * Fanboy's Cookie Monster list. Kept separate from [adBlockEngine] so the per-site ad
     * allowlist never disables banner dismissal — this engine is a global on/off, gated by the
     * WebViewHolder's dismissCookieBanners flag rather than by [AdBlockEngine.updatePolicy].
     */
    val annoyanceEngine = AdBlockEngine()

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        appScope.launch { reloadAdblock() }
        FilterListUpdater.schedulePeriodic(this)
        com.udaytank.browse.feed.FeedRefreshWorker.schedulePeriodic(this)
        appScope.launch {
            // The download engine lives in this process: at process start no download
            // can actually be running, so any row still marked RUNNING is an orphan
            // from a killed process. Flip it to PAUSED so the user can resume it.
            database.downloadDao().getActive()
                .filter { it.state == "RUNNING" }
                .forEach { database.downloadDao().setState(it.id, "PAUSED") }
        }
    }

    /**
     * (Re)builds both engines from every enabled filter list, entirely off-main. Each list is
     * read from filesDir/adblock/<id>.txt (a downloaded update) when present, else from the
     * bundled asset snapshot. Called at app start, after a [FilterListUpdater] run, and after
     * a per-list toggle in Settings.
     */
    suspend fun reloadAdblock() = withContext(Dispatchers.Default) {
        val enabledIds = settingsRepository.adBlockLists.first()
        val parsed = FilterLists.ADS
            .filter { it.id in enabledIds }
            .mapNotNull { def -> readListText(def)?.let { AbpParser.parse(it) } }
        adBlockEngine.load(parsed)
        readListText(FilterLists.ANNOYANCE)?.let { annoyanceEngine.load(AbpParser.parse(it)) }
    }

    /** Downloaded snapshot if present, else the bundled asset; null only if both fail. */
    private fun readListText(def: FilterListDef): String? = runCatching {
        val updated = File(filesDir, "adblock/${def.id}.txt")
        if (updated.exists()) updated.readText()
        else assets.open(def.assetPath).bufferedReader().use { it.readText() }
    }.getOrNull()

    val database: BrowseDatabase by lazy {
        Room.databaseBuilder(this, BrowseDatabase::class.java, "browse.db")
            .addMigrations(
                BrowseDatabase.MIGRATION_1_2,
                BrowseDatabase.MIGRATION_2_3,
                BrowseDatabase.MIGRATION_3_4,
                BrowseDatabase.MIGRATION_4_5,
                BrowseDatabase.MIGRATION_5_6,
                BrowseDatabase.MIGRATION_6_7,
                BrowseDatabase.MIGRATION_7_8,
                BrowseDatabase.MIGRATION_8_9,
                BrowseDatabase.MIGRATION_9_10,
                BrowseDatabase.MIGRATION_10_11,
                BrowseDatabase.MIGRATION_11_12,
                BrowseDatabase.MIGRATION_12_13,
                BrowseDatabase.MIGRATION_13_14,
                BrowseDatabase.MIGRATION_14_15,
                BrowseDatabase.MIGRATION_15_16,
                BrowseDatabase.MIGRATION_16_17,
                BrowseDatabase.MIGRATION_17_18,
            )
            .build()
    }

    val settingsRepository: SettingsRepository by lazy {
        DataStoreSettingsRepository(settingsDataStore)
    }

    /** v3.2 home feed: RSS cache + source-direct fetch. */
    val feedRepository: FeedRepository by lazy { FeedRepository(database.feedDao()) }

    /** v3.2 weather via Open-Meteo (keyless). */
    val weatherRepository: WeatherRepository by lazy { WeatherRepository() }

    /** v4.0 Element Zapper: per-site hidden-element store. */
    val zapRepository: com.udaytank.browse.data.ZapRepository by lazy {
        com.udaytank.browse.data.ZapRepository(database.zappedElementDao())
    }

    /** v4.1 site-icon cache, captured source-direct from the WebView as you browse. */
    val faviconRepository: com.udaytank.browse.data.FaviconRepository by lazy {
        com.udaytank.browse.data.FaviconRepository(database.faviconDao())
    }

    /** v4.2 Orbits: user-created browsing profiles (isolated tabs + WebView profile). */
    val orbitRepository: com.udaytank.browse.data.OrbitRepository by lazy {
        com.udaytank.browse.data.OrbitRepository(database.orbitDao())
    }

    /** v4.7 Passwords: per-Orbit credential vault, encrypted with an AndroidKeyStore key. */
    val credentialRepository: com.udaytank.browse.data.CredentialRepository by lazy {
        com.udaytank.browse.data.CredentialRepository(
            database.credentialDao(),
            com.udaytank.browse.data.KeystoreCredentialCipher(),
        )
    }
}
