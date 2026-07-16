# Orbits (Profiles / Containers) — Phase 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add user-created, named, colored **Orbits** — browsing identities each with isolated logins/cookies/storage (via `ProfileStore`) and their own set of tabs — plus the switcher UI and a live indicator.

**Architecture:** Generalize the existing single `"incognito"` ProfileStore profile to one profile per Orbit. A new `orbits` Room table + `TabEntity.orbitId` + a persisted `activeOrbitId`. The tab switcher filters to the active Orbit's tabs (the pattern Incognito already uses). MVVM single-`StateFlow` UI state; Room migration chain; DataStore; Compose.

**Tech Stack:** Kotlin · Jetpack Compose Material3 · Room (v14) · DataStore · androidx.webkit `ProfileStore`/`WebViewCompat`.

## Global Constraints

- **Incognito is never an Orbit:** incognito tabs keep `orbitId = null`, stay in-memory/ephemeral, never persisted, never written to any DAO. Unchanged behavior.
- **Deleting an Orbit wipes its data:** delete closes its tabs, deletes its `ProfileStore` profile (cookies/storage), removes the row. **At least one Orbit always exists** — deleting the last is disallowed.
- **No `profileKey` reuse:** each Orbit's `profileKey` is generated once, unique, and never handed to a future Orbit (prevents inheriting a deleted Orbit's cookies).
- **Graceful degrade:** if `WebViewFeature.MULTI_PROFILE` is unsupported, Orbits still organize tabs; cookies are shared; show a one-time note. Never crash, never hide the feature.
- **Phase 1 shares** history, bookmarks, downloads, home shortcuts across Orbits (Phases 2–3 isolate them). Do NOT touch those DAOs.
- **Orbit UI uses Orbit tokens only** (`orbit()`, `OrbitSpacing`, `OrbitRadii`, orbit type styles, shared `OrbitTextField`) — flat/tonal, no hairline-bordered rectangles. Light + dark via tokens.
- **Build-verify ritual:** clean build (`./gradlew --stop` then `clean assembleRelease`), dex-verify a new-only string is present, md5-match the installed APK, force-stop before relaunch. Never claim installed from an exit code alone.
- **Secrets:** `keystore.properties`/`*.jks`/`.kotlin/` stay gitignored — never staged.

---

### Task 1: Orbit entity + DAO + Room v14 migration

**Files:**
- Create: `app/src/main/java/com/udaytank/browse/data/OrbitEntity.kt`
- Modify: `app/src/main/java/com/udaytank/browse/data/TabEntity.kt` (add `orbitId`)
- Modify: `app/src/main/java/com/udaytank/browse/data/BrowseDatabase.kt` (entity, version 14, dao, `MIGRATION_13_14`)
- Modify: `app/src/main/java/com/udaytank/browse/BrowseApplication.kt` (register migration)
- Test: `app/src/androidTest/java/com/udaytank/browse/MigrationTest.kt` (add 13→14 case, following existing migration-test pattern)

**Interfaces:**
- Produces: `OrbitEntity(id, name, colorArgb, position, profileKey)`; `OrbitDao` with `observeAll(): Flow<List<OrbitEntity>>`, `getAll(): List<OrbitEntity>`, `getById(id): OrbitEntity?`, `insert(e): Long`, `update(e)`, `deleteById(id)`, `count(): Int`. `TabEntity.orbitId: Long?`.

- [ ] **Step 1: Create `OrbitEntity.kt`**

```kotlin
package com.udaytank.browse.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "orbits")
data class OrbitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val colorArgb: Int,
    val position: Int,
    /** Stable ProfileStore profile name. Generated once at creation; never reused. */
    val profileKey: String,
)

@Dao
interface OrbitDao {
    @Query("SELECT * FROM orbits ORDER BY position ASC, id ASC")
    fun observeAll(): Flow<List<OrbitEntity>>

    @Query("SELECT * FROM orbits ORDER BY position ASC, id ASC")
    suspend fun getAll(): List<OrbitEntity>

    @Query("SELECT * FROM orbits WHERE id = :id")
    suspend fun getById(id: Long): OrbitEntity?

    @Query("SELECT COUNT(*) FROM orbits")
    suspend fun count(): Int

    @Insert
    suspend fun insert(orbit: OrbitEntity): Long

    @Update
    suspend fun update(orbit: OrbitEntity)

    @Query("DELETE FROM orbits WHERE id = :id")
    suspend fun deleteById(id: Long)
}
```

