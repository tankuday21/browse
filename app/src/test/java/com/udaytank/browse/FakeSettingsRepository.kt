package com.udaytank.browse

import com.udaytank.browse.browser.adblock.FilterLists
import com.udaytank.browse.data.ReaderTheme
import com.udaytank.browse.data.SearchEngine
import com.udaytank.browse.data.SettingsRepository
import com.udaytank.browse.data.ShortcutDensity
import com.udaytank.browse.data.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow

class FakeSettingsRepository : SettingsRepository {
    override val searchEngine = MutableStateFlow(SearchEngine.GOOGLE)
    override val themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    override val javaScriptEnabled = MutableStateFlow(true)
    override val cookiesEnabled = MutableStateFlow(true)
    override val adBlockEnabled = MutableStateFlow(true)
    override val adAllowedSites = MutableStateFlow<Set<String>>(emptySet())

    override suspend fun setSearchEngine(engine: SearchEngine) {
        searchEngine.value = engine
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        themeMode.value = mode
    }

    override suspend fun setJavaScriptEnabled(enabled: Boolean) {
        javaScriptEnabled.value = enabled
    }

    override suspend fun setCookiesEnabled(enabled: Boolean) {
        cookiesEnabled.value = enabled
    }

    override suspend fun setAdBlockEnabled(enabled: Boolean) {
        adBlockEnabled.value = enabled
    }

    override val safeBrowsing = MutableStateFlow(true)
    override suspend fun setSafeBrowsing(enabled: Boolean) {
        safeBrowsing.value = enabled
    }

    override val dismissCookieBanners = MutableStateFlow(true)
    override suspend fun setDismissCookieBanners(enabled: Boolean) {
        dismissCookieBanners.value = enabled
    }

    override val gpcEnabled = MutableStateFlow(false)
    override suspend fun setGpcEnabled(enabled: Boolean) {
        gpcEnabled.value = enabled
    }

    override val lifetimeBlocked = MutableStateFlow(0L)
    override suspend fun addBlockedCount(delta: Long) {
        lifetimeBlocked.value += delta
    }

    override suspend fun toggleAdAllowedSite(host: String) {
        adAllowedSites.value =
            if (host in adAllowedSites.value) adAllowedSites.value - host
            else adAllowedSites.value + host
    }

    override val adBlockLists = MutableStateFlow(FilterLists.DEFAULT_ENABLED_IDS)
    override suspend fun toggleAdBlockList(id: String) {
        adBlockLists.value =
            if (id in adBlockLists.value) adBlockLists.value - id
            else adBlockLists.value + id
    }

    override val adBlockLastUpdated = MutableStateFlow(0L)
    override suspend fun setAdBlockLastUpdated(timestamp: Long) {
        adBlockLastUpdated.value = timestamp
    }

    override val forceDarkWebsites = MutableStateFlow(false)
    override suspend fun setForceDarkWebsites(enabled: Boolean) {
        forceDarkWebsites.value = enabled
    }

    override val httpsOnly = MutableStateFlow(false)
    override suspend fun setHttpsOnly(enabled: Boolean) { httpsOnly.value = enabled }

    override val lockIncognito = MutableStateFlow(false)
    override suspend fun setLockIncognito(enabled: Boolean) { lockIncognito.value = enabled }

    override val autoIslands = MutableStateFlow(true)
    override suspend fun setAutoIslands(enabled: Boolean) {
        autoIslands.value = enabled
    }

    override val switcherListLayout = MutableStateFlow(false)
    override suspend fun setSwitcherListLayout(enabled: Boolean) {
        switcherListLayout.value = enabled
    }

    override val useSystemDownloader = MutableStateFlow(false)
    override suspend fun setUseSystemDownloader(enabled: Boolean) {
        useSystemDownloader.value = enabled
    }

    override val backgroundMedia = MutableStateFlow(false)
    override suspend fun setBackgroundMedia(enabled: Boolean) {
        backgroundMedia.value = enabled
    }

    override val backgroundMediaSites = MutableStateFlow<Set<String>>(emptySet())
    override suspend fun setBackgroundMediaSites(sites: Set<String>) {
        backgroundMediaSites.value = sites
    }

    override val readerFontScale = MutableStateFlow(100)
    override suspend fun setReaderFontScale(percent: Int) {
        readerFontScale.value = percent.coerceIn(70, 160)
    }

    override val readerTheme = MutableStateFlow(ReaderTheme.SYSTEM)
    override suspend fun setReaderTheme(theme: ReaderTheme) {
        readerTheme.value = theme
    }

    override val readerWide = MutableStateFlow(false)
    override suspend fun setReaderWide(wide: Boolean) {
        readerWide.value = wide
    }

    override val onboardingDone = MutableStateFlow(false)
    override suspend fun setOnboardingDone(done: Boolean) {
        onboardingDone.value = done
    }

    override val textScale = MutableStateFlow(100)
    override suspend fun setTextScale(percent: Int) {
        textScale.value = percent.coerceIn(50, 200)
    }

    override val asteroidHighScore = MutableStateFlow(0)
    override suspend fun setAsteroidHighScore(score: Int) {
        asteroidHighScore.value = score.coerceAtLeast(0)
    }

    override val showGreeting = MutableStateFlow(false)
    override suspend fun setShowGreeting(enabled: Boolean) {
        showGreeting.value = enabled
    }

    override val showHomeStats = MutableStateFlow(false)
    override suspend fun setShowHomeStats(enabled: Boolean) {
        showHomeStats.value = enabled
    }

    override val shortcutDensity = MutableStateFlow(ShortcutDensity.FEW)
    override suspend fun setShortcutDensity(density: ShortcutDensity) {
        shortcutDensity.value = density
    }

    override val homeWallpaper = MutableStateFlow("")
    override suspend fun setHomeWallpaper(id: String) {
        homeWallpaper.value = id
    }

    override val showFeed = MutableStateFlow(true)
    override suspend fun setShowFeed(enabled: Boolean) { showFeed.value = enabled }
    override val showWeather = MutableStateFlow(true)
    override suspend fun setShowWeather(enabled: Boolean) { showWeather.value = enabled }
    override val showNews = MutableStateFlow(false)
    override suspend fun setShowNews(enabled: Boolean) { showNews.value = enabled }
    override val weatherCity = MutableStateFlow("")
    override suspend fun setWeatherCity(city: String) { weatherCity.value = city }
    override val weatherUseLocation = MutableStateFlow(false)
    override suspend fun setWeatherUseLocation(enabled: Boolean) { weatherUseLocation.value = enabled }

    override val activeOrbitId = MutableStateFlow(0L)
    override suspend fun setActiveOrbitId(id: Long) { activeOrbitId.value = id }
}
