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
}
