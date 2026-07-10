# Phase 2a: History & Bookmarks Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Every page visit is recorded to a local database with smart deduplication; users can browse/delete history grouped by day, and star/unstar pages as bookmarks with dedicated screens for both.

**Architecture:** Room database (SQLite) with two entities + DAOs, owned by a `BrowseApplication` singleton and injected into `BrowserViewModel` via a ViewModel factory. Navigation between the browser, history, and bookmarks screens via Navigation-Compose, all sharing the one activity-scoped `BrowserViewModel`. History recording is gated by a pure, unit-tested `VisitPolicy`.

**Tech Stack:** Room 2.6.1 (with KSP compiler), Navigation-Compose, kotlinx-coroutines-test, Material icons extended.

**Learning mode:** Steps marked **👤 OWNER WRITES** are written by Uday with guidance; reference implementation included for verification only.

## Global Constraints

- Package `com.udaytank.browse`; existing MVVM/one-`StateFlow` architecture (spec §4) — no browser logic in composables
- This plan runs on branch `feature/history-and-bookmarks`, merged to `main` with `--no-ff` at the end
- Every task ends with tests passing and a commit; push after merge
- PowerShell from `F:\Dev\Browse`; once per terminal: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"`
- Instrumented (device) tests require the emulator running
- Known accepted limitation (fixed in Phase 2b's tab architecture): navigating away from the browser screen recreates the WebView; we reload `currentUrl` but scroll position and in-page history are lost.

---

### Task 1: Branch, Dependencies, and Application Class

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/com/udaytank/browse/BrowseApplication.kt`

**Interfaces:**
- Produces: `BrowseApplication.database: BrowseDatabase` (used by Task 4's ViewModel factory); Gradle coordinates for Room/KSP/Navigation available to all later tasks. (`BrowseDatabase` itself arrives in Task 2 — this task's build check therefore uses a placeholder-free subset: add deps and Application class WITHOUT the database property, which is added in Task 2.)

- [ ] **Step 1: Create the feature branch**

```powershell
git checkout -b feature/history-and-bookmarks
```

- [ ] **Step 2: Add versions and libraries to `gradle/libs.versions.toml`**

Under `[versions]` add:

```toml
room = "2.6.1"
ksp = "2.0.21-1.0.28"
navigationCompose = "2.8.5"
coroutinesTest = "1.9.0"
```

Under `[libraries]` add:

```toml
androidx-room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
androidx-room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
androidx-room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
androidx-navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigationCompose" }
androidx-compose-material-icons-extended = { group = "androidx.compose.material", name = "material-icons-extended" }
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutinesTest" }
```

Under `[plugins]` add:

```toml
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

- [ ] **Step 3: Wire them into `app/build.gradle.kts`**

In the `plugins { }` block add:

```kotlin
alias(libs.plugins.ksp)
```

In `dependencies { }` add:

```kotlin
implementation(libs.androidx.room.runtime)
implementation(libs.androidx.room.ktx)
ksp(libs.androidx.room.compiler)
implementation(libs.androidx.navigation.compose)
implementation(libs.androidx.compose.material.icons.extended)
testImplementation(libs.kotlinx.coroutines.test)
```

- [ ] **Step 4: Create the Application class**

Create `app/src/main/java/com/udaytank/browse/BrowseApplication.kt`:

```kotlin
package com.udaytank.browse

import android.app.Application

class BrowseApplication : Application()
```

Register it in `app/src/main/AndroidManifest.xml` — add to the `<application>` tag:

```xml
android:name=".BrowseApplication"
```

- [ ] **Step 5: Build check**

Run: `.\gradlew.bat assembleDebug`
Expected: `BUILD SUCCESSFUL` (first KSP run downloads the plugin).

- [ ] **Step 6: Commit**

```powershell
git add -A
git commit -m "chore: add Room, KSP, Navigation-Compose deps and Application class"
```

---

### Task 2: Room Database — Entities, DAOs, Instrumented Tests

**Files:**
- Create: `app/src/main/java/com/udaytank/browse/data/HistoryEntry.kt`
- Create: `app/src/main/java/com/udaytank/browse/data/Bookmark.kt`
- Create: `app/src/main/java/com/udaytank/browse/data/HistoryDao.kt`
- Create: `app/src/main/java/com/udaytank/browse/data/BookmarkDao.kt`
- Create: `app/src/main/java/com/udaytank/browse/data/BrowseDatabase.kt`
- Modify: `app/src/main/java/com/udaytank/browse/BrowseApplication.kt`
- Test: `app/src/androidTest/java/com/udaytank/browse/data/BrowseDatabaseTest.kt`

**Interfaces:**
- Produces (used by Tasks 3–5):
  - `HistoryEntry(id: Long = 0, url: String, title: String, visitedAt: Long)`
  - `Bookmark(id: Long = 0, url: String, title: String, createdAt: Long)`
  - `HistoryDao`: `suspend insert(HistoryEntry)`, `observeAll(): Flow<List<HistoryEntry>>`, `suspend mostRecent(): HistoryEntry?`, `suspend deleteById(Long)`, `suspend clearAll()`
  - `BookmarkDao`: `suspend insert(Bookmark)` (IGNORE on duplicate url), `observeAll(): Flow<List<Bookmark>>`, `observeIsBookmarked(url: String): Flow<Boolean>`, `suspend deleteByUrl(String)`
  - `BrowseApplication.database: BrowseDatabase` with `historyDao()` / `bookmarkDao()`

- [ ] **Step 1: Create the entities**

`app/src/main/java/com/udaytank/browse/data/HistoryEntry.kt`:

```kotlin
package com.udaytank.browse.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "history")
data class HistoryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String,
    val visitedAt: Long,
)
```

`app/src/main/java/com/udaytank/browse/data/Bookmark.kt`:

```kotlin
package com.udaytank.browse.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "bookmarks", indices = [Index(value = ["url"], unique = true)])
data class Bookmark(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String,
    val createdAt: Long,
)
```

- [ ] **Step 2: Create the DAOs**

`app/src/main/java/com/udaytank/browse/data/HistoryDao.kt`:

```kotlin
package com.udaytank.browse.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Insert
    suspend fun insert(entry: HistoryEntry)

    @Query("SELECT * FROM history ORDER BY visitedAt DESC")
    fun observeAll(): Flow<List<HistoryEntry>>

    @Query("SELECT * FROM history ORDER BY visitedAt DESC LIMIT 1")
    suspend fun mostRecent(): HistoryEntry?

    @Query("DELETE FROM history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM history")
    suspend fun clearAll()
}
```

`app/src/main/java/com/udaytank/browse/data/BookmarkDao.kt`:

```kotlin
package com.udaytank.browse.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(bookmark: Bookmark)

    @Query("SELECT * FROM bookmarks ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<Bookmark>>

    @Query("SELECT EXISTS(SELECT 1 FROM bookmarks WHERE url = :url)")
    fun observeIsBookmarked(url: String): Flow<Boolean>

    @Query("DELETE FROM bookmarks WHERE url = :url")
    suspend fun deleteByUrl(url: String)
}
```

- [ ] **Step 3: Create the database and expose it from the Application**

`app/src/main/java/com/udaytank/browse/data/BrowseDatabase.kt`:

```kotlin
package com.udaytank.browse.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [HistoryEntry::class, Bookmark::class], version = 1)
abstract class BrowseDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
    abstract fun bookmarkDao(): BookmarkDao
}
```

Replace `app/src/main/java/com/udaytank/browse/BrowseApplication.kt` with:

```kotlin
package com.udaytank.browse

import android.app.Application
import androidx.room.Room
import com.udaytank.browse.data.BrowseDatabase

class BrowseApplication : Application() {
    val database: BrowseDatabase by lazy {
        Room.databaseBuilder(this, BrowseDatabase::class.java, "browse.db").build()
    }
}
```

- [ ] **Step 4: Write the instrumented DAO tests**

Create `app/src/androidTest/java/com/udaytank/browse/data/BrowseDatabaseTest.kt`:

```kotlin
package com.udaytank.browse.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrowseDatabaseTest {

    private lateinit var db: BrowseDatabase

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            BrowseDatabase::class.java
        ).build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun historyInsertAndReadNewestFirst() = runBlocking {
        db.historyDao().insert(HistoryEntry(url = "https://a.com", title = "A", visitedAt = 1))
        db.historyDao().insert(HistoryEntry(url = "https://b.com", title = "B", visitedAt = 2))
        val all = db.historyDao().observeAll().first()
        assertEquals(listOf("https://b.com", "https://a.com"), all.map { it.url })
        assertEquals("https://b.com", db.historyDao().mostRecent()?.url)
    }

    @Test
    fun historyClearAll() = runBlocking {
        db.historyDao().insert(HistoryEntry(url = "https://a.com", title = "A", visitedAt = 1))
        db.historyDao().clearAll()
        assertTrue(db.historyDao().observeAll().first().isEmpty())
    }

    @Test
    fun bookmarkDuplicateUrlIsIgnored() = runBlocking {
        db.bookmarkDao().insert(Bookmark(url = "https://a.com", title = "A", createdAt = 1))
        db.bookmarkDao().insert(Bookmark(url = "https://a.com", title = "A again", createdAt = 2))
        assertEquals(1, db.bookmarkDao().observeAll().first().size)
    }

    @Test
    fun bookmarkToggleLifecycle() = runBlocking {
        assertFalse(db.bookmarkDao().observeIsBookmarked("https://a.com").first())
        db.bookmarkDao().insert(Bookmark(url = "https://a.com", title = "A", createdAt = 1))
        assertTrue(db.bookmarkDao().observeIsBookmarked("https://a.com").first())
        db.bookmarkDao().deleteByUrl("https://a.com")
        assertFalse(db.bookmarkDao().observeIsBookmarked("https://a.com").first())
    }
}
```

- [ ] **Step 5: Run instrumented tests (emulator must be running)**

Run: `.\gradlew.bat connectedDebugAndroidTest`
Expected: `BUILD SUCCESSFUL`, 4 tests passed (plus the wizard's ExampleInstrumentedTest). Report at `app/build/reports/androidTests/connected/`.

- [ ] **Step 6: Commit**

```powershell
git add -A
git commit -m "feat: Room database with history and bookmark DAOs"
```

---

### Task 3: VisitPolicy — 👤 OWNER WRITES

Decides which page visits deserve a history entry. Pure Kotlin, fully unit-tested.

**Files:**
- Create: `app/src/main/java/com/udaytank/browse/browser/VisitPolicy.kt`
- Test: `app/src/test/java/com/udaytank/browse/browser/VisitPolicyTest.kt`

**Interfaces:**
- Consumes: `HistoryEntry` (Task 2)
- Produces: `object VisitPolicy { fun shouldRecord(previous: HistoryEntry?, url: String, visitedAt: Long): Boolean }` — Task 4's ViewModel calls exactly this before inserting.

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/java/com/udaytank/browse/browser/VisitPolicyTest.kt`:

```kotlin
package com.udaytank.browse.browser

import com.udaytank.browse.data.HistoryEntry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VisitPolicyTest {

    private fun prev(url: String, at: Long) = HistoryEntry(url = url, title = "t", visitedAt = at)

    @Test
    fun `first ever visit is recorded`() {
        assertTrue(VisitPolicy.shouldRecord(null, "https://a.com", 1_000L))
    }

    @Test
    fun `different url is always recorded`() {
        assertTrue(VisitPolicy.shouldRecord(prev("https://a.com", 1_000L), "https://b.com", 2_000L))
    }

    @Test
    fun `same url within 5 seconds is skipped (reload spam)`() {
        assertFalse(VisitPolicy.shouldRecord(prev("https://a.com", 1_000L), "https://a.com", 4_000L))
    }

    @Test
    fun `same url after 5 seconds is recorded again`() {
        assertTrue(VisitPolicy.shouldRecord(prev("https://a.com", 1_000L), "https://a.com", 7_000L))
    }

    @Test
    fun `blank pages are never recorded`() {
        assertFalse(VisitPolicy.shouldRecord(null, "about:blank", 1_000L))
        assertFalse(VisitPolicy.shouldRecord(null, "", 1_000L))
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.udaytank.browse.browser.VisitPolicyTest"`
Expected: FAIL — `Unresolved reference: VisitPolicy`.

- [ ] **Step 3: 👤 OWNER WRITES — implement `shouldRecord`**

Create `app/src/main/java/com/udaytank/browse/browser/VisitPolicy.kt` skeleton:

```kotlin
package com.udaytank.browse.browser

import com.udaytank.browse.data.HistoryEntry

object VisitPolicy {

    private const val DUPLICATE_WINDOW_MS = 5_000L

    /**
     * Should this visit be written to history?
     * Skip junk (blank pages) and reload spam (same URL within the window).
     */
    fun shouldRecord(previous: HistoryEntry?, url: String, visitedAt: Long): Boolean {
        // 👤 OWNER WRITES (~5 lines). Tests define the behavior. Hints:
        //  - Handle the "never record" urls first
        //  - previous is nullable: previous?.url compares safely against url
        //  - Time since last visit: visitedAt - previous.visitedAt
        TODO()
    }
}
```

Reference implementation (executor: reveal only after the owner's attempt):

```kotlin
fun shouldRecord(previous: HistoryEntry?, url: String, visitedAt: Long): Boolean {
    if (url.isBlank() || url == "about:blank") return false
    if (previous == null || previous.url != url) return true
    return visitedAt - previous.visitedAt >= DUPLICATE_WINDOW_MS
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.udaytank.browse.browser.VisitPolicyTest"`
Expected: PASS — 5 tests, 0 failures.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/com/udaytank/browse/browser/VisitPolicy.kt app/src/test/java/com/udaytank/browse/browser/VisitPolicyTest.kt
git commit -m "feat: visit policy gates history recording"
```

---

### Task 4: ViewModel Integration — Recording, Bookmark Toggle, Factory

**Files:**
- Modify: `app/src/main/java/com/udaytank/browse/BrowserViewModel.kt` (full replacement below)
- Modify: `app/src/main/java/com/udaytank/browse/ui/WebViewContainer.kt` (onPageFinished now passes url+title)
- Modify: `app/src/main/java/com/udaytank/browse/MainActivity.kt` (use factory)
- Test: `app/src/test/java/com/udaytank/browse/BrowserViewModelTest.kt` (full replacement below)
- Create: `app/src/test/java/com/udaytank/browse/FakeDaos.kt`
- Create: `app/src/test/java/com/udaytank/browse/MainDispatcherRule.kt`

**Interfaces:**
- Consumes: `HistoryDao`, `BookmarkDao`, `HistoryEntry`, `Bookmark` (Task 2), `VisitPolicy` (Task 3)
- Produces (used by Task 5):
  - `BrowserViewModel(historyDao: HistoryDao, bookmarkDao: BookmarkDao)` + `BrowserViewModel.Factory`
  - New VM members: `historyEntries: StateFlow<List<HistoryEntry>>`, `bookmarks: StateFlow<List<Bookmark>>`, `isBookmarked: StateFlow<Boolean>`, `onToggleBookmark()`, `onOpenUrl(url: String)`, `onDeleteHistoryEntry(id: Long)`, `onClearHistory()`, `onDeleteBookmark(url: String)`
  - Changed signature: `onPageFinished(url: String, title: String?)`
  - `WebViewContainer` gains `currentUrl: String?` param and reloads it when a fresh WebView instance has nothing loaded

- [ ] **Step 1: Replace `BrowserViewModel.kt`**

```kotlin
package com.udaytank.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.udaytank.browse.browser.BrowserCommand
import com.udaytank.browse.browser.UrlInput
import com.udaytank.browse.browser.VisitPolicy
import com.udaytank.browse.data.Bookmark
import com.udaytank.browse.data.BookmarkDao
import com.udaytank.browse.data.HistoryDao
import com.udaytank.browse.data.HistoryEntry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BrowserUiState(
    val addressBarText: String = "",
    val currentUrl: String? = null,
    val isLoading: Boolean = false,
    val progress: Int = 0,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val pendingCommand: BrowserCommand? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class BrowserViewModel(
    private val historyDao: HistoryDao,
    private val bookmarkDao: BookmarkDao,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        BrowserUiState(pendingCommand = BrowserCommand.LoadUrl(HOME_URL))
    )
    val uiState: StateFlow<BrowserUiState> = _uiState.asStateFlow()

    val historyEntries: StateFlow<List<HistoryEntry>> = historyDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val bookmarks: StateFlow<List<Bookmark>> = bookmarkDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val isBookmarked: StateFlow<Boolean> = uiState
        .map { it.currentUrl }
        .distinctUntilChanged()
        .flatMapLatest { url -> if (url == null) flowOf(false) else bookmarkDao.observeIsBookmarked(url) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    // --- events from the UI ---

    fun onAddressBarTextChanged(text: String) =
        _uiState.update { it.copy(addressBarText = text) }

    fun onGoPressed() = _uiState.update {
        it.copy(pendingCommand = BrowserCommand.LoadUrl(UrlInput.toLoadableUrl(it.addressBarText)))
    }

    fun onOpenUrl(url: String) =
        _uiState.update { it.copy(pendingCommand = BrowserCommand.LoadUrl(url)) }

    fun onBackPressed() = _uiState.update { it.copy(pendingCommand = BrowserCommand.GoBack) }
    fun onForwardPressed() = _uiState.update { it.copy(pendingCommand = BrowserCommand.GoForward) }
    fun onReloadPressed() = _uiState.update { it.copy(pendingCommand = BrowserCommand.Reload) }

    fun onCommandConsumed() = _uiState.update { it.copy(pendingCommand = null) }

    fun onToggleBookmark() {
        val state = _uiState.value
        val url = state.currentUrl ?: return
        viewModelScope.launch {
            if (isBookmarked.value) {
                bookmarkDao.deleteByUrl(url)
            } else {
                bookmarkDao.insert(
                    Bookmark(url = url, title = state.addressBarText, createdAt = System.currentTimeMillis())
                )
            }
        }
    }

    fun onDeleteHistoryEntry(id: Long) {
        viewModelScope.launch { historyDao.deleteById(id) }
    }

    fun onClearHistory() {
        viewModelScope.launch { historyDao.clearAll() }
    }

    fun onDeleteBookmark(url: String) {
        viewModelScope.launch { bookmarkDao.deleteByUrl(url) }
    }

    // --- callbacks from the WebView ---

    fun onPageStarted(url: String) = _uiState.update {
        it.copy(currentUrl = url, addressBarText = url, isLoading = true, progress = 0)
    }

    fun onProgressChanged(percent: Int) = _uiState.update { it.copy(progress = percent) }

    fun onPageFinished(url: String, title: String?) {
        _uiState.update { it.copy(isLoading = false) }
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            if (VisitPolicy.shouldRecord(historyDao.mostRecent(), url, now)) {
                historyDao.insert(HistoryEntry(url = url, title = title ?: url, visitedAt = now))
            }
        }
    }

    fun onHistoryChanged(canGoBack: Boolean, canGoForward: Boolean) =
        _uiState.update { it.copy(canGoBack = canGoBack, canGoForward = canGoForward) }

    companion object {
        const val HOME_URL = "https://www.google.com"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as BrowseApplication
                BrowserViewModel(app.database.historyDao(), app.database.bookmarkDao())
            }
        }
    }
}
```

- [ ] **Step 2: Update `WebViewContainer.kt`**

Change the signature — add `currentUrl` and change `onPageFinished`:

```kotlin
fun WebViewContainer(
    pendingCommand: BrowserCommand?,
    currentUrl: String?,
    onCommandConsumed: () -> Unit,
    onPageStarted: (String) -> Unit,
    onProgressChanged: (Int) -> Unit,
    onPageFinished: (url: String, title: String?) -> Unit,
    onHistoryChanged: (canGoBack: Boolean, canGoForward: Boolean) -> Unit,
    modifier: Modifier = Modifier,
)
```

Inside the `WebViewClient`, change:

```kotlin
override fun onPageFinished(view: WebView, url: String) {
    onPageFinished(url, view.title)
}
```

Replace the `update` block with:

```kotlin
update = { webView ->
    when (pendingCommand) {
        is BrowserCommand.LoadUrl -> webView.loadUrl(pendingCommand.url)
        BrowserCommand.GoBack -> if (webView.canGoBack()) webView.goBack()
        BrowserCommand.GoForward -> if (webView.canGoForward()) webView.goForward()
        BrowserCommand.Reload -> webView.reload()
        null -> if (webView.url == null && currentUrl != null) webView.loadUrl(currentUrl)
    }
    if (pendingCommand != null) onCommandConsumed()
},
```

(The `null ->` branch restores the page when Compose recreates the WebView — after rotation or returning from another screen.)

- [ ] **Step 3: Use the factory in `MainActivity.kt`**

Change the viewModel property to:

```kotlin
private val viewModel: BrowserViewModel by viewModels { BrowserViewModel.Factory }
```

(Import stays `androidx.activity.viewModels`.)

- [ ] **Step 4: Create test helpers**

`app/src/test/java/com/udaytank/browse/MainDispatcherRule.kt`:

```kotlin
package com.udaytank.browse

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule : TestWatcher() {
    private val dispatcher = UnconfinedTestDispatcher()
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)
    override fun finished(description: Description) = Dispatchers.resetMain()
}
```

`app/src/test/java/com/udaytank/browse/FakeDaos.kt`:

```kotlin
package com.udaytank.browse

import com.udaytank.browse.data.Bookmark
import com.udaytank.browse.data.BookmarkDao
import com.udaytank.browse.data.HistoryDao
import com.udaytank.browse.data.HistoryEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeHistoryDao : HistoryDao {
    val entries = MutableStateFlow<List<HistoryEntry>>(emptyList())

    override suspend fun insert(entry: HistoryEntry) {
        entries.value = entries.value + entry.copy(id = (entries.value.size + 1).toLong())
    }

    override fun observeAll(): Flow<List<HistoryEntry>> =
        entries.map { it.sortedByDescending { e -> e.visitedAt } }

    override suspend fun mostRecent(): HistoryEntry? =
        entries.value.maxByOrNull { it.visitedAt }

    override suspend fun deleteById(id: Long) {
        entries.value = entries.value.filterNot { it.id == id }
    }

    override suspend fun clearAll() {
        entries.value = emptyList()
    }
}

class FakeBookmarkDao : BookmarkDao {
    val bookmarks = MutableStateFlow<List<Bookmark>>(emptyList())

    override suspend fun insert(bookmark: Bookmark) {
        if (bookmarks.value.none { it.url == bookmark.url }) {
            bookmarks.value = bookmarks.value + bookmark
        }
    }

    override fun observeAll(): Flow<List<Bookmark>> = bookmarks

    override fun observeIsBookmarked(url: String): Flow<Boolean> =
        bookmarks.map { list -> list.any { it.url == url } }

    override suspend fun deleteByUrl(url: String) {
        bookmarks.value = bookmarks.value.filterNot { it.url == url }
    }
}
```

- [ ] **Step 5: Replace `BrowserViewModelTest.kt`**

```kotlin
package com.udaytank.browse

import com.udaytank.browse.browser.BrowserCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class BrowserViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun vm(
        historyDao: FakeHistoryDao = FakeHistoryDao(),
        bookmarkDao: FakeBookmarkDao = FakeBookmarkDao(),
    ) = BrowserViewModel(historyDao, bookmarkDao)

    @Test
    fun `typing updates address bar text`() {
        val vm = vm()
        vm.onAddressBarTextChanged("bbc.com")
        assertEquals("bbc.com", vm.uiState.value.addressBarText)
    }

    @Test
    fun `go pressed emits LoadUrl command with normalized url`() {
        val vm = vm()
        vm.onAddressBarTextChanged("bbc.com")
        vm.onGoPressed()
        assertEquals(BrowserCommand.LoadUrl("https://bbc.com"), vm.uiState.value.pendingCommand)
    }

    @Test
    fun `consuming a command clears it`() {
        val vm = vm()
        vm.onGoPressed()
        vm.onCommandConsumed()
        assertNull(vm.uiState.value.pendingCommand)
    }

    @Test
    fun `page start sets loading and syncs address bar`() {
        val vm = vm()
        vm.onPageStarted("https://bbc.com/news")
        assertTrue(vm.uiState.value.isLoading)
        assertEquals("https://bbc.com/news", vm.uiState.value.addressBarText)
        assertEquals("https://bbc.com/news", vm.uiState.value.currentUrl)
    }

    @Test
    fun `page finish clears loading and records history`() {
        val history = FakeHistoryDao()
        val vm = vm(historyDao = history)
        vm.onPageStarted("https://bbc.com")
        vm.onPageFinished("https://bbc.com", "BBC")
        assertFalse(vm.uiState.value.isLoading)
        assertEquals(1, history.entries.value.size)
        assertEquals("BBC", history.entries.value.first().title)
    }

    @Test
    fun `reload spam records history only once`() {
        val history = FakeHistoryDao()
        val vm = vm(historyDao = history)
        vm.onPageFinished("https://bbc.com", "BBC")
        vm.onPageFinished("https://bbc.com", "BBC")
        assertEquals(1, history.entries.value.size)
    }

    @Test
    fun `toggle bookmark adds bookmark for current page`() {
        val bookmarks = FakeBookmarkDao()
        val vm = vm(bookmarkDao = bookmarks)
        vm.onPageStarted("https://bbc.com")
        vm.onToggleBookmark()
        assertEquals(1, bookmarks.bookmarks.value.size)
    }

    @Test
    fun `history change updates nav button state`() {
        val vm = vm()
        vm.onHistoryChanged(canGoBack = true, canGoForward = false)
        assertTrue(vm.uiState.value.canGoBack)
        assertFalse(vm.uiState.value.canGoForward)
    }
}
```

Note on the bookmark toggle test: with `WhileSubscribed`, `isBookmarked` only tracks the DB while something collects it, so the unit test asserts the add path; the remove path is covered by the DAO test (`bookmarkToggleLifecycle`) and manually in Task 6.

- [ ] **Step 6: Run all unit tests**

Run: `.\gradlew.bat testDebugUnitTest`
Expected: PASS — UrlInputTest (6), VisitPolicyTest (5), BrowserViewModelTest (8), ExampleUnitTest (1).

- [ ] **Step 7: Commit**

```powershell
git add -A
git commit -m "feat: record history with visit policy; bookmark toggle in ViewModel"
```

---

### Task 5: Navigation + History & Bookmarks Screens

**Files:**
- Create: `app/src/main/java/com/udaytank/browse/ui/HistoryScreen.kt`
- Create: `app/src/main/java/com/udaytank/browse/ui/BookmarksScreen.kt`
- Modify: `app/src/main/java/com/udaytank/browse/ui/BrowserScreen.kt`
- Modify: `app/src/main/java/com/udaytank/browse/MainActivity.kt`

**Interfaces:**
- Consumes: everything from Task 4
- Produces: NavHost with routes `"browser"`, `"history"`, `"bookmarks"`; `BrowserScreen(viewModel, onOpenHistory, onOpenBookmarks)`

- [ ] **Step 1: Create `HistoryScreen.kt`**

```kotlin
package com.udaytank.browse.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.udaytank.browse.BrowserViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: BrowserViewModel,
    onOpenUrl: (String) -> Unit,
    onBack: () -> Unit,
) {
    val entries by viewModel.historyEntries.collectAsStateWithLifecycle()

    val grouped = entries.groupBy { entry ->
        Instant.ofEpochMilli(entry.visitedAt).atZone(ZoneId.systemDefault()).toLocalDate()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("History") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::onClearHistory) {
                        Icon(Icons.Filled.DeleteSweep, contentDescription = "Clear all history")
                    }
                },
            )
        },
    ) { innerPadding ->
        if (entries.isEmpty()) {
            Column(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(24.dp)) {
                Text("No history yet", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                grouped.forEach { (date, dayEntries) ->
                    item(key = "header-$date") {
                        Text(
                            text = formatDay(date),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    items(dayEntries, key = { it.id }) { entry ->
                        ListItem(
                            headlineContent = { Text(entry.title, maxLines = 1) },
                            supportingContent = { Text(entry.url, maxLines = 1) },
                            trailingContent = {
                                IconButton(onClick = { viewModel.onDeleteHistoryEntry(entry.id) }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Delete entry")
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenUrl(entry.url) },
                        )
                    }
                }
            }
        }
    }
}

private fun formatDay(date: LocalDate): String = when (date) {
    LocalDate.now() -> "Today"
    LocalDate.now().minusDays(1) -> "Yesterday"
    else -> date.format(DateTimeFormatter.ofPattern("d MMMM yyyy"))
}
```

- [ ] **Step 2: Create `BookmarksScreen.kt`**

```kotlin
package com.udaytank.browse.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.udaytank.browse.BrowserViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarksScreen(
    viewModel: BrowserViewModel,
    onOpenUrl: (String) -> Unit,
    onBack: () -> Unit,
) {
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bookmarks") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        if (bookmarks.isEmpty()) {
            Column(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(24.dp)) {
                Text("No bookmarks yet — tap the star on any page", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                items(bookmarks, key = { it.id }) { bookmark ->
                    ListItem(
                        headlineContent = { Text(bookmark.title, maxLines = 1) },
                        supportingContent = { Text(bookmark.url, maxLines = 1) },
                        trailingContent = {
                            IconButton(onClick = { viewModel.onDeleteBookmark(bookmark.url) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete bookmark")
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenUrl(bookmark.url) },
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 3: Update `BrowserScreen.kt`**

Add two parameters and three toolbar buttons. New signature:

```kotlin
@Composable
fun BrowserScreen(
    viewModel: BrowserViewModel,
    onOpenHistory: () -> Unit,
    onOpenBookmarks: () -> Unit,
)
```

Add below the `state` declaration:

```kotlin
val isBookmarked by viewModel.isBookmarked.collectAsStateWithLifecycle()
```

Replace the `bottomBar` Row content with:

```kotlin
IconButton(onClick = viewModel::onBackPressed, enabled = state.canGoBack) {
    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
}
IconButton(onClick = viewModel::onForwardPressed, enabled = state.canGoForward) {
    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Forward")
}
IconButton(onClick = viewModel::onReloadPressed) {
    Icon(Icons.Filled.Refresh, contentDescription = "Reload")
}
IconButton(onClick = viewModel::onToggleBookmark, enabled = state.currentUrl != null) {
    Icon(
        if (isBookmarked) Icons.Filled.Star else Icons.Filled.StarBorder,
        contentDescription = if (isBookmarked) "Remove bookmark" else "Add bookmark",
    )
}
IconButton(onClick = onOpenBookmarks) {
    Icon(Icons.Filled.Bookmarks, contentDescription = "Bookmarks")
}
IconButton(onClick = onOpenHistory) {
    Icon(Icons.Filled.History, contentDescription = "History")
}
```

Add imports:

```kotlin
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
```

Update the `WebViewContainer` call — add the new parameter:

```kotlin
WebViewContainer(
    pendingCommand = state.pendingCommand,
    currentUrl = state.currentUrl,
    onCommandConsumed = viewModel::onCommandConsumed,
    onPageStarted = viewModel::onPageStarted,
    onProgressChanged = viewModel::onProgressChanged,
    onPageFinished = viewModel::onPageFinished,
    onHistoryChanged = viewModel::onHistoryChanged,
    modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding),
)
```

- [ ] **Step 4: Add NavHost in `MainActivity.kt`**

Replace the `setContent` block:

```kotlin
setContent {
    BrowseTheme {
        val navController = rememberNavController()
        NavHost(navController = navController, startDestination = "browser") {
            composable("browser") {
                BrowserScreen(
                    viewModel = viewModel,
                    onOpenHistory = { navController.navigate("history") },
                    onOpenBookmarks = { navController.navigate("bookmarks") },
                )
            }
            composable("history") {
                HistoryScreen(
                    viewModel = viewModel,
                    onOpenUrl = { url ->
                        viewModel.onOpenUrl(url)
                        navController.popBackStack()
                    },
                    onBack = { navController.popBackStack() },
                )
            }
            composable("bookmarks") {
                BookmarksScreen(
                    viewModel = viewModel,
                    onOpenUrl = { url ->
                        viewModel.onOpenUrl(url)
                        navController.popBackStack()
                    },
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}
```

Add imports:

```kotlin
import androidx.compose.runtime.remember
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.udaytank.browse.ui.BookmarksScreen
import com.udaytank.browse.ui.HistoryScreen
```

- [ ] **Step 5: Build and run all tests**

Run: `.\gradlew.bat assembleDebug testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`, all unit tests pass.

- [ ] **Step 6: Commit**

```powershell
git add -A
git commit -m "feat: history and bookmarks screens with navigation"
```

---

### Task 6: Acceptance, Merge, Push

**Files:** none — quality gate.

- [ ] **Step 1: Manual acceptance checklist (emulator or phone)**

1. Visit 3 different sites → History shows all 3, newest first, under "Today".
2. Reload a page twice quickly → only one history entry for it.
3. Tap a history entry → returns to browser and loads that page.
4. Delete one entry (bin icon) → it disappears immediately.
5. Clear-all (sweep icon) → history empties, "No history yet" appears.
6. On any page, tap the star → fills in; open Bookmarks → page is listed.
7. Tap the star again → empties; bookmark gone from the list.
8. Bookmark a page, close the app fully (swipe away), reopen → bookmark still there (data survives restarts).
9. Rotate on the browser screen → current page comes back (fresh load is acceptable).

- [ ] **Step 2: Merge to main and push**

```powershell
git checkout main
git merge --no-ff feature/history-and-bookmarks -m "Merge feature/history-and-bookmarks: Phase 2a"
git push
git branch -d feature/history-and-bookmarks
```
