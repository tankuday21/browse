# Phase 2b: Tabs Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Multiple tabs with instant switching and preserved page state; a tab switcher screen; tabs survive process death; rotation and screen navigation no longer reset the page.

**Architecture:** Three layers. (1) Room `tabs` table — persistent truth, restored at startup (DB migration v1→v2). (2) `TabManager` (browser core) — in-memory authority over the tab list and active tab, write-through to the DAO. (3) `WebViewHolder` (UI layer) — one live `WebView` per tab, created outside the compose tree so instances survive navigation; `android:configChanges` keeps the Activity alive through rotation. `BrowserViewModel` callbacks become tab-aware (`tabId` first parameter); UI state only reflects events from the active tab.

**Tech Stack:** existing stack + Room `Migration`, `LazyVerticalGrid`.

**Learning mode:** Task 2 is 👤 OWNER-written (tab-close activation policy). Reference implementation included for verification only.

## Global Constraints

- Branch `feature/tabs`, merged `--no-ff` to main at the end; push after merge
- Package `com.udaytank.browse`; MVVM rules unchanged (no browser logic in composables)
- DB schema change MUST ship a manual `Migration(1, 2)` — no destructive fallback (the installed app has v1 data)
- PowerShell from `F:\Dev\Browse`; once per terminal: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"`
- Instrumented tests need the emulator running
- Incognito tabs are Phase 3; `TabEntity` deliberately has no incognito flag yet (YAGNI)

---

### Task 1: Tab Entity, DAO, and Database Migration v1→v2

**Files:**
- Create: `app/src/main/java/com/udaytank/browse/data/TabEntity.kt`
- Create: `app/src/main/java/com/udaytank/browse/data/TabDao.kt`
- Modify: `app/src/main/java/com/udaytank/browse/data/BrowseDatabase.kt`
- Modify: `app/src/main/java/com/udaytank/browse/BrowseApplication.kt`
- Test: extend `app/src/androidTest/java/com/udaytank/browse/data/BrowseDatabaseTest.kt`

**Interfaces:**
- Produces (used by Tasks 2–5):
  - `TabEntity(id: Long = 0, url: String, title: String, position: Int, isActive: Boolean)`
  - `TabDao`: `suspend getAll(): List<TabEntity>` (ordered by position), `suspend insert(TabEntity): Long`, `suspend deleteById(Long)`, `suspend setActive(Long)`, `suspend updateContent(id: Long, url: String, title: String)`
  - `BrowseDatabase.tabDao()`; DB version 2 with `MIGRATION_1_2`

- [ ] **Step 1: Create branch**

```powershell
git checkout -b feature/tabs
```

- [ ] **Step 2: Create `TabEntity.kt`**

```kotlin
package com.udaytank.browse.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tabs")
data class TabEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String,
    val position: Int,
    val isActive: Boolean,
)
```

- [ ] **Step 3: Create `TabDao.kt`**

```kotlin
package com.udaytank.browse.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface TabDao {
    @Query("SELECT * FROM tabs ORDER BY position")
    suspend fun getAll(): List<TabEntity>

    @Insert
    suspend fun insert(tab: TabEntity): Long

    @Query("DELETE FROM tabs WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** Marks exactly one tab active: SQLite evaluates (id = :id) to 1 or 0. */
    @Query("UPDATE tabs SET isActive = (id = :id)")
    suspend fun setActive(id: Long)

    @Query("UPDATE tabs SET url = :url, title = :title WHERE id = :id")
    suspend fun updateContent(id: Long, url: String, title: String)
}
```

- [ ] **Step 4: Bump database version and add the migration**

Replace `BrowseDatabase.kt`:

```kotlin
package com.udaytank.browse.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [HistoryEntry::class, Bookmark::class, TabEntity::class], version = 2)
abstract class BrowseDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun tabDao(): TabDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `tabs` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`url` TEXT NOT NULL, " +
                        "`title` TEXT NOT NULL, " +
                        "`position` INTEGER NOT NULL, " +
                        "`isActive` INTEGER NOT NULL)"
                )
            }
        }
    }
}
```

In `BrowseApplication.kt`, register the migration:

```kotlin
val database: BrowseDatabase by lazy {
    Room.databaseBuilder(this, BrowseDatabase::class.java, "browse.db")
        .addMigrations(BrowseDatabase.MIGRATION_1_2)
        .build()
}
```

- [ ] **Step 5: Add DAO tests to `BrowseDatabaseTest.kt`**

