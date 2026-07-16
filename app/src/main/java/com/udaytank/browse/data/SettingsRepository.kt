package com.udaytank.browse.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.udaytank.browse.browser.adblock.FilterLists
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class SearchEngine(val label: String, val queryUrl: String) {
    GOOGLE("Google", "https://www.google.com/search?q="),
    DUCKDUCKGO("DuckDuckGo", "https://duckduckgo.com/?q="),
    BING("Bing", "https://www.bing.com/search?q="),
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class ReaderTheme { SYSTEM, LIGHT, SEPIA, DARK }

/** Home canvas shortcut layout (v3.1 Focused home): one calm row, or the full grid. */
enum class ShortcutDensity { FEW, MORE }

interface SettingsRepository {
    val searchEngine: Flow<SearchEngine>
    val themeMode: Flow<ThemeMode>
    val javaScriptEnabled: Flow<Boolean>
    val cookiesEnabled: Flow<Boolean>
    val adBlockEnabled: Flow<Boolean>
    val adAllowedSites: Flow<Set<String>>
    /** Ids of the enabled ad/tracker filter lists (see FilterLists.ADS). Default: all of them. */
    val adBlockLists: Flow<Set<String>>
    suspend fun toggleAdBlockList(id: String)
    /** Epoch millis of the last successful filter-list update; 0 = never updated. */
    val adBlockLastUpdated: Flow<Long>
    suspend fun setAdBlockLastUpdated(timestamp: Long)
    /** Google Safe Browsing checks inside WebView (D1). Default ON. */
    val safeBrowsing: Flow<Boolean>
    suspend fun setSafeBrowsing(enabled: Boolean)
    /** Cookie-consent banner auto-dismiss via the annoyance filter list (D2). Default ON. */
    val dismissCookieBanners: Flow<Boolean>
    suspend fun setDismissCookieBanners(enabled: Boolean)
    /** Global Privacy Control (D5) — a legal do-not-sell/share signal, so the user opts IN. */
    val gpcEnabled: Flow<Boolean>
    suspend fun setGpcEnabled(enabled: Boolean)
    /** Lifetime count of blocked requests across all pages (C3 home stats). */
    val lifetimeBlocked: Flow<Long>
    suspend fun addBlockedCount(delta: Long)
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
    val readerFontScale: Flow<Int>
    suspend fun setReaderFontScale(percent: Int)
    val readerTheme: Flow<ReaderTheme>
    suspend fun setReaderTheme(theme: ReaderTheme)
    val readerWide: Flow<Boolean>
    suspend fun setReaderWide(wide: Boolean)
    /** First-run onboarding finished (J2). Device-local — deliberately never backed up. */
    val onboardingDone: Flow<Boolean>
    suspend fun setOnboardingDone(done: Boolean)
    /** Global page text scale in percent (I3), clamped 50..200. Site overrides win. */
    val textScale: Flow<Int>
    suspend fun setTextScale(percent: Int)
    /** Best score in the offline-page asteroid game (K1). Never negative. */
    val asteroidHighScore: Flow<Int>
    suspend fun setAsteroidHighScore(score: Int)

    /** Home canvas customization (v3.1 Focused home). */
    val showGreeting: Flow<Boolean>
    suspend fun setShowGreeting(enabled: Boolean)
    /** Non-incognito only — enforced by the caller, never by this flow. */
    val showHomeStats: Flow<Boolean>
    suspend fun setShowHomeStats(enabled: Boolean)
    val shortcutDensity: Flow<ShortcutDensity>
    suspend fun setShortcutDensity(density: ShortcutDensity)
    /** Id of a bundled backdrop ("aurora"/"nebula"), or "" for none. */
    val homeWallpaper: Flow<String>
    suspend fun setHomeWallpaper(id: String)

    /** v3.2 home feed. Master toggle (off → the calm focused home); weather sub-controls. */
    val showFeed: Flow<Boolean>
    suspend fun setShowFeed(enabled: Boolean)
    val showWeather: Flow<Boolean>
    suspend fun setShowWeather(enabled: Boolean)
    /** News section on the home feed. Off by default (v4.1) — opt-in from Settings. */
    val showNews: Flow<Boolean>
    suspend fun setShowNews(enabled: Boolean)
    /** City name for weather when not using location; "" if unset. */
    val weatherCity: Flow<String>
    suspend fun setWeatherCity(city: String)
    /** Use opt-in coarse location for weather instead of [weatherCity]. */
    val weatherUseLocation: Flow<Boolean>
    suspend fun setWeatherUseLocation(enabled: Boolean)
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

    override val readerFontScale: Flow<Int> = dataStore.data.map { prefs ->
        prefs[READER_FONT_SCALE_KEY] ?: 100
    }

    override suspend fun setReaderFontScale(percent: Int) {
        dataStore.edit { it[READER_FONT_SCALE_KEY] = percent.coerceIn(70, 160) }
    }

    override val readerTheme: Flow<ReaderTheme> = dataStore.data.map { prefs ->
        prefs[READER_THEME_KEY]?.let { stored ->
            ReaderTheme.entries.find { it.name == stored }
        } ?: ReaderTheme.SYSTEM
    }

    override suspend fun setReaderTheme(theme: ReaderTheme) {
        dataStore.edit { it[READER_THEME_KEY] = theme.name }
    }

    override val readerWide: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[READER_WIDE_KEY] ?: false
    }

    override suspend fun setReaderWide(wide: Boolean) {
        dataStore.edit { it[READER_WIDE_KEY] = wide }
    }

    override val onboardingDone: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[ONBOARDING_DONE_KEY] ?: false
    }

    override suspend fun setOnboardingDone(done: Boolean) {
        dataStore.edit { it[ONBOARDING_DONE_KEY] = done }
    }

    override val textScale: Flow<Int> = dataStore.data.map { prefs ->
        prefs[TEXT_SCALE_KEY] ?: 100
    }

    override suspend fun setTextScale(percent: Int) {
        dataStore.edit { it[TEXT_SCALE_KEY] = percent.coerceIn(50, 200) }
    }

    override val asteroidHighScore: Flow<Int> = dataStore.data.map { prefs ->
        prefs[ASTEROID_HIGH_SCORE_KEY] ?: 0
    }

    override suspend fun setAsteroidHighScore(score: Int) {
        dataStore.edit { it[ASTEROID_HIGH_SCORE_KEY] = score.coerceAtLeast(0) }
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

    override val adBlockLists: Flow<Set<String>> = dataStore.data.map { prefs ->
        prefs[AD_BLOCK_LISTS_KEY] ?: FilterLists.DEFAULT_ENABLED_IDS
    }

    override suspend fun toggleAdBlockList(id: String) {
        dataStore.edit { prefs ->
            val current = prefs[AD_BLOCK_LISTS_KEY] ?: FilterLists.DEFAULT_ENABLED_IDS
            prefs[AD_BLOCK_LISTS_KEY] = if (id in current) current - id else current + id
        }
    }

    override val adBlockLastUpdated: Flow<Long> = dataStore.data.map { prefs ->
        prefs[AD_BLOCK_LAST_UPDATED_KEY] ?: 0L
    }

    override suspend fun setAdBlockLastUpdated(timestamp: Long) {
        dataStore.edit { it[AD_BLOCK_LAST_UPDATED_KEY] = timestamp }
    }

    override val safeBrowsing: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[SAFE_BROWSING_KEY] ?: true
    }

    override suspend fun setSafeBrowsing(enabled: Boolean) {
        dataStore.edit { it[SAFE_BROWSING_KEY] = enabled }
    }

    override val dismissCookieBanners: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[DISMISS_COOKIE_BANNERS_KEY] ?: true
    }

    override suspend fun setDismissCookieBanners(enabled: Boolean) {
        dataStore.edit { it[DISMISS_COOKIE_BANNERS_KEY] = enabled }
    }

    override val gpcEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[GPC_ENABLED_KEY] ?: false
    }

    override suspend fun setGpcEnabled(enabled: Boolean) {
        dataStore.edit { it[GPC_ENABLED_KEY] = enabled }
    }

    override val lifetimeBlocked: Flow<Long> = dataStore.data.map { prefs ->
        prefs[LIFETIME_BLOCKED_KEY] ?: 0L
    }

    override suspend fun addBlockedCount(delta: Long) {
        dataStore.edit { it[LIFETIME_BLOCKED_KEY] = (it[LIFETIME_BLOCKED_KEY] ?: 0L) + delta }
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

    override val showGreeting: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[SHOW_GREETING_KEY] ?: false
    }

    override suspend fun setShowGreeting(enabled: Boolean) {
        dataStore.edit { it[SHOW_GREETING_KEY] = enabled }
    }

    override val showHomeStats: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[SHOW_HOME_STATS_KEY] ?: false
    }

    override suspend fun setShowHomeStats(enabled: Boolean) {
        dataStore.edit { it[SHOW_HOME_STATS_KEY] = enabled }
    }

    override val shortcutDensity: Flow<ShortcutDensity> = dataStore.data.map { prefs ->
        prefs[SHORTCUT_DENSITY_KEY]?.let { stored ->
            ShortcutDensity.entries.find { it.name == stored }
        } ?: ShortcutDensity.FEW
    }

    override suspend fun setShortcutDensity(density: ShortcutDensity) {
        dataStore.edit { it[SHORTCUT_DENSITY_KEY] = density.name }
    }

    override val homeWallpaper: Flow<String> = dataStore.data.map { prefs ->
        prefs[HOME_WALLPAPER_KEY] ?: ""
    }

    override suspend fun setHomeWallpaper(id: String) {
        dataStore.edit { it[HOME_WALLPAPER_KEY] = id }
    }

    override val showFeed: Flow<Boolean> = dataStore.data.map { it[SHOW_FEED_KEY] ?: true }
    override suspend fun setShowFeed(enabled: Boolean) {
        dataStore.edit { it[SHOW_FEED_KEY] = enabled }
    }

    override val showWeather: Flow<Boolean> = dataStore.data.map { it[SHOW_WEATHER_KEY] ?: true }
    override suspend fun setShowWeather(enabled: Boolean) {
        dataStore.edit { it[SHOW_WEATHER_KEY] = enabled }
    }

    override val showNews: Flow<Boolean> = dataStore.data.map { it[SHOW_NEWS_KEY] ?: false }
    override suspend fun setShowNews(enabled: Boolean) {
        dataStore.edit { it[SHOW_NEWS_KEY] = enabled }
    }

    override val weatherCity: Flow<String> = dataStore.data.map { it[WEATHER_CITY_KEY] ?: "" }
    override suspend fun setWeatherCity(city: String) {
        dataStore.edit { it[WEATHER_CITY_KEY] = city }
    }

    override val weatherUseLocation: Flow<Boolean> =
        dataStore.data.map { it[WEATHER_USE_LOCATION_KEY] ?: false }
    override suspend fun setWeatherUseLocation(enabled: Boolean) {
        dataStore.edit { it[WEATHER_USE_LOCATION_KEY] = enabled }
    }

    private companion object {
        val SHOW_FEED_KEY = booleanPreferencesKey("show_feed")
        val SHOW_WEATHER_KEY = booleanPreferencesKey("show_weather")
        val SHOW_NEWS_KEY = booleanPreferencesKey("show_news")
        val WEATHER_CITY_KEY = stringPreferencesKey("weather_city")
        val WEATHER_USE_LOCATION_KEY = booleanPreferencesKey("weather_use_location")
        val SEARCH_ENGINE_KEY = stringPreferencesKey("search_engine")
        val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
        val JAVASCRIPT_KEY = booleanPreferencesKey("javascript_enabled")
        val COOKIES_KEY = booleanPreferencesKey("cookies_enabled")
        val AD_BLOCK_KEY = booleanPreferencesKey("ad_block_enabled")
        val SAFE_BROWSING_KEY = booleanPreferencesKey("safe_browsing")
        val DISMISS_COOKIE_BANNERS_KEY = booleanPreferencesKey("dismiss_cookie_banners")
        val GPC_ENABLED_KEY = booleanPreferencesKey("gpc_enabled")
        val LIFETIME_BLOCKED_KEY = longPreferencesKey("lifetime_blocked")
        val FORCE_DARK_KEY = booleanPreferencesKey("force_dark_websites")
        val HTTPS_ONLY_KEY = booleanPreferencesKey("https_only")
        val LOCK_INCOGNITO_KEY = booleanPreferencesKey("lock_incognito")
        val AD_ALLOWED_SITES_KEY = stringSetPreferencesKey("ad_allowed_sites")
        val AD_BLOCK_LISTS_KEY = stringSetPreferencesKey("ad_block_lists")
        val AD_BLOCK_LAST_UPDATED_KEY = longPreferencesKey("ad_block_last_updated")
        val AUTO_ISLANDS_KEY = booleanPreferencesKey("auto_islands")
        val SWITCHER_LIST_LAYOUT_KEY = booleanPreferencesKey("switcher_list_layout")
        val USE_SYSTEM_DOWNLOADER_KEY = booleanPreferencesKey("use_system_downloader")
        val BACKGROUND_MEDIA_KEY = booleanPreferencesKey("background_media")
        val BACKGROUND_MEDIA_SITES_KEY = stringSetPreferencesKey("background_media_sites")
        val READER_FONT_SCALE_KEY = intPreferencesKey("reader_font_scale")
        val READER_THEME_KEY = stringPreferencesKey("reader_theme")
        val READER_WIDE_KEY = booleanPreferencesKey("reader_wide")
        val ONBOARDING_DONE_KEY = booleanPreferencesKey("onboarding_done")
        val TEXT_SCALE_KEY = intPreferencesKey("text_scale")
        val ASTEROID_HIGH_SCORE_KEY = intPreferencesKey("asteroid_high_score")
        val SHOW_GREETING_KEY = booleanPreferencesKey("show_greeting")
        val SHOW_HOME_STATS_KEY = booleanPreferencesKey("show_home_stats")
        val SHORTCUT_DENSITY_KEY = stringPreferencesKey("shortcut_density")
        val HOME_WALLPAPER_KEY = stringPreferencesKey("home_wallpaper")
    }
}
