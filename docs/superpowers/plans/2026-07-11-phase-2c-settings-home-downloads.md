# Phase 2c: Settings, Home Page, Basic Downloads Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A settings screen (search engine + theme via DataStore), a native new-tab home page with a bookmarks speed dial (replacing the hardcoded Google home — also kills the home-page history spam from the backlog), and file downloads handed to Android's system DownloadManager.

**Architecture:** `SettingsRepository` interface (DataStore-backed impl, fake for tests) injected into the ViewModel; `UrlInput` gains a search-URL parameter. `HOME_URL` becomes the sentinel `browse://home` — the browser screen renders a native Compose `HomePage` for it instead of mounting a WebView; entering a URL on a home tab rewrites the tab's URL (which mounts the WebView) rather than issuing a command. Downloads: `DownloadListener` on each WebView enqueues into the system DownloadManager (system notification + Downloads folder, zero permissions needed on API 26+... 29+; on 26–28 legacy external storage applies to the public dir via the manager itself — handled by the system service).

**Tech Stack:** + `androidx.datastore:datastore-preferences`.

## Global Constraints

- Branch `feature/settings-home-downloads`, merge `--no-ff`, push
- Existing MVVM rules; PowerShell from `F:\Dev\Browse` with `$env:JAVA_HOME` set
- `SearchEngine` defaults to Google; theme defaults to system
- Home tab: address bar shows empty text, star disabled, nav flags reset

---

### Task 1: Settings Foundation (DataStore, SearchEngine, ThemeMode, UrlInput param)

**Files:**
- Modify: `gradle/libs.versions.toml`, `app/build.gradle.kts` (add `androidx-datastore-preferences` 1.1.1)
- Create: `app/src/main/java/com/udaytank/browse/data/SettingsRepository.kt`
- Modify: `app/src/main/java/com/udaytank/browse/BrowseApplication.kt` (expose `settingsRepository`)
- Modify: `app/src/main/java/com/udaytank/browse/browser/UrlInput.kt`
- Test: extend `UrlInputTest`; create `app/src/test/java/com/udaytank/browse/FakeSettingsRepository.kt`

**Interfaces:**
- `enum class SearchEngine(val label: String, val queryUrl: String)` — GOOGLE, DUCKDUCKGO, BING
- `enum class ThemeMode { SYSTEM, LIGHT, DARK }`
- `interface SettingsRepository { val searchEngine: Flow<SearchEngine>; val themeMode: Flow<ThemeMode>; suspend fun setSearchEngine(SearchEngine); suspend fun setThemeMode(ThemeMode) }`
- `UrlInput.toLoadableUrl(input: String, searchUrl: String = SearchEngine.GOOGLE.queryUrl)`

`SettingsRepository.kt`:

```kotlin
package com.udaytank.browse.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
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
    suspend fun setSearchEngine(engine: SearchEngine)
    suspend fun setThemeMode(mode: ThemeMode)
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

    override suspend fun setSearchEngine(engine: SearchEngine) {
        dataStore.edit { it[SEARCH_ENGINE_KEY] = engine.name }
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[THEME_MODE_KEY] = mode.name }
    }

    private companion object {
        val SEARCH_ENGINE_KEY = stringPreferencesKey("search_engine")
        val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
    }
}
```

`BrowseApplication` additions:

```kotlin
private val Context.settingsDataStore by preferencesDataStore(name = "settings")
// inside class:
val settingsRepository: SettingsRepository by lazy { DataStoreSettingsRepository(settingsDataStore) }
```

`UrlInput` change: `SEARCH_URL` const removed; signature `fun toLoadableUrl(input: String, searchUrl: String = SearchEngine.GOOGLE.queryUrl)`; the search branch becomes `searchUrl + URLEncoder.encode(text, "UTF-8")`.

New `UrlInputTest` case:

```kotlin
@Test
fun `custom search engine url is used`() {
    assertEquals(
        "https://duckduckgo.com/?q=pizza",
        UrlInput.toLoadableUrl("pizza", SearchEngine.DUCKDUCKGO.queryUrl)
    )
}
```

`FakeSettingsRepository.kt` (test sources):