```kotlin
@Test
fun tabInsertReturnsIdAndKeepsOrder() = runBlocking {
    val id1 = db.tabDao().insert(TabEntity(url = "https://a.com", title = "A", position = 0, isActive = true))
    val id2 = db.tabDao().insert(TabEntity(url = "https://b.com", title = "B", position = 1, isActive = false))
    val all = db.tabDao().getAll()
    assertEquals(listOf(id1, id2), all.map { it.id })
}

@Test
fun tabSetActiveMarksExactlyOne() = runBlocking {
    val id1 = db.tabDao().insert(TabEntity(url = "https://a.com", title = "A", position = 0, isActive = true))
    val id2 = db.tabDao().insert(TabEntity(url = "https://b.com", title = "B", position = 1, isActive = false))
    db.tabDao().setActive(id2)
    val all = db.tabDao().getAll()
    assertEquals(listOf(false, true), all.map { it.isActive })
    assertEquals(id1, all.first().id)
}

@Test
fun tabUpdateContent() = runBlocking {
    val id = db.tabDao().insert(TabEntity(url = "https://a.com", title = "A", position = 0, isActive = true))
    db.tabDao().updateContent(id, "https://a.com/page", "A page")
    assertEquals("A page", db.tabDao().getAll().first().title)
}
```

- [ ] **Step 6: Run instrumented tests, commit**

Run: `.\gradlew.bat connectedDebugAndroidTest` — Expected: PASS (9 tests + example).
Note: the real migration is exercised live when the already-installed app (DB v1) next opens — acceptance Step 1 in Task 6 covers it.

```powershell
git add -A
git commit -m "feat: tabs table with DAO and v1-to-v2 migration"
```

---

### Task 2: TabClosePolicy — 👤 OWNER WRITES

Which tab becomes active after one is closed? This defines how closing *feels*. Chrome activates the right neighbor; Safari the left. Owner decides and encodes it.

**Files:**
- Create: `app/src/main/java/com/udaytank/browse/browser/TabClosePolicy.kt`
- Test: `app/src/test/java/com/udaytank/browse/browser/TabClosePolicyTest.kt`

**Interfaces:**
- Consumes: `TabEntity` (Task 1)
- Produces: `object TabClosePolicy { fun nextActiveId(tabs: List<TabEntity>, closingId: Long, activeId: Long?): Long? }` — returns the id that should be active AFTER the close; null when no tabs remain. Task 3's TabManager calls exactly this.

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.udaytank.browse.browser

