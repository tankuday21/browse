package com.udaytank.browse.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class SearchEngine(val label: String, val queryUrl: String) {
    GOOGLE("Google", "https://www.google.com/search?q="),
    DUCKDUCKGO("DuckDuckGo", "https://duckduckgo.com/?q="),
    BING("Bing", "https://www.bing.com/search?q="),
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }

interface SettingsRepository {
    val searchEngine: Flow<SearchEngine>
    val themeMode: Flow<ThemeMode>
    val javaScriptEnabled: Flow<Boolean>
    val cookiesEnabled: Flow<Boolean>
    val adBlockEnabled: Flow<Boolean>
    val adAllowedSites: Flow<Set<String>>
    suspend fun setSearchEngine(engine: SearchEngine)
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setJavaScriptEnabled(enabled: Boolean)
    suspend fun setCookiesEnabled(enabled: Boolean)
    suspend fun setAdBlockEnabled(enabled: Boolean)
    suspend fun toggleAdAllowedSite(host: String)
    val forceDarkWebsites: Flow<Boolean>
    suspend fun setForceDarkWebsites(enabled: Boolean)
    val httpsOnly: Flow<Boolean>
    suspend fun setHttpsOnly(enabled: Boolean)
    val lockIncognito: Flow<Boolean>
    suspend fun setLockIncognito(enabled: Boolean)
    val autoIslands: Flow<Boolean>
    suspend fun setAutoIslands(enabled: Boolean)
    val switcherListLayout: Flow<Boolean>
    suspend fun setSwitcherListLayout(enabled: Boolean)
    val useSystemDownloader: Flow<Boolean>
    suspend fun setUseSystemDownloader(enabled: Boolean)
    val backgroundMedia: Flow<Boolean>
    suspend fun setBackgroundMedia(enabled: Boolean)
    val backgroundMediaSites: Flow<Set<String>>
    suspend fun setBackgroundMediaSites(sites: Set<String>)
}

class DataStoreSettingsRepository(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    override val searchEngine: Flow<SearchEngine> = dataStore.data.map { prefs ->
        prefs[SEARCH_ENGINE_KEY]?.let { stored ->
            SearchEngine.entries.find { it.name == stored }
        } ?: SearchEngine.GOOGLE
    }

    override val themeMode: Flow<ThemeMode> = dataStore.data.map { prefs ->
        prefs[THEME_MODE_KEY]?.let { stored ->
            ThemeMode.entries.find { it.name == stored }
        } ?: ThemeMode.SYSTEM
    }

    override val javaScriptEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[JAVASCRIPT_KEY] ?: true
    }

    override val cookiesEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[COOKIES_KEY] ?: true
    }

    override val forceDarkWebsites: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[FORCE_DARK_KEY] ?: false
    }

    override suspend fun setForceDarkWebsites(enabled: Boolean) {
        dataStore.edit { it[FORCE_DARK_KEY] = enabled }
    }

    override val httpsOnly: Flow<Boolean> = dataStore.data.map { it[HTTPS_ONLY_KEY] ?: false }
    override suspend fun setHttpsOnly(enabled: Boolean) {
        dataStore.edit { it[HTTPS_ONLY_KEY] = enabled }
    }

    override val lockIncognito: Flow<Boolean> = dataStore.data.map { it[LOCK_INCOGNITO_KEY] ?: false }
    override suspend fun setLockIncognito(enabled: Boolean) {
        dataStore.edit { it[LOCK_INCOGNITO_KEY] = enabled }
    }

    override val autoIslands: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[AUTO_ISLANDS_KEY] ?: true
    }

    override suspend fun setAutoIslands(enabled: Boolean) {
        dataStore.edit { it[AUTO_ISLANDS_KEY] = enabled }
    }

    override val switcherListLayout: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[SWITCHER_LIST_LAYOUT_KEY] ?: false
    }

    override suspend fun setSwitcherListLayout(enabled: Boolean) {
        dataStore.edit { it[SWITCHER_LIST_LAYOUT_KEY] = enabled }
    }

    override val useSystemDownloader: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[USE_SYSTEM_DOWNLOADER_KEY] ?: false
    }

    override suspend fun setUseSystemDownloader(enabled: Boolean) {
        dataStore.edit { it[USE_SYSTEM_DOWNLOADER_KEY] = enabled }
    }

    override val backgroundMedia: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[BACKGROUND_MEDIA_KEY] ?: false
    }

    override suspend fun setBackgroundMedia(enabled: Boolean) {
        dataStore.edit { it[BACKGROUND_MEDIA_KEY] = enabled }
    }

    override val backgroundMediaSites: Flow<Set<String>> = dataStore.data.map { prefs ->
        prefs[BACKGROUND_MEDIA_SITES_KEY] ?: emptySet()
    }

    override suspend fun setBackgroundMediaSites(sites: Set<String>) {
        dataStore.edit { it[BACKGROUND_MEDIA_SITES_KEY] = sites }
    }

    override suspend fun setSearchEngine(engine: SearchEngine) {
        dataStore.edit { it[SEARCH_ENGINE_KEY] = engine.name }
    }

    override val adBlockEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[AD_BLOCK_KEY] ?: true
    }

    override val adAllowedSites: Flow<Set<String>> = dataStore.data.map { prefs ->
        prefs[AD_ALLOWED_SITES_KEY] ?: emptySet()
    }

    override suspend fun setAdBlockEnabled(enabled: Boolean) {
        dataStore.edit { it[AD_BLOCK_KEY] = enabled }
    }

    override suspend fun toggleAdAllowedSite(host: String) {
        dataStore.edit { prefs ->
            val current = prefs[AD_ALLOWED_SITES_KEY] ?: emptySet()
            prefs[AD_ALLOWED_SITES_KEY] =
                if (host in current) current - host else current + host
        }
    }

    override suspend fun setJavaScriptEnabled(enabled: Boolean) {
        dataStore.edit { it[JAVASCRIPT_KEY] = enabled }
    }

    override suspend fun setCookiesEnabled(enabled: Boolean) {
        dataStore.edit { it[COOKIES_KEY] = enabled }
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[THEME_MODE_KEY] = mode.name }
    }

    private companion object {
        val SEARCH_ENGINE_KEY = stringPreferencesKey("search_engine")
        val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
        val JAVASCRIPT_KEY = booleanPreferencesKey("javascript_enabled")
        val COOKIES_KEY = booleanPreferencesKey("cookies_enabled")
        val AD_BLOCK_KEY = booleanPreferencesKey("ad_block_enabled")
        val FORCE_DARK_KEY = booleanPreferencesKey("force_dark_websites")
        val HTTPS_ONLY_KEY = booleanPreferencesKey("https_only")
        val LOCK_INCOGNITO_KEY = booleanPreferencesKey("lock_incognito")
        val AD_ALLOWED_SITES_KEY = stringSetPreferencesKey("ad_allowed_sites")
        val AUTO_ISLANDS_KEY = booleanPreferencesKey("auto_islands")
        val SWITCHER_LIST_LAYOUT_KEY = booleanPreferencesKey("switcher_list_layout")
        val USE_SYSTEM_DOWNLOADER_KEY = booleanPreferencesKey("use_system_downloader")
        val BACKGROUND_MEDIA_KEY = booleanPreferencesKey("background_media")
        val BACKGROUND_MEDIA_SITES_KEY = stringSetPreferencesKey("background_media_sites")
    }
}
