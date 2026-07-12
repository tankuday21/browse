package com.udaytank.browse

import com.udaytank.browse.data.SearchEngine
import com.udaytank.browse.data.SettingsRepository
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

    override suspend fun toggleAdAllowedSite(host: String) {
        adAllowedSites.value =
            if (host in adAllowedSites.value) adAllowedSites.value - host
            else adAllowedSites.value + host
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
}