- [ ] **Step 2: Add `orbitId` to `TabEntity`**

In `TabEntity.kt`, add after `isIncognito`:
```kotlin
    val isIncognito: Boolean = false,
    val orbitId: Long? = null,
    val groupId: Long? = null,
```

- [ ] **Step 3: Wire the DB** — in `BrowseDatabase.kt`: add `OrbitEntity::class` to `entities`, bump `version = 14`, add `abstract fun orbitDao(): OrbitDao`, and add the migration:

```kotlin
val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `orbits` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`colorArgb` INTEGER NOT NULL, " +
                "`position` INTEGER NOT NULL, " +
                "`profileKey` TEXT NOT NULL)"
        )
        db.execSQL("ALTER TABLE tabs ADD COLUMN orbitId INTEGER")
        // Seed the default "Personal" Orbit and assign all existing (non-incognito) tabs to it.
        db.execSQL(
            "INSERT INTO orbits (name, colorArgb, position, profileKey) " +
                "VALUES ('Personal', " + DEFAULT_ORBIT_COLOR + ", 0, 'orbit_personal_default')"
        )
        db.execSQL(
            "UPDATE tabs SET orbitId = (SELECT id FROM orbits WHERE profileKey = 'orbit_personal_default') " +
                "WHERE isIncognito = 0"
        )
    }
}
```
Add `const val DEFAULT_ORBIT_COLOR = 0xFF2C5BE6.toInt()` (Orbit accent blue) in the companion. Register `MIGRATION_13_14` in `BrowseApplication.addMigrations(...)`.

- [ ] **Step 4: Add the instrumented migration test** (mirror the existing 12→13 test): open a v13 DB with one normal + one incognito tab, run `MIGRATION_13_14`, assert `orbits` has one row named "Personal", the normal tab's `orbitId` equals that row's id, and the incognito tab's `orbitId` IS NULL.

- [ ] **Step 5: Build + run migration test**
Run: `./gradlew testDebugUnitTest` (compile) then the instrumented migration test if an emulator is available; otherwise verify compile + schema `14.json` generated. Expected: PASS / schema written.

- [ ] **Step 6: Commit** — `git add app/ && git commit -m "feat(orbits): orbits table + TabEntity.orbitId + migration v14"`

---

### Task 2: OrbitRepository (CRUD, profileKey, last-Orbit guard)

**Files:**
- Create: `app/src/main/java/com/udaytank/browse/data/OrbitRepository.kt`
- Test: `app/src/test/java/com/udaytank/browse/OrbitRepositoryTest.kt`
- Test helper: `app/src/test/java/com/udaytank/browse/FakeOrbitDao.kt` (in-memory list impl of `OrbitDao`)

**Interfaces:**
- Consumes: `OrbitDao`.
- Produces: `OrbitRepository` with `observeAll(): Flow<List<OrbitEntity>>`, `ensureDefault(now): OrbitEntity` (creates "Personal" if the table is empty; returns the first Orbit), `create(name, colorArgb, now): OrbitEntity`, `rename(id, name)`, `setColor(id, colorArgb)`, `delete(id): Boolean` (false + no-op if it would remove the last Orbit), `get(id): OrbitEntity?`. `profileKey` generated as `"orbit_" + now + "_" + count`.

- [ ] **Step 1: Write the failing test** `OrbitRepositoryTest.kt`:

```kotlin
class OrbitRepositoryTest {
    private fun repo() = OrbitRepository(FakeOrbitDao(), io = kotlinx.coroutines.Dispatchers.Unconfined)

    @Test fun `create adds an orbit with a unique stable profileKey`() = runTest {
        val r = repo()
        val a = r.create("Work", 0x11, now = 1000L)
        val b = r.create("Shop", 0x22, now = 2000L)
        assertEquals("Work", a.name)
        assertNotEquals(a.profileKey, b.profileKey)
        assertTrue(a.profileKey.startsWith("orbit_"))
    }

    @Test fun `delete refuses to remove the last orbit`() = runTest {
        val r = repo()
        val only = r.create("Personal", 0x1, now = 1L)
        assertFalse(r.delete(only.id))            // last one -> refused
        assertNotNull(r.get(only.id))
        val second = r.create("Work", 0x2, now = 2L)
        assertTrue(r.delete(second.id))           // now allowed
        assertNull(r.get(second.id))
    }

    @Test fun `ensureDefault creates Personal only when empty`() = runTest {
        val r = repo()
        val first = r.ensureDefault(now = 5L)
        assertEquals("Personal", first.name)
        val again = r.ensureDefault(now = 6L)
        assertEquals(first.id, again.id)          // no duplicate
    }
}
```
Also write `FakeOrbitDao` (mutable list, autoincrement id, implements every `OrbitDao` method; `observeAll` via `MutableStateFlow`).