```kotlin
package com.udaytank.browse

import com.udaytank.browse.data.SearchEngine
import com.udaytank.browse.data.SettingsRepository
import com.udaytank.browse.data.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow

class FakeSettingsRepository : SettingsRepository {
    override val searchEngine = MutableStateFlow(SearchEngine.GOOGLE)
    override val themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    override suspend fun setSearchEngine(engine: SearchEngine) { searchEngine.value = engine }
    override suspend fun setThemeMode(mode: ThemeMode) { themeMode.value = mode }
}
```

Verify: `.\gradlew.bat testDebugUnitTest` (compile will need Task 2's VM change if constructor is touched — Tasks 1–3 may be committed together if interdependent; prefer Task 1 standalone: UrlInput default keeps old callers compiling). Commit: `feat: settings repository with search engine and theme`.

---

### Task 2: ViewModel — Settings Flows and Home-Tab Loading

**Files:** modify `BrowserViewModel.kt`, `BrowserViewModelTest.kt`

- Constructor: `BrowserViewModel(historyDao, bookmarkDao, tabDao, settings: SettingsRepository)`; Factory passes `app.settingsRepository`
- `HOME_URL = "browse://home"`
- New: `val searchEngine: StateFlow<SearchEngine>`, `val themeMode: StateFlow<ThemeMode>`, `fun onSearchEngineSelected(SearchEngine)`, `fun onThemeSelected(ThemeMode)`
- `stateIn(viewModelScope, SharingStarted.Eagerly, default)` for both (Eagerly so `searchEngine.value` is fresh when Go is pressed)
- Private `loadInActiveTab(url)`: home tab → `tabManager.onContentChanged(tab.id, url, url)`; otherwise pendingCommand LoadUrl. `onGoPressed` and `onOpenUrl` route through it; `onGoPressed` passes `searchEngine.value.queryUrl`
- Active-tab sync collector: home tab → `addressBarText = ""`, `currentUrl = null`, `isLoading = false`, `canGoBack/Forward = false`

Test updates: `vm()` helper gains `settings: FakeSettingsRepository = FakeSettingsRepository()`; `startup creates an active home tab` now asserts `""` address bar and null currentUrl; new tests:

```kotlin
@Test
fun `go on a home tab loads by rewriting the tab url`() {
    val vm = vm()
    vm.onAddressBarTextChanged("bbc.com")
    vm.onGoPressed()
    assertNull(vm.uiState.value.pendingCommand)
    assertEquals("https://bbc.com", vm.tabs.value.first().url)
}

@Test
fun `go on a web tab issues a load command`() {
    val vm = vm()
    val tabId = vm.activeTabId.value!!
    vm.onPageStarted(tabId, "https://bbc.com")
    vm.onAddressBarTextChanged("cnn.com")
    vm.onGoPressed()
    assertEquals(BrowserCommand.LoadUrl("https://cnn.com"), vm.uiState.value.pendingCommand)
}

@Test
fun `selected search engine drives searches`() {
    val settings = FakeSettingsRepository()
    val vm = vm(settings = settings)
    vm.onSearchEngineSelected(SearchEngine.DUCKDUCKGO)
    vm.onAddressBarTextChanged("pizza")
    vm.onGoPressed()
    assertEquals("https://duckduckgo.com/?q=pizza", vm.tabs.value.first().url)
}
```

(`consuming a command clears it` changes: `onGoPressed` on home tab no longer sets a command — rewrite that test to use `onReloadPressed` instead.)

Commit: `feat: search engine setting drives searches; home-tab loading`.

---

### Task 3: Native Home Page

**Files:** create `ui/HomePage.kt`; modify `ui/BrowserScreen.kt`

`HomePage.kt` — speed dial from bookmarks:

```kotlin
package com.udaytank.browse.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.udaytank.browse.data.Bookmark

@Composable
fun HomePage(
    bookmarks: List<Bookmark>,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Browse",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 48.dp, bottom = 32.dp),
        )
        if (bookmarks.isEmpty()) {
            Text(
                "Star pages to see them here",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(bookmarks.take(8), key = { it.id }) { bookmark ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable { onOpenUrl(bookmark.url) },
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                bookmark.title.take(1).uppercase(),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                        Text(
                            bookmark.title,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
    }
}
```

`BrowserScreen` content block: home tab renders `HomePage` instead of `TabWebView`:

```kotlin
val currentTabId = activeTabId
if (currentTabId != null) {
    val activeTab = tabs.find { it.id == currentTabId }
    if (activeTab == null || activeTab.url == BrowserViewModel.HOME_URL) {
        HomePage(
            bookmarks = bookmarksList,
            onOpenUrl = viewModel::onOpenUrl,
            modifier = Modifier.fillMaxSize().padding(innerPadding),
        )
    } else {
        TabWebView(/* as before, tabUrl = activeTab.url */)
    }
}
```

(`bookmarksList` = `viewModel.bookmarks.collectAsStateWithLifecycle()`.)

Commit: `feat: native home page with bookmarks speed dial`.

---

### Task 4: Basic Downloads

**Files:** modify `ui/WebViewHolder.kt`

Inside `obtain`'s `apply` block add:

```kotlin
setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
    val fileName = URLUtil.guessFileName(url, contentDisposition, mimetype)
    val request = DownloadManager.Request(Uri.parse(url)).apply {
        setMimeType(mimetype)
        addRequestHeader("Cookie", CookieManager.getInstance().getCookie(url))
        addRequestHeader("User-Agent", userAgent)
        setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
        setTitle(fileName)
    }
    (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
    Toast.makeText(context, "Downloading $fileName", Toast.LENGTH_SHORT).show()
}
```

Imports: `android.app.DownloadManager`, `android.net.Uri`, `android.os.Environment`, `android.webkit.CookieManager`, `android.webkit.URLUtil`, `android.widget.Toast`.

Commit: `feat: downloads via system DownloadManager`.

---

### Task 5: Settings Screen + Theme Wiring

**Files:** create `ui/SettingsScreen.kt`; modify `MainActivity.kt`

`SettingsScreen.kt` — radio groups for search engine and theme:

```kotlin
package com.udaytank.browse.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.udaytank.browse.BrowserViewModel
import com.udaytank.browse.data.SearchEngine
import com.udaytank.browse.data.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: BrowserViewModel, onBack: () -> Unit) {
    val engine by viewModel.searchEngine.collectAsStateWithLifecycle()
    val theme by viewModel.themeMode.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).verticalScroll(rememberScrollState()),
        ) {
            Text(
                "Search engine",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(16.dp),
            )
            SearchEngine.entries.forEach { option ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(selected = engine == option, onClick = { viewModel.onSearchEngineSelected(option) })
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    RadioButton(selected = engine == option, onClick = null)
                    Text(option.label, modifier = Modifier.padding(start = 8.dp))
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text(
                "Theme",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(16.dp),
            )
            ThemeMode.entries.forEach { option ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(selected = theme == option, onClick = { viewModel.onThemeSelected(option) })
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    RadioButton(selected = theme == option, onClick = null)
                    Text(
                        option.name.lowercase().replaceFirstChar { it.uppercase() },
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}
```

`MainActivity`: collect `themeMode`, drive `BrowseTheme(darkTheme = ...)`; add `"settings"` route and a Settings item in the browser ⋮ menu (`BrowserScreen` gains `onOpenSettings: () -> Unit` and a `DropdownMenuItem("Settings")`).

```kotlin
val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
BrowseTheme(
    darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
) { ... }
```

Build + full unit suite. Commit: `feat: settings screen with theme wiring`.

---

### Task 6: Acceptance, Merge, Push

1. Fresh new tab → native **Browse home page** (no Google load, no history entry for it)
2. Star two pages → they appear as speed-dial circles on the home page; tapping one opens it
3. On the home tab: type `bbc.com` → Go → loads normally (tab leaves home mode)
4. Settings → switch search engine to DuckDuckGo → search from address bar uses DuckDuckGo; **kill app, reopen** → still DuckDuckGo (DataStore persisted)
5. Settings → Dark theme → app immediately dark; survives restart
6. Download a file (e.g. a PDF link or test file) → system notification shows progress; file lands in Downloads
7. History no longer gains an entry per new tab
8. Regression sweep: tabs switch/close/restore, history dedup, bookmarks toggle

Merge:
```powershell
git checkout main
git merge --no-ff feature/settings-home-downloads -m "Merge feature/settings-home-downloads: Phase 2c"
git push
git branch -d feature/settings-home-downloads
```