import com.udaytank.browse.data.TabEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TabClosePolicyTest {

    private fun tab(id: Long, position: Int) =
        TabEntity(id = id, url = "https://a.com", title = "t", position = position, isActive = false)

    private val three = listOf(tab(1, 0), tab(2, 1), tab(3, 2))

    @Test
    fun `closing a background tab keeps the current active tab`() {
        assertEquals(2L, TabClosePolicy.nextActiveId(three, closingId = 3, activeId = 2))
    }

    @Test
    fun `closing the active tab activates its right neighbor`() {
        assertEquals(3L, TabClosePolicy.nextActiveId(three, closingId = 2, activeId = 2))
    }

    @Test
    fun `closing the active last tab falls back to the left neighbor`() {
        assertEquals(2L, TabClosePolicy.nextActiveId(three, closingId = 3, activeId = 3))
    }

    @Test
    fun `closing the only tab leaves nothing to activate`() {
        assertNull(TabClosePolicy.nextActiveId(listOf(tab(1, 0)), closingId = 1, activeId = 1))
    }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.udaytank.browse.browser.TabClosePolicyTest"`
Expected: FAIL — `Unresolved reference: TabClosePolicy`.

- [ ] **Step 3: 👤 OWNER WRITES — skeleton:**

```kotlin
package com.udaytank.browse.browser

import com.udaytank.browse.data.TabEntity

object TabClosePolicy {

    /**
     * Which tab id should be active after [closingId] closes?
     * Null when no tabs will remain. Chrome rule: closing a background
     * tab changes nothing; closing the active tab prefers the right
     * neighbor, then the left.
     */
    fun nextActiveId(tabs: List<TabEntity>, closingId: Long, activeId: Long?): Long? {
        // 👤 OWNER WRITES (~6 lines). Hints:
        //  - closing a non-active tab → activeId unchanged
        //  - indexOfFirst { it.id == closingId } finds the closing position
        //  - after removal, the "right neighbor" sits at that same index
        //  - List.getOrNull(index) returns null instead of crashing
        TODO()
    }
}
```

Reference implementation (executor: reveal only after the owner's attempt):

```kotlin
fun nextActiveId(tabs: List<TabEntity>, closingId: Long, activeId: Long?): Long? {
    if (closingId != activeId) return activeId
    val remaining = tabs.filterNot { it.id == closingId }
    if (remaining.isEmpty()) return null
    val closedIndex = tabs.indexOfFirst { it.id == closingId }
    return (remaining.getOrNull(closedIndex) ?: remaining.last()).id
}
```

- [ ] **Step 4: Run tests → PASS, then commit**

```powershell
git add app/src/main/java/com/udaytank/browse/browser/TabClosePolicy.kt app/src/test/java/com/udaytank/browse/browser/TabClosePolicyTest.kt
git commit -m "feat: tab close activation policy"
```

---

### Task 3: TabManager

**Files:**
- Create: `app/src/main/java/com/udaytank/browse/browser/TabManager.kt`
- Create: `app/src/test/java/com/udaytank/browse/FakeTabDao.kt`
- Test: `app/src/test/java/com/udaytank/browse/browser/TabManagerTest.kt`

**Interfaces:**
- Consumes: `TabDao`, `TabEntity` (Task 1), `TabClosePolicy` (Task 2)
- Produces (used by Task 4):
  - `class TabManager(tabDao: TabDao)` with `tabs: StateFlow<List<TabEntity>>`, `activeTabId: StateFlow<Long?>`, `suspend initialize(homeUrl: String)`, `suspend newTab(url: String): Long`, `suspend switchTo(id: Long)`, `suspend closeTab(id: Long, homeUrl: String)`, `suspend onContentChanged(id: Long, url: String, title: String)`

- [ ] **Step 1: Create `FakeTabDao.kt`**

```kotlin
package com.udaytank.browse

import com.udaytank.browse.data.TabDao
import com.udaytank.browse.data.TabEntity

class FakeTabDao : TabDao {
    val stored = mutableListOf<TabEntity>()
    private var nextId = 1L

    override suspend fun getAll(): List<TabEntity> = stored.sortedBy { it.position }

    override suspend fun insert(tab: TabEntity): Long {
        val id = nextId++
        stored += tab.copy(id = id)
        return id
    }

    override suspend fun deleteById(id: Long) {
        stored.removeAll { it.id == id }
    }

    override suspend fun setActive(id: Long) {
        stored.replaceAll { it.copy(isActive = it.id == id) }
    }

    override suspend fun updateContent(id: Long, url: String, title: String) {
        stored.replaceAll { if (it.id == id) it.copy(url = url, title = title) else it }
    }
}
```

- [ ] **Step 2: Write the failing tests (`TabManagerTest.kt`)**

```kotlin
package com.udaytank.browse.browser

import com.udaytank.browse.FakeTabDao
import com.udaytank.browse.data.TabEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class TabManagerTest {

    @Test
    fun `initialize with empty storage creates one home tab`() = runTest {
        val manager = TabManager(FakeTabDao())
        manager.initialize("https://home")
        assertEquals(1, manager.tabs.value.size)
        assertEquals("https://home", manager.tabs.value.first().url)
        assertEquals(manager.tabs.value.first().id, manager.activeTabId.value)
    }

    @Test
    fun `initialize restores stored tabs and active choice`() = runTest {
        val dao = FakeTabDao()
        dao.insert(TabEntity(url = "https://a.com", title = "A", position = 0, isActive = false))
        val activeId = dao.insert(TabEntity(url = "https://b.com", title = "B", position = 1, isActive = true))
        val manager = TabManager(dao)
        manager.initialize("https://home")
        assertEquals(2, manager.tabs.value.size)
        assertEquals(activeId, manager.activeTabId.value)
    }

    @Test
    fun `newTab appends and becomes active`() = runTest {
        val manager = TabManager(FakeTabDao())
        manager.initialize("https://home")
        val newId = manager.newTab("https://b.com")
        assertEquals(2, manager.tabs.value.size)
        assertEquals(newId, manager.activeTabId.value)
    }

    @Test
    fun `switchTo changes the active tab`() = runTest {
        val manager = TabManager(FakeTabDao())
        manager.initialize("https://home")
        val first = manager.activeTabId.value!!
        manager.newTab("https://b.com")
        manager.switchTo(first)
        assertEquals(first, manager.activeTabId.value)
    }

    @Test
    fun `closing the active tab activates per policy`() = runTest {
        val manager = TabManager(FakeTabDao())
        manager.initialize("https://home")
        val first = manager.activeTabId.value!!
        val second = manager.newTab("https://b.com")
        manager.switchTo(first)
        manager.closeTab(first, "https://home")
        assertEquals(second, manager.activeTabId.value)
        assertEquals(1, manager.tabs.value.size)
    }

    @Test
    fun `closing the last tab opens a fresh home tab`() = runTest {
        val manager = TabManager(FakeTabDao())
        manager.initialize("https://home")
        manager.closeTab(manager.activeTabId.value!!, "https://home")
        assertEquals(1, manager.tabs.value.size)
        assertEquals("https://home", manager.tabs.value.first().url)
    }

    @Test
    fun `content change updates the tab entry`() = runTest {
        val manager = TabManager(FakeTabDao())
        manager.initialize("https://home")
        val id = manager.activeTabId.value!!
        manager.onContentChanged(id, "https://a.com/x", "Page X")
        assertEquals("Page X", manager.tabs.value.first().title)
    }
}
```

- [ ] **Step 3: Run → FAIL (`Unresolved reference: TabManager`)**

- [ ] **Step 4: Implement `TabManager.kt`**

```kotlin
package com.udaytank.browse.browser

import com.udaytank.browse.data.TabDao
import com.udaytank.browse.data.TabEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** In-memory authority over tabs; every mutation is written through to the DAO. */
class TabManager(private val tabDao: TabDao) {

    private val _tabs = MutableStateFlow<List<TabEntity>>(emptyList())
    val tabs: StateFlow<List<TabEntity>> = _tabs.asStateFlow()

    private val _activeTabId = MutableStateFlow<Long?>(null)
    val activeTabId: StateFlow<Long?> = _activeTabId.asStateFlow()

    suspend fun initialize(homeUrl: String) {
        val stored = tabDao.getAll()
        if (stored.isEmpty()) {
            newTab(homeUrl)
        } else {
            _tabs.value = stored
            _activeTabId.value = (stored.find { it.isActive } ?: stored.first()).id
        }
    }

    suspend fun newTab(url: String): Long {
        val position = (_tabs.value.maxOfOrNull { it.position } ?: -1) + 1
        val id = tabDao.insert(TabEntity(url = url, title = url, position = position, isActive = true))
        _tabs.value = _tabs.value.map { it.copy(isActive = false) } +
            TabEntity(id = id, url = url, title = url, position = position, isActive = true)
        _activeTabId.value = id
        tabDao.setActive(id)
        return id
    }

    suspend fun switchTo(id: Long) {
        if (_tabs.value.none { it.id == id }) return
        _tabs.value = _tabs.value.map { it.copy(isActive = it.id == id) }
        _activeTabId.value = id
        tabDao.setActive(id)
    }

    suspend fun closeTab(id: Long, homeUrl: String) {
        val next = TabClosePolicy.nextActiveId(_tabs.value, closingId = id, activeId = _activeTabId.value)
        _tabs.value = _tabs.value.filterNot { it.id == id }
        tabDao.deleteById(id)
        when {
            _tabs.value.isEmpty() -> newTab(homeUrl)
            next != null -> switchTo(next)
        }
    }

    suspend fun onContentChanged(id: Long, url: String, title: String) {
        _tabs.value = _tabs.value.map { if (it.id == id) it.copy(url = url, title = title) else it }
        tabDao.updateContent(id, url, title)
    }
}
```

- [ ] **Step 5: Run all unit tests → PASS, commit**

```powershell
git add -A
git commit -m "feat: TabManager with persistence write-through"
```

---

### Task 4: Tab-Aware ViewModel

**Files:**
- Modify: `app/src/main/java/com/udaytank/browse/BrowserViewModel.kt`
- Modify: `app/src/test/java/com/udaytank/browse/BrowserViewModelTest.kt`

**Interfaces:**
- Consumes: `TabManager` (Task 3), `TabDao` (Task 1)
- Produces (used by Task 5):
  - Constructor: `BrowserViewModel(historyDao, bookmarkDao, tabDao)`; Factory updated
  - New members: `tabs: StateFlow<List<TabEntity>>`, `activeTabId: StateFlow<Long?>`, `onNewTab()`, `onCloseTab(id: Long)`, `onSwitchTab(id: Long)`
  - Changed callbacks (tabId first): `onPageStarted(tabId, url)`, `onProgressChanged(tabId, percent)`, `onPageFinished(tabId, url, title)`, `onHistoryChanged(tabId, canGoBack, canGoForward)` — UI state updates apply only when `tabId == activeTabId.value`; history recording applies to every tab
  - Removed: initial `LoadUrl(HOME_URL)` pending command (the restored/created tab's URL loads on WebView attach instead)

- [ ] **Step 1: Rework `BrowserViewModel.kt`**

Constructor, tab plumbing, and init:

```kotlin
class BrowserViewModel(
    private val historyDao: HistoryDao,
    private val bookmarkDao: BookmarkDao,
    tabDao: TabDao,
) : ViewModel() {

    private val tabManager = TabManager(tabDao)
    val tabs: StateFlow<List<TabEntity>> = tabManager.tabs
    val activeTabId: StateFlow<Long?> = tabManager.activeTabId

    private val _uiState = MutableStateFlow(BrowserUiState())
    val uiState: StateFlow<BrowserUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            tabManager.initialize(HOME_URL)
        }
        // Keep the address bar in sync with whichever tab is active.
        viewModelScope.launch {
            combine(tabManager.tabs, tabManager.activeTabId) { tabs, active ->
                tabs.find { it.id == active }
            }.distinctUntilChanged().collect { tab ->
                if (tab != null) {
                    _uiState.update { it.copy(currentUrl = tab.url, addressBarText = tab.url) }
                }
            }
        }
    }
```

Tab events:

```kotlin
    fun onNewTab() {
        viewModelScope.launch { tabManager.newTab(HOME_URL) }
    }

    fun onCloseTab(id: Long) {
        viewModelScope.launch { tabManager.closeTab(id, HOME_URL) }
    }

    fun onSwitchTab(id: Long) {
        viewModelScope.launch { tabManager.switchTo(id) }
    }
```

Tab-aware WebView callbacks (replace the old ones):

```kotlin
    fun onPageStarted(tabId: Long, url: String) {
        viewModelScope.launch { tabManager.onContentChanged(tabId, url, url) }
        if (tabId == activeTabId.value) {
            _uiState.update { it.copy(currentUrl = url, addressBarText = url, isLoading = true, progress = 0) }
        }
    }

    fun onProgressChanged(tabId: Long, percent: Int) {
        if (tabId == activeTabId.value) _uiState.update { it.copy(progress = percent) }
    }

    fun onPageFinished(tabId: Long, url: String, title: String?) {
        viewModelScope.launch { tabManager.onContentChanged(tabId, url, title ?: url) }
        if (tabId == activeTabId.value) _uiState.update { it.copy(isLoading = false) }
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            when (val decision = VisitPolicy.decide(historyDao.mostRecent(), url)) {
                VisitDecision.Skip -> Unit
                VisitDecision.RecordNew ->
                    historyDao.insert(HistoryEntry(url = url, title = title ?: url, visitedAt = now))
                is VisitDecision.RefreshExisting ->
                    historyDao.updateVisitedAt(decision.existingId, now)
            }
        }
    }

    fun onHistoryChanged(tabId: Long, canGoBack: Boolean, canGoForward: Boolean) {
        if (tabId == activeTabId.value) {
            _uiState.update { it.copy(canGoBack = canGoBack, canGoForward = canGoForward) }
        }
    }
```

`_uiState` no longer starts with a pending command — remove `BrowserUiState(pendingCommand = BrowserCommand.LoadUrl(HOME_URL))`. Factory gains the third DAO:

```kotlin
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as BrowseApplication
                BrowserViewModel(
                    app.database.historyDao(),
                    app.database.bookmarkDao(),
                    app.database.tabDao(),
                )
            }
        }
```

Add imports: `com.udaytank.browse.browser.TabManager`, `com.udaytank.browse.data.TabDao`, `com.udaytank.browse.data.TabEntity`, `kotlinx.coroutines.flow.combine`.

- [ ] **Step 2: Update `BrowserViewModelTest.kt`**

The `vm(...)` helper gains a `FakeTabDao`; callbacks pass the active tab's id:

```kotlin
    private fun vm(
        historyDao: FakeHistoryDao = FakeHistoryDao(),
        bookmarkDao: FakeBookmarkDao = FakeBookmarkDao(),
        tabDao: FakeTabDao = FakeTabDao(),
    ) = BrowserViewModel(historyDao, bookmarkDao, tabDao)
```

Update every test that calls a WebView callback to fetch `val tabId = vm.activeTabId.value!!` and pass it, e.g.:

```kotlin
    @Test
    fun `page start sets loading and syncs address bar`() {
        val vm = vm()
        val tabId = vm.activeTabId.value!!
        vm.onPageStarted(tabId, "https://bbc.com/news")
        assertTrue(vm.uiState.value.isLoading)
        assertEquals("https://bbc.com/news", vm.uiState.value.addressBarText)
        assertEquals("https://bbc.com/news", vm.uiState.value.currentUrl)
    }
```

Add two new tests:

```kotlin
    @Test
    fun `events from a background tab do not disturb the active ui`() {
        val vm = vm()
        val first = vm.activeTabId.value!!
        vm.onNewTab()
        vm.onPageStarted(first, "https://background.com")
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test
    fun `switching tabs syncs the address bar to that tab`() {
        val vm = vm()
        val first = vm.activeTabId.value!!
        vm.onPageFinished(first, "https://a.com", "A")
        vm.onNewTab()
        vm.onSwitchTab(first)
        assertEquals("https://a.com", vm.uiState.value.addressBarText)
    }
```

(`MainDispatcherRule`'s unconfined dispatcher runs the `init` coroutines eagerly, so `activeTabId.value` is ready in tests.)

- [ ] **Step 3: Run unit tests → PASS, commit**

Run: `.\gradlew.bat testDebugUnitTest` — the compile will fail for `BrowserScreen`/`WebViewContainer` until Task 5's UI rework; do Task 5 Steps 1–3 first if so, then run. Otherwise:

```powershell
git add -A
git commit -m "feat: tab-aware ViewModel over TabManager"
```

---

### Task 5: WebViewHolder, Tab Switcher UI, configChanges

**Files:**
- Create: `app/src/main/java/com/udaytank/browse/ui/WebViewHolder.kt`
- Create: `app/src/main/java/com/udaytank/browse/ui/TabWebView.kt`
- Create: `app/src/main/java/com/udaytank/browse/ui/TabSwitcherScreen.kt`
- Delete: `app/src/main/java/com/udaytank/browse/ui/WebViewContainer.kt`
- Modify: `app/src/main/java/com/udaytank/browse/ui/BrowserScreen.kt`
- Modify: `app/src/main/java/com/udaytank/browse/MainActivity.kt`
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: everything from Task 4
- Produces:
  - `class WebViewHolder(context, listener)` — `obtain(tabId): WebView` (lazy, one per tab), `close(tabId)`, `destroyAll()`; `WebViewHolder.Listener` mirrors the four VM callbacks
  - `@Composable TabWebView(holder, tabId, tabUrl, pendingCommand, onCommandConsumed, modifier)`
  - `@Composable TabSwitcherScreen(viewModel, onTabChosen: () -> Unit, onCloseTabView: (Long) -> Unit, onBack: () -> Unit)`
  - Route `"tabs"`; `BrowserScreen(viewModel, holder, onOpenHistory, onOpenBookmarks, onOpenTabs)`

- [ ] **Step 1: Create `WebViewHolder.kt`**

```kotlin
package com.udaytank.browse.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * Owns one live WebView per tab, outside the compose tree, so instances
 * (and their page state) survive navigation and recomposition.
 */
class WebViewHolder(
    private val context: Context,
    private val listener: Listener,
) {
    interface Listener {
        fun onPageStarted(tabId: Long, url: String)
        fun onProgressChanged(tabId: Long, percent: Int)
        fun onPageFinished(tabId: Long, url: String, title: String?)
        fun onHistoryChanged(tabId: Long, canGoBack: Boolean, canGoForward: Boolean)
    }

    private val webViews = mutableMapOf<Long, WebView>()

    @SuppressLint("SetJavaScriptEnabled")
    fun obtain(tabId: Long): WebView = webViews.getOrPut(tabId) {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true

            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                    listener.onPageStarted(tabId, url)
                }

                override fun onPageFinished(view: WebView, url: String) {
                    listener.onPageFinished(tabId, url, view.title)
                }

                override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
                    listener.onHistoryChanged(tabId, view.canGoBack(), view.canGoForward())
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView, newProgress: Int) {
                    listener.onProgressChanged(tabId, newProgress)
                }
            }
        }
    }

    fun close(tabId: Long) {
        webViews.remove(tabId)?.destroy()
    }

    fun destroyAll() {
        webViews.values.forEach { it.destroy() }
        webViews.clear()
    }
}
```

- [ ] **Step 2: Create `TabWebView.kt`**

```kotlin
package com.udaytank.browse.ui

import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.udaytank.browse.browser.BrowserCommand

@Composable
fun TabWebView(
    holder: WebViewHolder,
    tabId: Long,
    tabUrl: String,
    pendingCommand: BrowserCommand?,
    onCommandConsumed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    key(tabId) {
        AndroidView(
            modifier = modifier,
            factory = {
                holder.obtain(tabId).also { webView ->
                    (webView.parent as? ViewGroup)?.removeView(webView)
                    if (webView.url == null) webView.loadUrl(tabUrl)
                }
            },
            update = { webView ->
                when (pendingCommand) {
                    is BrowserCommand.LoadUrl -> webView.loadUrl(pendingCommand.url)
                    BrowserCommand.GoBack -> if (webView.canGoBack()) webView.goBack()
                    BrowserCommand.GoForward -> if (webView.canGoForward()) webView.goForward()
                    BrowserCommand.Reload -> webView.reload()
                    null -> Unit
                }
                if (pendingCommand != null) onCommandConsumed()
            },
        )
    }
}
```

Delete `WebViewContainer.kt`.

- [ ] **Step 3: Create `TabSwitcherScreen.kt`**

```kotlin
package com.udaytank.browse.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabSwitcherScreen(
    viewModel: BrowserViewModel,
    onTabChosen: () -> Unit,
    onCloseTabView: (Long) -> Unit,
    onBack: () -> Unit,
) {
    val tabs by viewModel.tabs.collectAsStateWithLifecycle()
    val activeTabId by viewModel.activeTabId.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tabs (${tabs.size})") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                viewModel.onNewTab()
                onTabChosen()
            }) {
                Icon(Icons.Filled.Add, contentDescription = "New tab")
            }
        },
    ) { innerPadding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(tabs, key = { it.id }) { tab ->
                ElevatedCard(
                    modifier = Modifier.clickable {
                        viewModel.onSwitchTab(tab.id)
                        onTabChosen()
                    },
                    colors = if (tab.id == activeTabId) {
                        CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    } else {
                        CardDefaults.elevatedCardColors()
                    },
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(vertical = 8.dp)) {
                            Text(tab.title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                            Text(tab.url, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                        }
                        IconButton(onClick = {
                            onCloseTabView(tab.id)
                            viewModel.onCloseTab(tab.id)
                        }) {
                            Icon(Icons.Filled.Close, contentDescription = "Close tab")
                        }
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 4: Rework `BrowserScreen.kt`**

New signature and bottom bar. Replace the whole bottom bar and WebView call; move Reload/Bookmarks/History into an overflow `DropdownMenu`; add the tab-count button:

```kotlin
@Composable
fun BrowserScreen(
    viewModel: BrowserViewModel,
    holder: WebViewHolder,
    onOpenHistory: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenTabs: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isBookmarked by viewModel.isBookmarked.collectAsStateWithLifecycle()
    val tabs by viewModel.tabs.collectAsStateWithLifecycle()
    val activeTabId by viewModel.activeTabId.collectAsStateWithLifecycle()
    val keyboard = LocalSoftwareKeyboardController.current
    var menuOpen by remember { mutableStateOf(false) }

    BackHandler(enabled = state.canGoBack) { viewModel.onBackPressed() }

    Scaffold(
        topBar = { /* unchanged address bar + progress Column */ },
        bottomBar = {
            Row(
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().imePadding(),
            ) {
                IconButton(onClick = viewModel::onBackPressed, enabled = state.canGoBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                IconButton(onClick = viewModel::onForwardPressed, enabled = state.canGoForward) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Forward")
                }
                IconButton(onClick = onOpenTabs) {
                    Text(
                        text = "${tabs.size}",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier
                            .border(1.5.dp, LocalContentColor.current, RoundedCornerShape(4.dp))
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                    )
                }
                IconButton(onClick = viewModel::onToggleBookmark, enabled = state.currentUrl != null) {
                    Icon(
                        if (isBookmarked) Icons.Filled.Star else Icons.Filled.StarBorder,
                        contentDescription = if (isBookmarked) "Remove bookmark" else "Add bookmark",
                    )
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Menu")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Reload") },
                            onClick = { viewModel.onReloadPressed(); menuOpen = false },
                        )
                        DropdownMenuItem(
                            text = { Text("Bookmarks") },
                            onClick = { onOpenBookmarks(); menuOpen = false },
                        )
                        DropdownMenuItem(
                            text = { Text("History") },
                            onClick = { onOpenHistory(); menuOpen = false },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        val currentTabId = activeTabId
        if (currentTabId != null) {
            val tabUrl = tabs.find { it.id == currentTabId }?.url ?: BrowserViewModel.HOME_URL
            TabWebView(
                holder = holder,
                tabId = currentTabId,
                tabUrl = tabUrl,
                pendingCommand = state.pendingCommand,
                onCommandConsumed = viewModel::onCommandConsumed,
                modifier = Modifier.fillMaxSize().padding(innerPadding),
            )
        }
    }
}
```

New imports: `androidx.compose.foundation.border`, `androidx.compose.foundation.layout.Box`, `androidx.compose.foundation.shape.RoundedCornerShape`, `androidx.compose.material.icons.filled.MoreVert`, `androidx.compose.material3.DropdownMenu`, `androidx.compose.material3.DropdownMenuItem`, `androidx.compose.material3.LocalContentColor`, `androidx.compose.material3.MaterialTheme`, `androidx.compose.runtime.mutableStateOf`, `androidx.compose.runtime.remember`, `androidx.compose.runtime.setValue`, `com.udaytank.browse.BrowserViewModel`. Removed: History/Bookmarks/Refresh icon imports if unused.

- [ ] **Step 5: Wire `MainActivity.kt`**

Create the holder above the NavHost and add the `"tabs"` route:

```kotlin
setContent {
    BrowseTheme {
        val navController = rememberNavController()
        val holder = remember {
            WebViewHolder(this, object : WebViewHolder.Listener {
                override fun onPageStarted(tabId: Long, url: String) = viewModel.onPageStarted(tabId, url)
                override fun onProgressChanged(tabId: Long, percent: Int) = viewModel.onProgressChanged(tabId, percent)
                override fun onPageFinished(tabId: Long, url: String, title: String?) = viewModel.onPageFinished(tabId, url, title)
                override fun onHistoryChanged(tabId: Long, canGoBack: Boolean, canGoForward: Boolean) = viewModel.onHistoryChanged(tabId, canGoBack, canGoForward)
            })
        }
        DisposableEffect(Unit) { onDispose { holder.destroyAll() } }

        NavHost(navController = navController, startDestination = "browser") {
            composable("browser") {
                BrowserScreen(
                    viewModel = viewModel,
                    holder = holder,
                    onOpenHistory = { navController.navigate("history") },
                    onOpenBookmarks = { navController.navigate("bookmarks") },
                    onOpenTabs = { navController.navigate("tabs") },
                )
            }
            composable("tabs") {
                TabSwitcherScreen(
                    viewModel = viewModel,
                    onTabChosen = { navController.popBackStack() },
                    onCloseTabView = { tabId -> holder.close(tabId) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable("history") { /* unchanged */ }
            composable("bookmarks") { /* unchanged */ }
        }
    }
}
```

New imports: `androidx.compose.runtime.DisposableEffect`, `androidx.compose.runtime.remember`, `com.udaytank.browse.ui.TabSwitcherScreen`, `com.udaytank.browse.ui.WebViewHolder`.

- [ ] **Step 6: Manifest — survive rotation like a real browser**

In `AndroidManifest.xml`, add to the `<activity>` tag:

```xml
android:configChanges="orientation|screenSize|smallestScreenSize|screenLayout|keyboardHidden"
```

- [ ] **Step 7: Build + all unit tests → PASS, commit**

```powershell
git add -A
git commit -m "feat: tab switcher, per-tab WebViews, rotation survival"
```

---

### Task 6: Acceptance, Merge, Push

- [ ] **Step 1: Manual acceptance (install over the existing app — this exercises the live DB migration)**

1. App opens without crashing (migration v1→v2 succeeded) and shows your previous session's... nothing yet — one home tab (first run creates it). History and bookmarks from Phase 2a still present.
2. Tab button shows `1`. Open switcher → `+` → new tab appears and activates (count `2`).
3. Load different pages in each tab; switch back and forth → **instant**, scroll position preserved.
4. Scroll down a page → rotate the device → **page does not reload**, scroll preserved.
5. Visit History and return → page state preserved (no reload).
6. Close the active tab in the switcher → the right neighbor activates (owner's policy).
7. Close ALL tabs → a fresh home tab appears automatically.
8. Load pages in 2 tabs → swipe the app away (kill it) → reopen → both tabs restored (they reload their URLs), correct one active.
9. History recording and bookmark star still work per active tab.

- [ ] **Step 2: Merge and push**

```powershell
git checkout main
git merge --no-ff feature/tabs -m "Merge feature/tabs: Phase 2b"
git push
git branch -d feature/tabs
```