- [ ] **Step 2: Run test to verify it fails** — `./gradlew testDebugUnitTest --tests "*OrbitRepositoryTest*"` → FAIL (unresolved `OrbitRepository`).

- [ ] **Step 3: Implement `OrbitRepository.kt`**

```kotlin
package com.udaytank.browse.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class OrbitRepository(
    private val dao: OrbitDao,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    fun observeAll(): Flow<List<OrbitEntity>> = dao.observeAll()
    suspend fun get(id: Long): OrbitEntity? = withContext(io) { dao.getById(id) }

    suspend fun ensureDefault(now: Long): OrbitEntity = withContext(io) {
        dao.getAll().firstOrNull() ?: create("Personal", BrowseDatabase.DEFAULT_ORBIT_COLOR, now)
    }

    suspend fun create(name: String, colorArgb: Int, now: Long): OrbitEntity = withContext(io) {
        val count = dao.count()
        val key = "orbit_${now}_$count"
        val row = OrbitEntity(
            name = name.trim().ifBlank { "Orbit" }.take(30),
            colorArgb = colorArgb,
            position = count,
            profileKey = key,
        )
        row.copy(id = dao.insert(row))
    }

    suspend fun rename(id: Long, name: String) = withContext(io) {
        dao.getById(id)?.let { dao.update(it.copy(name = name.trim().ifBlank { it.name }.take(30))) }
    }

    suspend fun setColor(id: Long, colorArgb: Int) = withContext(io) {
        dao.getById(id)?.let { dao.update(it.copy(colorArgb = colorArgb)) }
    }

    /** Returns false (no-op) if this is the last Orbit — at least one must always remain. */
    suspend fun delete(id: Long): Boolean = withContext(io) {
        if (dao.count() <= 1) return@withContext false
        dao.deleteById(id)
        true
    }
}
```

- [ ] **Step 4: Run test to verify pass** — same command → PASS.
- [ ] **Step 5: Commit** — `git commit -m "feat(orbits): OrbitRepository with profileKey + last-orbit guard"`

---

### Task 3: `activeOrbitId` in settings

**Files:**
- Modify: `data/SettingsRepository.kt` (interface + `DataStoreSettingsRepository` + key)
- Modify: `app/src/test/java/com/udaytank/browse/FakeSettingsRepository.kt`
- Test: add to an existing settings test or a new small test.

**Interfaces:** Produces `val activeOrbitId: Flow<Long>` (default `0L` = "unset → resolve to first Orbit at the VM"); `suspend fun setActiveOrbitId(id: Long)`.

- [ ] **Step 1: Add to the `SettingsRepository` interface** (near `showNews`):
```kotlin
    /** Which Orbit is active. 0 = unset → VM resolves to the first Orbit. */
    val activeOrbitId: Flow<Long>
    suspend fun setActiveOrbitId(id: Long)
```
- [ ] **Step 2: Implement in `DataStoreSettingsRepository`:**
```kotlin
    override val activeOrbitId: Flow<Long> = dataStore.data.map { it[ACTIVE_ORBIT_ID_KEY] ?: 0L }
    override suspend fun setActiveOrbitId(id: Long) { dataStore.edit { it[ACTIVE_ORBIT_ID_KEY] = id } }
```
Add key: `val ACTIVE_ORBIT_ID_KEY = longPreferencesKey("active_orbit_id")` (import `androidx.datastore.preferences.core.longPreferencesKey`).
- [ ] **Step 3: Update `FakeSettingsRepository`:**
```kotlin
    override val activeOrbitId = MutableStateFlow(0L)
    override suspend fun setActiveOrbitId(id: Long) { activeOrbitId.value = id }
```
- [ ] **Step 4: Build** — `./gradlew testDebugUnitTest` (existing suite compiles with the new fake member). Expected: PASS.
- [ ] **Step 5: Commit** — `git commit -m "feat(orbits): persist activeOrbitId in settings"`

---

### Task 4: BrowserViewModel — Orbit state, tab filtering, actions

**Files:**
- Modify: `app/src/main/java/com/udaytank/browse/BrowserViewModel.kt` (+ `orbitRepository` ctor param + factory wiring)
- Modify: `BrowseApplication.kt` (`val orbitRepository by lazy { OrbitRepository(database.orbitDao()) }`)
- Test: `app/src/test/java/com/udaytank/browse/BrowserViewModelOrbitTest.kt`

**Interfaces:**
- Consumes: `OrbitRepository`, `SettingsRepository.activeOrbitId`.
- Produces (VM API): `val orbits: StateFlow<List<OrbitEntity>>`; `val activeOrbitId: StateFlow<Long>`; `fun activeOrbit(): OrbitEntity?`; `fun profileKeyForTab(tabId): String?`; `fun onSwitchOrbit(id)`; `fun onCreateOrbit(name, colorArgb)`; `fun onRenameOrbit(id, name)`; `fun onDeleteOrbit(id)`; `fun onOpenLinkInOrbit(url, orbitId)`. New tabs created via `tabManager.newTab(...)` must set `orbitId = activeOrbitId` for non-incognito.

- [ ] **Step 1: Write failing tests** (`BrowserViewModelOrbitTest.kt`) — using the existing VM test harness (`FakeSettingsRepository`, in-memory/fake DAOs, `OrbitRepository(FakeOrbitDao())`):
  - `onCreateOrbit adds an orbit and it appears in orbits flow`
  - `onSwitchOrbit updates activeOrbitId`
  - `new non-incognito tab inherits the active orbit`
  - `onDeleteOrbit closes that orbit's tabs and switches active away if it was active`
  - `first launch resolves activeOrbitId to the seeded Personal orbit (ensureDefault)`

  (Assert against `viewModel.orbits.value`, `viewModel.activeOrbitId.value`, and `viewModel.tabs.value` orbitIds. Follow the existing test file's construction of `BrowserViewModel`.)

- [ ] **Step 2: Run → FAIL** (`./gradlew testDebugUnitTest --tests "*OrbitTest*"`).

- [ ] **Step 3: Implement** in `BrowserViewModel.kt`:
  - Add ctor param `private val orbitRepository: OrbitRepository? = null` (nullable → tests may pass a real one over a fake dao).
  - On init (`viewModelScope.launch`): `orbitRepository?.ensureDefault(System.currentTimeMillis())`; if `settings.activeOrbitId` resolves to 0/absent, set it to the first Orbit's id.
  - `val orbits = (orbitRepository?.observeAll() ?: flowOf(emptyList())).stateIn(...)`.
  - `val activeOrbitId = settings.activeOrbitId.map { resolveOrFirst(it) }.stateIn(...)`.
  - Tab filtering: expose the active-orbit tab list (or add `orbitId` awareness where `normalTabs` are computed in the UI — the UI reads `activeOrbitId` + `orbits`; see Task 6).
  - `onSwitchOrbit(id)`: `settings.setActiveOrbitId(id)`, then activate that Orbit's most-recent tab or open a new home tab in it.
  - `onCreateOrbit`, `onRenameOrbit`: delegate to repo.
  - `onDeleteOrbit(id)`: if `repo.delete(id)` succeeds → close tabs where `orbitId == id` (via `tabManager`), delete the ProfileStore profile (call a `holder`/bridge hook — see Task 5), and if it was active switch to the first remaining Orbit.
  - `onOpenLinkInOrbit(url, orbitId)`: create a tab with that `orbitId` and switch active Orbit.
  - `profileKeyForTab(tabId)`: resolve `tab.orbitId → orbits.value.first { it.id == orbitId }.profileKey`.
  - Ensure `tabManager.newTab` calls set `orbitId` = active for non-incognito (update `TabManager.newTab` signature to accept `orbitId: Long?` and persist it).
  - Factory: pass `orbitRepository = app.orbitRepository`.

- [ ] **Step 4: Run → PASS.**
- [ ] **Step 5: Commit** — `git commit -m "feat(orbits): ViewModel orbit state, switching, create/rename/delete, tab inheritance"`

---

### Task 5: WebViewHolder per-Orbit profile + MainActivity wiring + profile deletion

**Files:**
- Modify: `app/src/main/java/com/udaytank/browse/ui/WebViewHolder.kt` (`obtain(tabId, incognito, profileKey)`, plus `fun deleteProfile(profileKey)`)
- Modify: `app/src/main/java/com/udaytank/browse/MainActivity.kt` (pass `viewModel.profileKeyForTab(tabId)` into `obtain`; wire profile deletion on Orbit delete)

**Interfaces:**
- Consumes: `profileKey: String?` per tab.
- Produces: `WebViewHolder.obtain(tabId, incognito, profileKey: String? = null)`; `WebViewHolder.deleteProfile(profileKey: String)`.

- [ ] **Step 1: Modify `obtain`** — add `profileKey: String? = null`. In the non-incognito branch, when `MULTI_PROFILE` supported and `profileKey != null`:
```kotlin
runCatching {
    val store = androidx.webkit.ProfileStore.getInstance()
    val profile = store.getOrCreateProfile(profileKey)
    androidx.webkit.WebViewCompat.setProfile(this, profile.name)
}
```
(Incognito branch unchanged — still `"incognito"`.)

- [ ] **Step 2: Add `deleteProfile`:**
```kotlin
fun deleteProfile(profileKey: String) {
    if (!WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) return
    runCatching { androidx.webkit.ProfileStore.getInstance().deleteProfile(profileKey) }
}
```
(Per the ProfileStore API, a profile can only be deleted when no WebView is using it — the VM must close that Orbit's tabs first; ordering handled in Task 4's `onDeleteOrbit`.)

- [ ] **Step 3: Wire `MainActivity`** — at every `holder.obtain(tabId, incognito)` call site, pass `profileKey = viewModel.profileKeyForTab(tabId)`. On Orbit delete, after tabs close, call `holder.deleteProfile(key)` for the deleted Orbit's key.

- [ ] **Step 4: Build** — `./gradlew assembleDebug`. Expected: compiles.
- [ ] **Step 5: Commit** — `git commit -m "feat(orbits): per-orbit WebView profile + profile deletion"`

---

### Task 6: Tab-switcher Orbit selector

**Files:**
- Create: `app/src/main/java/com/udaytank/browse/ui/components/OrbitSwitcherChips.kt`
- Modify: `app/src/main/java/com/udaytank/browse/ui/TabSwitcherScreen.kt` (replace the Tabs/Incognito mode switch with the Orbit selector; filter tabs by active Orbit)

**Interfaces:** Consumes `orbits: List<OrbitEntity>`, `activeOrbitId: Long`, `incognitoMode: Boolean`, callbacks `onSelectOrbit(id)`, `onSelectIncognito()`, `onAddOrbit()`. Replaces `SingleChoiceSegmentedButtonRow`-style mode.

- [ ] **Step 1: Build `OrbitSwitcherChips`** — a horizontally scrollable `Row` of chips: one per Orbit (a `Surface`, `RoundedCornerShape(OrbitRadii.pill)`, filled with `Color(orbit.colorArgb)` when selected else `orbit().surfaces.elevated`; shows a color dot + name + tab count), a trailing **"+"** chip (`onAddOrbit`), and an **Incognito** chip (`VisibilityOff` icon, selected when `incognitoMode`). Touch targets ≥44dp; selected animates via `OrbitMotion.standard()`.
- [ ] **Step 2: Integrate into `TabSwitcherScreen`** — replace the current `incognitoMode` segmented control with `OrbitSwitcherChips`. Add `incognitoMode` OR active-Orbit state. Tab filtering becomes:
```kotlin
val visibleTabs =
    if (incognitoMode) tabs.filter { it.isIncognito }
    else tabs.filter { !it.isIncognito && it.orbitId == activeOrbitId }
```
Preserve ALL existing behavior (swipe+undo, grid/list, groups, multi-select, HomeTabPreview). New-tab FAB creates a tab in the active Orbit (or incognito).
- [ ] **Step 3: Build + manual review** — `./gradlew assembleDebug`.
- [ ] **Step 4: Commit** — `git commit -m "feat(orbits): tab-switcher Orbit selector + per-orbit tab filtering"`

---

### Task 7: Colored indicator + quick-switch sheet

**Files:**
- Create: `app/src/main/java/com/udaytank/browse/ui/components/OrbitQuickSwitchSheet.kt`
- Modify: `ui/components/CommandBar.kt` (colored Orbit dot/ring, tap → open sheet) and the home top bar in `HomePage.kt`
- Modify: `ui/BrowserScreen.kt` (host the sheet; wire state)

**Interfaces:** `OrbitQuickSwitchSheet(orbits, activeOrbitId, onSwitch(id), onManage(), onDismiss())` — a `ModalBottomSheet` listing Orbits (color dot + name + tab count, current one checked) and a "Manage Orbits" row. A small colored ring (current Orbit color) sits on the command bar / home top bar; tapping opens the sheet.

- [ ] **Step 1:** Build `OrbitQuickSwitchSheet` (tonal rows, `orbit()` tokens).
- [ ] **Step 2:** Add the colored ring indicator to `CommandBar` (web) and the home top bar (`HomePage`), tap → `onOpenOrbitSwitch`.
- [ ] **Step 3:** In `BrowserScreen`, host the sheet (`var orbitSwitchOpen`) and wire switch/manage.
- [ ] **Step 4: Build.**
- [ ] **Step 5: Commit** — `git commit -m "feat(orbits): colored indicator + quick-switch sheet"`

---

### Task 8: Manage Orbits (add / rename / delete + color picker)

**Files:**
- Create: `app/src/main/java/com/udaytank/browse/ui/components/ManageOrbitsSheet.kt`
- Modify: `ui/BrowserScreen.kt` (host it; reachable from the quick-switch sheet's "Manage" and the "+" chip)

**Interfaces:** `ManageOrbitsSheet(orbits, onCreate(name,color), onRename(id,name), onDelete(id), onDismiss())`. Add flow: `OrbitTextField` for name + a `Row` of ~8 curated color swatches (define `OrbitColors: List<Int>` — accent-family ARGB values). Delete shows a confirm dialog and is disabled when `orbits.size <= 1`.

- [ ] **Step 1:** Define `OrbitColors` palette (8 ARGB constants, e.g. blue/violet/cyan/green/amber/rose/teal/slate).
- [ ] **Step 2:** Build the sheet: existing Orbits list with rename (inline `OrbitTextField`) + delete (confirm); an "Add Orbit" section (name + color swatches + Add button using `OrbitTextField`).
- [ ] **Step 3:** Host in `BrowserScreen`; wire to VM `onCreateOrbit`/`onRenameOrbit`/`onDeleteOrbit`.
- [ ] **Step 4: Build.**
- [ ] **Step 5: Commit** — `git commit -m "feat(orbits): manage orbits sheet (add/rename/delete + color picker)"`

---

### Task 9: "Open link in another Orbit" + MULTI_PROFILE note

**Files:**
- Modify: `ui/BrowserScreen.kt` (long-press context sheet: add an "Open in <Orbit>" row per *other* Orbit → `viewModel.onOpenLinkInOrbit(url, id)`)
- Modify: `ui/TabSwitcherScreen.kt` or a one-time banner: show a dismissible one-time note when `!WebViewFeature.isFeatureSupported(MULTI_PROFILE)` and more than one Orbit exists ("Update Android System WebView for full login separation between Orbits"). Persist a `seenOrbitProfileNote` flag in settings.

- [ ] **Step 1:** Add the context-sheet rows (only when >1 Orbit).
- [ ] **Step 2:** Add the one-time note + `seenOrbitProfileNote` DataStore flag + `FakeSettingsRepository` member.
- [ ] **Step 3: Build.**
- [ ] **Step 4: Commit** — `git commit -m "feat(orbits): open-link-in-orbit + old-WebView isolation note"`

---

### Task 10: Version bump + full integration build

**Files:**
- Modify: `app/build.gradle.kts` (`versionCode` 10, `versionName` "4.2")

- [ ] **Step 1:** Bump version.
- [ ] **Step 2: Clean build + dex-verify** — `./gradlew --stop`, then `./gradlew clean assembleRelease`; grep dex for a new-only string (e.g. `"Manage Orbits"`); confirm present.
- [ ] **Step 3: Run unit suite** — `./gradlew testReleaseUnitTest` → all pass.
- [ ] **Step 4: Commit** — `git commit -m "chore(orbits): bump to v4.2 (versionCode 10)"`

---

## Self-Review

- **Spec coverage:** data model (T1), profileKey+guard (T2), activeOrbitId (T3), VM state/switch/CRUD/inheritance/delete-wipe (T4/T5), per-Orbit isolation (T5), switcher chips (T6), indicator+quick switch (T7), manage+color (T8), open-in-orbit + fallback note (T9), version (T10). ✔ history/bookmarks/downloads correctly untouched (deferred).
- **Type consistency:** `profileKey: String`, `orbitId: Long?`, `colorArgb: Int`, `activeOrbitId: Long` used consistently across tasks. `OrbitRepository.create/delete` signatures match VM calls.
- **Global constraints** restated per-task where they bite (incognito null-orbit, delete wipes profile, no key reuse, graceful degrade, tokens-only UI, build ritual).
