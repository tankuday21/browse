# V3 Phase 1 — Tabs Power — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Tab groups (with auto-islands), tab search, 100-deep undo-close, pin + lock, list view, bulk actions, and the "∞" badge — spec items B1 B2 B3 B5 B6 B7 B9.

**Architecture:** All persistence rides the existing Room DB (migration v5→v6). Pure decision logic lives in small objects in `browser/` (unit-tested, TDD). `TabManager` stays the single write-through authority over tabs. UI work concentrates in `TabSwitcherScreen` (rework) plus a one-line `CommandBar` change. Incognito rules unchanged: negative ids, never persisted, never recorded to recently-closed.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), Room + KSP, DataStore, JUnit4 + kotlinx-coroutines-test.

## Global Constraints

- Branch: `feature/v3-p1-tabs` off `main`; merge `--no-ff`; tag `v3-phase-1` after owner/emulator verification.
- Package `com.udaytank.browse`; DB schema version becomes **6**; migrations must chain 1→6.
- Incognito tabs (id < 0): never persisted, never in `closed_tabs`, groups allowed in-memory only.
- No phase merges with failing tests (`gradlew.bat test` from `F:\Dev\Browse`).
- Run all commands from `F:\Dev\Browse` (Windows: `.\gradlew.bat`).

## File Structure

```
app/src/main/java/com/udaytank/browse/
  data/TabEntity.kt            (modify: +groupId/pinned/locked)
  data/TabDao.kt               (modify: +setGroup/setPinned/setLocked/clearGroup)
  data/TabGroupEntity.kt       (create)
  data/TabGroupDao.kt          (create)
  data/ClosedTabEntity.kt      (create)
  data/ClosedTabDao.kt         (create)
  data/BrowseDatabase.kt       (modify: v6 + MIGRATION_5_6)
  data/SettingsRepository.kt   (modify: +autoIslands, +switcherListLayout)
  browser/TabBadge.kt          (create)
  browser/TabSearchFilter.kt   (create)
  browser/TabOrderPolicy.kt    (create)
  browser/TabGroupPolicy.kt    (create)
  browser/TabManager.kt        (modify)
  BrowserViewModel.kt          (modify)
  ui/components/CommandBar.kt  (modify: badge label)
  ui/TabSwitcherScreen.kt      (rework)
  ui/SettingsScreen.kt         (modify: Tabs section)
app/src/test/java/com/udaytank/browse/
  FakeTabDao.kt                (modify)
  FakeTabGroupDao.kt           (create)
  FakeClosedTabDao.kt          (create)
  FakeSettingsRepository.kt    (modify)
  browser/TabBadgeTest.kt      (create)
  browser/TabSearchFilterTest.kt (create)
  browser/TabOrderPolicyTest.kt  (create)
  browser/TabGroupPolicyTest.kt  (create)
  browser/TabManagerTest.kt    (modify: new cases)
  BrowserViewModelTest.kt      (modify: new cases)
app/src/androidTest/java/com/udaytank/browse/
  BrowseDatabaseTest.kt        (modify: migration 5→6 + new DAO tests)
```

---

### Task 1: Schema v6 — entities, DAOs, migration

**Files:**
- Modify: `app/src/main/java/com/udaytank/browse/data/TabEntity.kt`
- Modify: `app/src/main/java/com/udaytank/browse/data/TabDao.kt`
- Create: `app/src/main/java/com/udaytank/browse/data/TabGroupEntity.kt`
- Create: `app/src/main/java/com/udaytank/browse/data/TabGroupDao.kt`
- Create: `app/src/main/java/com/udaytank/browse/data/ClosedTabEntity.kt`
- Create: `app/src/main/java/com/udaytank/browse/data/ClosedTabDao.kt`
- Modify: `app/src/main/java/com/udaytank/browse/data/BrowseDatabase.kt`
- Test: `app/src/androidTest/java/com/udaytank/browse/BrowseDatabaseTest.kt`

**Interfaces:**
- Produces: `TabEntity(id, url, title, position, isActive, isIncognito, groupId: Long?, pinned: Boolean, locked: Boolean)`; `TabGroupEntity(id: Long, name: String, color: Int, position: Int)`; `ClosedTabEntity(id: Long, url: String, title: String, closedAt: Long)`; `TabDao.setGroup(id, groupId)/setPinned(id, pinned)/setLocked(id, locked)/clearGroup(groupId)`; `TabGroupDao.insert/rename/deleteById/observeAll/getAll`; `ClosedTabDao.insert/trimTo(max)/observeRecent(limit)/deleteById/clear`.

- [ ] **Step 1: Write the failing migration + DAO tests** — add to `BrowseDatabaseTest.kt` (follow the file's existing migration-test pattern with `MigrationTestHelper`; the helper property already exists):

```kotlin
@Test
fun migrate5to6_preservesTabsAndAddsGroupTables() {
    helper.createDatabase(DB, 5).apply {
        execSQL(
            "INSERT INTO tabs (url, title, position, isActive, isIncognito) " +
                "VALUES ('https://a.com', 'A', 0, 1, 0)"
        )
        close()
    }
    val db = helper.runMigrationsAndValidate(DB, 6, true, BrowseDatabase.MIGRATION_5_6)
    db.query("SELECT groupId, pinned, locked FROM tabs").use { c ->
        assertTrue(c.moveToFirst())
        assertTrue(c.isNull(0))
        assertEquals(0, c.getInt(1))
        assertEquals(0, c.getInt(2))
    }
    db.query("SELECT COUNT(*) FROM tab_groups").use { c -> c.moveToFirst() } // table exists
    db.query("SELECT COUNT(*) FROM closed_tabs").use { c -> c.moveToFirst() } // table exists
}

@Test
fun closedTabDao_trimsToCap() = runBlocking {
    val dao = database.closedTabDao()
    repeat(105) { dao.insert(ClosedTabEntity(url = "https://x$it.com", title = "x$it", closedAt = it.toLong())) }
    dao.trimTo(100)
    val recent = dao.observeRecent(200).first()
    assertEquals(100, recent.size)
    assertEquals("x104", recent.first().title) // newest kept, ordered newest-first
}

@Test
fun tabGroupDao_roundTrip() = runBlocking {
    val dao = database.tabGroupDao()
    val id = dao.insert(TabGroupEntity(name = "Trip", color = 2, position = 0))
    dao.rename(id, "Holiday")
    assertEquals("Holiday", dao.getAll().single().name)
    dao.deleteById(id)
    assertTrue(dao.getAll().isEmpty())
}
```

- [ ] **Step 2: Run to verify failure**

Run: `.\gradlew.bat connectedDebugAndroidTest --tests "*BrowseDatabaseTest*"` (emulator running)
Expected: compile FAILURE — `MIGRATION_5_6`, `ClosedTabEntity`, `tabGroupDao` unresolved.

- [ ] **Step 3: Implement the schema.** `TabEntity.kt` — add three fields (keep defaults so existing constructors compile):

```kotlin
@Entity(tableName = "tabs")
data class TabEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String,
    val position: Int,
    val isActive: Boolean,
    val isIncognito: Boolean = false,
    val groupId: Long? = null,
    val pinned: Boolean = false,
    val locked: Boolean = false,
)
```

`TabGroupEntity.kt`:

```kotlin
package com.udaytank.browse.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tab_groups")
data class TabGroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Index into Orbit's group palette (0..5), not an ARGB value. */
    val color: Int,
    val position: Int,
)
```

`ClosedTabEntity.kt`:

```kotlin
package com.udaytank.browse.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "closed_tabs")
data class ClosedTabEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String,
    val closedAt: Long,
)
```

`TabGroupDao.kt`:

```kotlin
package com.udaytank.browse.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TabGroupDao {
    @Insert
    suspend fun insert(group: TabGroupEntity): Long

    @Query("UPDATE tab_groups SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM tab_groups WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM tab_groups ORDER BY position")
    fun observeAll(): Flow<List<TabGroupEntity>>

    @Query("SELECT * FROM tab_groups ORDER BY position")
    suspend fun getAll(): List<TabGroupEntity>
}
```

`ClosedTabDao.kt`:

```kotlin
package com.udaytank.browse.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ClosedTabDao {
    @Insert
    suspend fun insert(entry: ClosedTabEntity)

    /** Ring semantics: keep only the newest [max] rows. */
    @Query(
        "DELETE FROM closed_tabs WHERE id NOT IN " +
            "(SELECT id FROM closed_tabs ORDER BY closedAt DESC, id DESC LIMIT :max)"
    )
    suspend fun trimTo(max: Int)

    @Query("SELECT * FROM closed_tabs ORDER BY closedAt DESC, id DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<ClosedTabEntity>>

    @Query("DELETE FROM closed_tabs WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM closed_tabs")
    suspend fun clear()
}
```

`TabDao.kt` — add:

```kotlin
    @Query("UPDATE tabs SET groupId = :groupId WHERE id = :id")
    suspend fun setGroup(id: Long, groupId: Long?)

    @Query("UPDATE tabs SET pinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean)

    @Query("UPDATE tabs SET locked = :locked WHERE id = :id")
    suspend fun setLocked(id: Long, locked: Boolean)

    @Query("UPDATE tabs SET groupId = NULL WHERE groupId = :groupId")
    suspend fun clearGroup(groupId: Long)
```

`BrowseDatabase.kt` — bump to `version = 6`, add the two entities to `entities = [...]`, add `abstract fun tabGroupDao(): TabGroupDao` and `abstract fun closedTabDao(): ClosedTabDao`, and add:

```kotlin
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `tabs` ADD COLUMN `groupId` INTEGER")
                db.execSQL("ALTER TABLE `tabs` ADD COLUMN `pinned` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `tabs` ADD COLUMN `locked` INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `tab_groups` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, `color` INTEGER NOT NULL, `position` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `closed_tabs` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`url` TEXT NOT NULL, `title` TEXT NOT NULL, `closedAt` INTEGER NOT NULL)"
                )
            }
        }
```

Register `MIGRATION_5_6` wherever the database builder chains migrations (search for `MIGRATION_4_5` usage — `BrowserApp.kt` or the DI/database provider — and append).

- [ ] **Step 4: Run tests to verify pass**

Run: `.\gradlew.bat connectedDebugAndroidTest --tests "*BrowseDatabaseTest*"`
Expected: PASS (all, including the four pre-existing migration tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/udaytank/browse/data app/src/androidTest
git commit -m "feat(v3-p1): schema v6 - tab groups, closed-tabs ring, pin/lock columns"
```

---

### Task 2: TabBadge (B9)

**Files:**
- Create: `app/src/main/java/com/udaytank/browse/browser/TabBadge.kt`
- Test: `app/src/test/java/com/udaytank/browse/browser/TabBadgeTest.kt`

**Interfaces:**
- Produces: `TabBadge.label(count: Int): String` — used by `CommandBar` in Task 9.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.udaytank.browse.browser

import org.junit.Assert.assertEquals
import org.junit.Test

class TabBadgeTest {
    @Test fun `shows plain count below 100`() {
        assertEquals("1", TabBadge.label(1))
        assertEquals("99", TabBadge.label(99))
    }

    @Test fun `shows infinity at 100 and beyond`() {
        assertEquals("∞", TabBadge.label(100))
        assertEquals("∞", TabBadge.label(250))
    }

    @Test fun `zero and negatives clamp to 0`() {
        assertEquals("0", TabBadge.label(0))
        assertEquals("0", TabBadge.label(-3))
    }
}
```

- [ ] **Step 2: Run to verify failure** — `.\gradlew.bat test --tests "*TabBadgeTest*"` → FAIL: unresolved `TabBadge`.

- [ ] **Step 3: Implement**

```kotlin
package com.udaytank.browse.browser

object TabBadge {
    /** Command Bar tab counter; two chars max — 100+ tabs collapse to "∞". */
    fun label(count: Int): String = when {
        count >= 100 -> "∞"
        count < 0 -> "0"
        else -> count.toString()
    }
}
```

- [ ] **Step 4: Run to verify pass** — same command → PASS.
- [ ] **Step 5: Commit** — `git add app/src && git commit -m "feat(v3-p1): tab badge with infinity overflow"`

---

### Task 3: TabSearchFilter (B2)

**Files:**
- Create: `app/src/main/java/com/udaytank/browse/browser/TabSearchFilter.kt`
- Test: `app/src/test/java/com/udaytank/browse/browser/TabSearchFilterTest.kt`

**Interfaces:**
- Produces: `TabSearchFilter.filter(tabs: List<TabEntity>, query: String): List<TabEntity>` — used by `TabSwitcherScreen` (Task 10).

- [ ] **Step 1: Write the failing test**

```kotlin
package com.udaytank.browse.browser

import com.udaytank.browse.data.TabEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class TabSearchFilterTest {
    private fun tab(id: Long, url: String, title: String) =
        TabEntity(id = id, url = url, title = title, position = id.toInt(), isActive = false)

    private val tabs = listOf(
        tab(1, "https://kotlinlang.org/docs", "Kotlin Docs"),
        tab(2, "https://news.ycombinator.com", "Hacker News"),
        tab(3, "https://developer.android.com", "Android Developers"),
    )

    @Test fun `blank query returns everything`() {
        assertEquals(tabs, TabSearchFilter.filter(tabs, "  "))
    }

    @Test fun `matches title case-insensitively`() {
        assertEquals(listOf(1L), TabSearchFilter.filter(tabs, "kotlin d").map { it.id })
    }

    @Test fun `matches url when title does not`() {
        assertEquals(listOf(2L), TabSearchFilter.filter(tabs, "ycombinator").map { it.id })
    }

    @Test fun `no match returns empty`() {
        assertEquals(emptyList<TabEntity>(), TabSearchFilter.filter(tabs, "zzz"))
    }
}
```

- [ ] **Step 2: Run to verify failure** — `.\gradlew.bat test --tests "*TabSearchFilterTest*"` → FAIL.

- [ ] **Step 3: Implement**

```kotlin
package com.udaytank.browse.browser

import com.udaytank.browse.data.TabEntity

object TabSearchFilter {
    fun filter(tabs: List<TabEntity>, query: String): List<TabEntity> {
        val q = query.trim()
        if (q.isEmpty()) return tabs
        return tabs.filter {
            it.title.contains(q, ignoreCase = true) || it.url.contains(q, ignoreCase = true)
        }
    }
}
```

- [ ] **Step 4: Run to verify pass** → PASS.
- [ ] **Step 5: Commit** — `git commit -am "feat(v3-p1): tab search filter"`

---

### Task 4: TabOrderPolicy (B5 ordering + group clustering)

**Files:**
- Create: `app/src/main/java/com/udaytank/browse/browser/TabOrderPolicy.kt`
- Test: `app/src/test/java/com/udaytank/browse/browser/TabOrderPolicyTest.kt`

**Interfaces:**
- Consumes: `TabEntity` (Task 1 fields), `TabGroupEntity`.
- Produces: `TabOrderPolicy.ordered(tabs: List<TabEntity>, groups: List<TabGroupEntity>): List<TabEntity>` — display order for the switcher: pinned first (by position), then grouped tabs clustered in group-position order, then ungrouped by position.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.udaytank.browse.browser

import com.udaytank.browse.data.TabEntity
import com.udaytank.browse.data.TabGroupEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class TabOrderPolicyTest {
    private fun tab(id: Long, pos: Int, group: Long? = null, pinned: Boolean = false) =
        TabEntity(id = id, url = "u$id", title = "t$id", position = pos,
            isActive = false, groupId = group, pinned = pinned)

    @Test fun `pinned tabs come first regardless of position`() {
        val tabs = listOf(tab(1, 0), tab(2, 1, pinned = true), tab(3, 2))
        assertEquals(listOf(2L, 1L, 3L), TabOrderPolicy.ordered(tabs, emptyList()).map { it.id })
    }

    @Test fun `grouped tabs cluster together in group order`() {
        val groups = listOf(
            TabGroupEntity(id = 10, name = "B", color = 0, position = 1),
            TabGroupEntity(id = 20, name = "A", color = 1, position = 0),
        )
        val tabs = listOf(tab(1, 0, group = 10), tab(2, 1), tab(3, 2, group = 20), tab(4, 3, group = 10))
        // group 20 (position 0) first, then group 10 (position 1), then ungrouped
        assertEquals(listOf(3L, 1L, 4L, 2L), TabOrderPolicy.ordered(tabs, groups).map { it.id })
    }

    @Test fun `tab in an unknown group is treated as ungrouped`() {
        val tabs = listOf(tab(1, 0, group = 999), tab(2, 1))
        assertEquals(listOf(1L, 2L), TabOrderPolicy.ordered(tabs, emptyList()).map { it.id })
    }
}
```

- [ ] **Step 2: Run to verify failure** → FAIL.

- [ ] **Step 3: Implement**

```kotlin
package com.udaytank.browse.browser

import com.udaytank.browse.data.TabEntity
import com.udaytank.browse.data.TabGroupEntity

object TabOrderPolicy {
    /** Switcher display order: pinned → groups (by group position) → ungrouped. */
    fun ordered(tabs: List<TabEntity>, groups: List<TabGroupEntity>): List<TabEntity> {
        val knownGroups = groups.associateBy { it.id }
        val pinned = tabs.filter { it.pinned }.sortedBy { it.position }
        val rest = tabs.filterNot { it.pinned }
        val (grouped, ungrouped) = rest.partition { it.groupId != null && it.groupId in knownGroups }
        val clustered = grouped
            .sortedBy { it.position }
            .sortedBy { knownGroups.getValue(it.groupId!!).position }
        return pinned + clustered + ungrouped.sortedBy { it.position }
    }
}
```

- [ ] **Step 4: Run to verify pass** → PASS.
- [ ] **Step 5: Commit** — `git commit -am "feat(v3-p1): tab display ordering - pinned, groups, rest"`

---

### Task 5: TabGroupPolicy — auto-islands (B1)

**Files:**
- Create: `app/src/main/java/com/udaytank/browse/browser/TabGroupPolicy.kt`
- Test: `app/src/test/java/com/udaytank/browse/browser/TabGroupPolicyTest.kt`

**Interfaces:**
- Produces: `TabGroupPolicy.groupForNewTab(parent: TabEntity?, autoIslands: Boolean): Long?` — decision recorded in spec §3 P1: a child joins the parent's **existing** group; we do not auto-create groups (avoids surprise; manual grouping is primary).

- [ ] **Step 1: Write the failing test**

```kotlin
package com.udaytank.browse.browser

import com.udaytank.browse.data.TabEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TabGroupPolicyTest {
    private fun parent(group: Long?) = TabEntity(
        id = 1, url = "u", title = "t", position = 0, isActive = true, groupId = group)

    @Test fun `child joins parent's group when auto-islands on`() {
        assertEquals(7L, TabGroupPolicy.groupForNewTab(parent(7L), autoIslands = true))
    }

    @Test fun `no group when parent ungrouped`() {
        assertNull(TabGroupPolicy.groupForNewTab(parent(null), autoIslands = true))
    }

    @Test fun `no group when auto-islands off`() {
        assertNull(TabGroupPolicy.groupForNewTab(parent(7L), autoIslands = false))
    }

    @Test fun `no group without a parent`() {
        assertNull(TabGroupPolicy.groupForNewTab(null, autoIslands = true))
    }
}
```

- [ ] **Step 2: Run to verify failure** → FAIL.

- [ ] **Step 3: Implement**

```kotlin
package com.udaytank.browse.browser

import com.udaytank.browse.data.TabEntity

object TabGroupPolicy {
    /**
     * Auto-islands: a tab opened FROM another tab inherits that tab's group.
     * Joins existing groups only — never creates one (owner-approved decision, spec §3 P1).
     */
    fun groupForNewTab(parent: TabEntity?, autoIslands: Boolean): Long? =
        if (autoIslands) parent?.groupId else null
}
```

- [ ] **Step 4: Run to verify pass** → PASS.
- [ ] **Step 5: Commit** — `git commit -am "feat(v3-p1): auto-island policy - child joins parent's group"`

---

### Task 6: TabManager — groups, pin/lock, closed-tab recording (B1 B3 B5)

**Files:**
- Modify: `app/src/main/java/com/udaytank/browse/browser/TabManager.kt`
- Modify: `app/src/test/java/com/udaytank/browse/FakeTabDao.kt`
- Create: `app/src/test/java/com/udaytank/browse/FakeClosedTabDao.kt`
- Test: `app/src/test/java/com/udaytank/browse/browser/TabManagerTest.kt` (add cases)

**Interfaces:**
- Consumes: `ClosedTabDao`, `TabDao` new methods (Task 1), `TabGroupPolicy` (Task 5).
- Produces: `TabManager(tabDao, closedTabDao, now: () -> Long = System::currentTimeMillis)` — constructor gains two params; `newTab(url, incognito = false, groupId: Long? = null): Long`; `setGroup(id: Long, groupId: Long?)`, `setPinned(id: Long, pinned: Boolean)`, `setLocked(id: Long, locked: Boolean)` (all suspend, write-through, in-memory update); `closeTab` now records non-incognito tabs to the closed ring (cap 100) before removal. `BrowserViewModel` (Task 8) relies on these exact signatures.

- [ ] **Step 1: Extend fakes.** `FakeTabDao.kt` — add the four new overrides:

```kotlin
    override suspend fun setGroup(id: Long, groupId: Long?) {
        stored.replaceAll { if (it.id == id) it.copy(groupId = groupId) else it }
    }

    override suspend fun setPinned(id: Long, pinned: Boolean) {
        stored.replaceAll { if (it.id == id) it.copy(pinned = pinned) else it }
    }

    override suspend fun setLocked(id: Long, locked: Boolean) {
        stored.replaceAll { if (it.id == id) it.copy(locked = locked) else it }
    }

    override suspend fun clearGroup(groupId: Long) {
        stored.replaceAll { if (it.groupId == groupId) it.copy(groupId = null) else it }
    }
```

`FakeClosedTabDao.kt` (new file):

```kotlin
package com.udaytank.browse

import com.udaytank.browse.data.ClosedTabDao
import com.udaytank.browse.data.ClosedTabEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeClosedTabDao : ClosedTabDao {
    val entries = MutableStateFlow<List<ClosedTabEntity>>(emptyList())
    private var nextId = 1L

    override suspend fun insert(entry: ClosedTabEntity) {
        entries.value = entries.value + entry.copy(id = nextId++)
    }

    override suspend fun trimTo(max: Int) {
        entries.value = entries.value
            .sortedWith(compareByDescending<ClosedTabEntity> { it.closedAt }.thenByDescending { it.id })
            .take(max)
    }

    override fun observeRecent(limit: Int): Flow<List<ClosedTabEntity>> =
        entries.map { list ->
            list.sortedWith(compareByDescending<ClosedTabEntity> { it.closedAt }.thenByDescending { it.id })
                .take(limit)
        }

    override suspend fun deleteById(id: Long) {
        entries.value = entries.value.filterNot { it.id == id }
    }

    override suspend fun clear() { entries.value = emptyList() }
}
```

- [ ] **Step 2: Write the failing tests** — add to `TabManagerTest.kt` (the file already constructs `TabManager(FakeTabDao())`; update every existing construction site to `TabManager(tabDao, closedTabDao)` with a shared `val closedTabDao = FakeClosedTabDao()`):

```kotlin
    @Test
    fun `closing a normal tab records it in the closed ring`() = runTest {
        manager.initialize("home")
        val id = manager.newTab("https://a.com")
        manager.onContentChanged(id, "https://a.com", "Site A")
        manager.closeTab(id, "home")
        val closed = closedTabDao.entries.value.single()
        assertEquals("https://a.com", closed.url)
        assertEquals("Site A", closed.title)
    }

    @Test
    fun `closing an incognito tab records nothing`() = runTest {
        manager.initialize("home")
        val id = manager.newTab("https://secret.com", incognito = true)
        manager.closeTab(id, "home")
        assertTrue(closedTabDao.entries.value.isEmpty())
    }

    @Test
    fun `new tab can join a group and group setters write through`() = runTest {
        manager.initialize("home")
        val id = manager.newTab("https://a.com", groupId = 5L)
        assertEquals(5L, manager.tabs.value.first { it.id == id }.groupId)
        manager.setPinned(id, true)
        manager.setLocked(id, true)
        val tab = manager.tabs.value.first { it.id == id }
        assertTrue(tab.pinned)
        assertTrue(tab.locked)
        manager.setGroup(id, null)
        assertNull(manager.tabs.value.first { it.id == id }.groupId)
    }
```

- [ ] **Step 3: Run to verify failure** — `.\gradlew.bat test --tests "*TabManagerTest*"` → FAIL (constructor arity / missing methods).

- [ ] **Step 4: Implement in `TabManager.kt`:**

```kotlin
class TabManager(
    private val tabDao: TabDao,
    private val closedTabDao: ClosedTabDao,
    private val now: () -> Long = System::currentTimeMillis,
) {
```

`newTab` — change signature to `suspend fun newTab(url: String, incognito: Boolean = false, groupId: Long? = null): Long`; persist the group for normal tabs (`TabEntity(url = url, title = url, position = position, isActive = true, groupId = groupId)`) and carry `groupId = groupId` into the in-memory copy.

`closeTab` — before `_tabs.value = _tabs.value.filterNot { ... }`:

```kotlin
        val closing = _tabs.value.find { it.id == id }
        if (closing != null && !isIncognitoId(id)) {
            closedTabDao.insert(ClosedTabEntity(url = closing.url, title = closing.title, closedAt = now()))
            closedTabDao.trimTo(100)
        }
```

New mutators (same write-through shape as the rest of the class):

```kotlin
    suspend fun setGroup(id: Long, groupId: Long?) {
        _tabs.value = _tabs.value.map { if (it.id == id) it.copy(groupId = groupId) else it }
        if (!isIncognitoId(id)) tabDao.setGroup(id, groupId)
    }

    suspend fun setPinned(id: Long, pinned: Boolean) {
        _tabs.value = _tabs.value.map { if (it.id == id) it.copy(pinned = pinned) else it }
        if (!isIncognitoId(id)) tabDao.setPinned(id, pinned)
    }

    suspend fun setLocked(id: Long, locked: Boolean) {
        _tabs.value = _tabs.value.map { if (it.id == id) it.copy(locked = locked) else it }
        if (!isIncognitoId(id)) tabDao.setLocked(id, locked)
    }
```

- [ ] **Step 5: Run to verify pass** — `.\gradlew.bat test --tests "*TabManagerTest*"` → PASS (old + new cases).
- [ ] **Step 6: Commit** — `git commit -am "feat(v3-p1): TabManager groups, pin/lock, closed-tab ring recording"`

---

### Task 7: Settings — autoIslands + switcherListLayout

**Files:**
- Modify: `app/src/main/java/com/udaytank/browse/data/SettingsRepository.kt`
- Modify: `app/src/test/java/com/udaytank/browse/FakeSettingsRepository.kt`

**Interfaces:**
- Produces (added to the `SettingsRepository` interface, mirroring the existing `adBlockEnabled` pattern exactly): `val autoIslands: Flow<Boolean>` (default **true**), `suspend fun setAutoIslands(enabled: Boolean)`, `val switcherListLayout: Flow<Boolean>` (default **false**), `suspend fun setSwitcherListLayout(enabled: Boolean)`.

- [ ] **Step 1: Add to the interface + DataStore impl** — copy the existing boolean-preference pattern in the file (`booleanPreferencesKey`, `data.map { it[KEY] ?: default }`, `edit { it[KEY] = enabled }`) with keys `"auto_islands"` and `"switcher_list_layout"`.
- [ ] **Step 2: Mirror in `FakeSettingsRepository`** — `MutableStateFlow(true)` / `MutableStateFlow(false)` backed, same as the other fake booleans.
- [ ] **Step 3: Compile check** — `.\gradlew.bat compileDebugKotlin testDebugUnitTest` → BUILD SUCCESSFUL (interface + fake stay in sync; any VM test compile break here means a fake member is missing).
- [ ] **Step 4: Commit** — `git commit -am "feat(v3-p1): auto-islands and switcher-layout settings"`

---

### Task 8: BrowserViewModel wiring

**Files:**
- Modify: `app/src/main/java/com/udaytank/browse/BrowserViewModel.kt`
- Create: `app/src/test/java/com/udaytank/browse/FakeTabGroupDao.kt`
- Test: `app/src/test/java/com/udaytank/browse/BrowserViewModelTest.kt` (add cases)

**Interfaces:**
- Consumes: everything from Tasks 1–7.
- Produces (Task 10's UI calls exactly these):
  - Constructor gains `tabGroupDao: TabGroupDao` and `closedTabDao: ClosedTabDao` (add to all test construction sites; `TabManager(tabDao, closedTabDao)`).
  - `val tabGroups: StateFlow<List<TabGroupEntity>>` (from `tabGroupDao.observeAll()`, `stateIn` eagerly, initial `emptyList()`)
  - `val recentlyClosed: StateFlow<List<ClosedTabEntity>>` (from `closedTabDao.observeRecent(100)`)
  - `val switcherListLayout: StateFlow<Boolean>`, `fun onSwitcherLayoutToggled()`
  - `val autoIslands: StateFlow<Boolean>`, `fun onAutoIslandsToggled(enabled: Boolean)`
  - `fun onCreateGroupWithTabs(name: String, tabIds: List<Long>)` — inserts `TabGroupEntity(name = name, color = tabGroups.value.size % 6, position = tabGroups.value.size)`, then `tabManager.setGroup(id, newGroupId)` per tab
  - `fun onRenameGroup(id: Long, name: String)`, `fun onDeleteGroup(id: Long)` (dao.clearGroup + dao.deleteById + in-memory `tabManager.setGroup(tab.id, null)` for members)
  - `fun onAssignTabToGroup(tabId: Long, groupId: Long?)`
  - `fun onTogglePinned(tabId: Long)`, `fun onToggleLocked(tabId: Long)`
  - `fun onCloseTab(id: Long)` — CHANGED: if the tab is `locked`, set `uiState.confirmCloseTabId = id` instead of closing; `fun onConfirmClose()` closes it; `fun onCloseCancelled()` clears
  - `BrowserUiState` gains `val confirmCloseTabId: Long? = null`
  - `fun onCloseTabs(ids: List<Long>)` (skips locked tabs), `fun onReopenClosed(entry: ClosedTabEntity)` (newTab(entry.url) + `closedTabDao.deleteById(entry.id)`)
  - `onOpenInNewTab(url)` — CHANGED: computes `groupId = TabGroupPolicy.groupForNewTab(parentTab, autoIslands.value)` and passes it to `tabManager.newTab(url, incognito, groupId)`
  - `onNewTab()` / `onNewIncognitoTab()` / `onExternalUrl()` unchanged behavior (no group).

- [ ] **Step 1: Create `FakeTabGroupDao.kt`**

```kotlin
package com.udaytank.browse

import com.udaytank.browse.data.TabGroupDao
import com.udaytank.browse.data.TabGroupEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeTabGroupDao : TabGroupDao {
    val groups = MutableStateFlow<List<TabGroupEntity>>(emptyList())
    private var nextId = 1L

    override suspend fun insert(group: TabGroupEntity): Long {
        val id = nextId++
        groups.value = groups.value + group.copy(id = id)
        return id
    }

    override suspend fun rename(id: Long, name: String) {
        groups.value = groups.value.map { if (it.id == id) it.copy(name = name) else it }
    }

    override suspend fun deleteById(id: Long) {
        groups.value = groups.value.filterNot { it.id == id }
    }

    override fun observeAll(): Flow<List<TabGroupEntity>> = groups

    override suspend fun getAll(): List<TabGroupEntity> = groups.value
}
```

- [ ] **Step 2: Write the failing VM tests** (follow the file's existing pattern — `MainDispatcherRule`, `runTest`, `advanceUntilIdle`):

```kotlin
    @Test
    fun `closing a locked tab asks for confirmation first`() = runTest {
        val vm = makeViewModel() // update the file's existing factory to pass the two new fakes
        advanceUntilIdle()
        val id = vm.tabs.value.first().id
        vm.onToggleLocked(id); advanceUntilIdle()
        vm.onCloseTab(id); advanceUntilIdle()
        assertEquals(id, vm.uiState.value.confirmCloseTabId)
        assertTrue(vm.tabs.value.any { it.id == id }) // still open
        vm.onConfirmClose(); advanceUntilIdle()
        assertNull(vm.uiState.value.confirmCloseTabId)
        assertTrue(vm.tabs.value.none { it.id == id })
    }

    @Test
    fun `create group with tabs assigns and colors it`() = runTest {
        val vm = makeViewModel(); advanceUntilIdle()
        vm.onNewTab(); advanceUntilIdle()
        val ids = vm.tabs.value.map { it.id }
        vm.onCreateGroupWithTabs("Research", ids); advanceUntilIdle()
        val group = vm.tabGroups.value.single()
        assertEquals("Research", group.name)
        assertTrue(vm.tabs.value.all { it.groupId == group.id })
    }

    @Test
    fun `open-in-new-tab joins parent group when auto-islands on`() = runTest {
        val vm = makeViewModel(); advanceUntilIdle()
        val parentId = vm.tabs.value.first().id
        vm.onCreateGroupWithTabs("Island", listOf(parentId)); advanceUntilIdle()
        vm.onOpenInNewTab("https://child.com"); advanceUntilIdle()
        val group = vm.tabGroups.value.single()
        val child = vm.tabs.value.first { it.url == "https://child.com" }
        assertEquals(group.id, child.groupId)
    }

    @Test
    fun `reopen closed tab restores url and removes ring entry`() = runTest {
        val vm = makeViewModel(); advanceUntilIdle()
        val id = vm.tabManagerNewTabForTest("https://gone.com") // or: vm.onOpenUrl + close; use the file's existing helper style
        vm.onCloseTab(id); advanceUntilIdle()
        val entry = vm.recentlyClosed.value.first()
        vm.onReopenClosed(entry); advanceUntilIdle()
        assertTrue(vm.tabs.value.any { it.url == "https://gone.com" })
        assertTrue(vm.recentlyClosed.value.none { it.id == entry.id })
    }
```

(If the test file has no helper for opening a URL-bearing tab, use `vm.onOpenInNewTab("https://gone.com")` before grouping exists — it lands ungrouped and works for this test.)

- [ ] **Step 3: Run to verify failure** — `.\gradlew.bat test --tests "*BrowserViewModelTest*"` → FAIL.
- [ ] **Step 4: Implement** all members listed in **Produces** above. Key snippets — locked-close gate:

```kotlin
    fun onCloseTab(id: Long) {
        val tab = tabs.value.find { it.id == id }
        if (tab?.locked == true) {
            _uiState.update { it.copy(confirmCloseTabId = id) }
            return
        }
        viewModelScope.launch { tabManager.closeTab(id, HOME_URL) }
    }

    fun onConfirmClose() {
        val id = _uiState.value.confirmCloseTabId ?: return
        _uiState.update { it.copy(confirmCloseTabId = null) }
        viewModelScope.launch { tabManager.closeTab(id, HOME_URL) }
    }

    fun onCloseCancelled() = _uiState.update { it.copy(confirmCloseTabId = null) }
```

auto-island in `onOpenInNewTab`:

```kotlin
    fun onOpenInNewTab(url: String) {
        val parent = tabs.value.find { it.id == activeTabId.value }
        val incognito = parent?.isIncognito == true
        val groupId = TabGroupPolicy.groupForNewTab(parent, autoIslands.value)
        viewModelScope.launch { tabManager.newTab(url, incognito, groupId) }
        onContextMenuDismissed()
    }
```

Update the production construction site of `BrowserViewModel`/`TabManager` (factory in `MainActivity.kt`/`BrowserApp.kt` — search for `BrowserViewModel(`) to pass `database.tabGroupDao()` and `database.closedTabDao()`.

- [ ] **Step 5: Run the whole unit suite** — `.\gradlew.bat test` → PASS (all ~90).
- [ ] **Step 6: Commit** — `git commit -am "feat(v3-p1): VM - groups, locked-close confirm, reopen closed, auto-islands"`

---

### Task 9: CommandBar ∞ badge (B9 UI)

**Files:**
- Modify: `app/src/main/java/com/udaytank/browse/ui/components/CommandBar.kt`

**Interfaces:**
- Consumes: `TabBadge.label(count)` (Task 2).

- [ ] **Step 1:** Find the tab-count `Text(...)` in `CommandBar.kt` (renders `tabCount.toString()` or similar) and replace the label expression with `TabBadge.label(tabCount)` (import `com.udaytank.browse.browser.TabBadge`).
- [ ] **Step 2:** `.\gradlew.bat compileDebugKotlin` → BUILD SUCCESSFUL.
- [ ] **Step 3: Commit** — `git commit -am "feat(v3-p1): infinity tab badge in Command Bar"`

---

### Task 10: TabSwitcherScreen rework (B1 B2 B3 B5 B6 B7 UI)

**Files:**
- Modify: `app/src/main/java/com/udaytank/browse/ui/TabSwitcherScreen.kt`

**Interfaces:**
- Consumes: `viewModel.tabGroups`, `recentlyClosed`, `switcherListLayout`, `onSwitcherLayoutToggled`, `onCreateGroupWithTabs`, `onRenameGroup`, `onDeleteGroup`, `onAssignTabToGroup`, `onTogglePinned`, `onToggleLocked`, `onCloseTabs`, `onReopenClosed`, `uiState.confirmCloseTabId`, `onConfirmClose`, `onCloseCancelled` (Task 8); `TabSearchFilter` (Task 3); `TabOrderPolicy` (Task 4).

This is UI composition — no unit tests; verified on the emulator in Task 12. Build it in this order, compiling between steps (`.\gradlew.bat compileDebugKotlin`):

- [ ] **Step 1: State + toolbar.** Local state: `var searchQuery by remember { mutableStateOf<String?>(null) }` (null = closed), `var selection by remember { mutableStateOf<Set<Long>>(emptySet()) }` (non-empty = selection mode), `var showRecentlyClosed by remember { mutableStateOf(false) }`, `var collapsedGroups by remember { mutableStateOf<Set<Long>>(emptySet()) }`. TopAppBar gains three actions: Search (toggles an `OutlinedTextField` row under the bar, filters via `TabSearchFilter.filter(tabs, searchQuery.orEmpty())`), layout toggle (icon `Icons.AutoMirrored.Filled.List` / `Icons.Filled.GridView` → `viewModel.onSwitcherLayoutToggled()`), History icon → `showRecentlyClosed = true`.
- [ ] **Step 2: Ordering + grouping render.** Compute `val ordered = TabOrderPolicy.ordered(filteredTabs, groups)`. Render as `LazyVerticalGrid(columns = if (listLayout) GridCells.Fixed(1) else GridCells.Fixed(2))`. Before each group's first tab, emit a full-width group header item (`span = { GridItemSpan(maxLineSpan) }`): colored dot (Orbit palette index `group.color`), name, member count, collapse chevron (toggles id in `collapsedGroups` — collapsed groups render header only), and a ⋮ menu with **Rename** (AlertDialog + TextField → `onRenameGroup`) and **Ungroup** (→ `onDeleteGroup`). Group color palette: `val GroupColors = listOf(Color(0xFF35C3F3), Color(0xFF1E4FD8), Color(0xFF9C6BFF), Color(0xFF3DDC97), Color(0xFFFFB84D), Color(0xFFFF6B8A))` (file-level, matches Orbit accents).
- [ ] **Step 3: Card upgrades.** `TabCard` gains `pinned`/`locked` marker icons (small `Icons.Filled.PushPin` / `Icons.Filled.Lock` overlay at TopStart, 14.dp, same translucent Surface treatment as the close button) and `onLongPress` via `Modifier.combinedClickable(onClick = onSelect, onLongClick = onLongPress)`. Long-press behavior: if `selection` empty → open a `DropdownMenu` on the card with items **Pin/Unpin**, **Lock/Unlock**, **Add to group…** (submenu listing groups + "New group…" which prompts for a name → `onCreateGroupWithTabs(name, listOf(tab.id))`), **Remove from group** (when grouped), **Select** (starts selection mode). In selection mode, cards show a checkmark overlay and clicks toggle membership instead of switching.
- [ ] **Step 4: Selection action bar.** When `selection` non-empty, a `BottomAppBar` replaces the FAB: `Close (n)` → `viewModel.onCloseTabs(selection.toList()); selection = emptySet()`, `Group` → name dialog → `onCreateGroupWithTabs(name, selection.toList())`, `Share` → `Intent.ACTION_SEND` with the selected tabs' URLs joined by newline (`context.startActivity(Intent.createChooser(...))`), `✕` clears selection.
- [ ] **Step 5: Recently-closed sheet.** `ModalBottomSheet(onDismissRequest = { showRecentlyClosed = false })` listing `recentlyClosed` (title + url, newest first); tap → `viewModel.onReopenClosed(entry); onTabChosen()`.
- [ ] **Step 6: Locked-close confirm dialog.** Observe `uiState.confirmCloseTabId`; when non-null show `AlertDialog("Close locked tab?", confirm = viewModel::onConfirmClose, dismiss = viewModel::onCloseCancelled)`. Also add the same dialog hookup in `BrowserScreen.kt` if the close path is reachable there (menu "close tab" does not exist today — switcher-only is fine; note it).
- [ ] **Step 7:** Full build — `.\gradlew.bat assembleDebug` → BUILD SUCCESSFUL.
- [ ] **Step 8: Commit** — `git commit -am "feat(v3-p1): switcher rework - groups, search, layouts, selection, recently closed"`

---

### Task 11: Settings > Tabs section

**Files:**
- Modify: `app/src/main/java/com/udaytank/browse/ui/SettingsScreen.kt`

- [ ] **Step 1:** After the "Theme" section and before "Privacy", add a "Tabs" section header (same `titleSmall` + primary-color pattern) with one switch row (same Row+Switch pattern as "Block ads"): label **"Group tabs opened from links"**, checked = `autoIslands` (collect from `viewModel.autoIslands`), onCheckedChange = `viewModel::onAutoIslandsToggled`.
- [ ] **Step 2:** `.\gradlew.bat assembleDebug` → BUILD SUCCESSFUL.
- [ ] **Step 3: Commit** — `git commit -am "feat(v3-p1): auto-islands toggle in settings"`

---

### Task 12: Verification, merge, tag

- [ ] **Step 1: Full test suites.** `.\gradlew.bat test` → all green; `.\gradlew.bat connectedDebugAndroidTest` (emulator) → all green including migration 5→6.
- [ ] **Step 2: Emulator walkthrough** (launch `emulator.exe -avd Medium_Phone_API_36.1 -no-snapshot-save -no-boot-anim`, wait for `sys.boot_completed`; screenshots per existing adb workflow):
  1. Open 3 tabs → switcher shows grid; toggle list view; toggle back (setting survives app restart).
  2. Long-press a card → New group "Test" → colored header appears; open a link from a grouped tab via page long-press → Open in new tab → lands inside "Test" (auto-island). Toggle setting off → repeat → lands ungrouped.
  3. Rename the group; collapse it; ungroup it.
  4. Search: type a word from one tab's title → only that card remains.
  5. Close a tab → snackbar path not built (recently-closed sheet is the path): open Recently closed → the tab is listed → tap → it reopens.
  6. Pin a tab → it sorts first with pin marker and survives app kill + relaunch. Lock a tab → close shows confirm dialog; cancel keeps it.
  7. Selection mode: select 2 tabs → Close(2) works; select 2 → Group works.
  8. Incognito: close an incognito tab → NOT in recently closed. Badge: open tabs until UI shows "∞" is impractical manually — trust `TabBadgeTest`; visually confirm normal counts render.
  9. `adb logcat -d | grep -i "FATAL"` → empty.
- [ ] **Step 3: Merge + tag.**

```bash
git checkout main && git merge --no-ff feature/v3-p1-tabs -m "merge: v3 phase 1 - tabs power"
git tag v3-phase-1 && git push origin main --tags
```

---

## Self-review notes (done)

- Spec coverage: B1 (Tasks 1,5,6,8,10,11) · B2 (3,10) · B3 (1,6,8,10) · B5 (1,6,8,10) · B6 (7,8,10) · B7 (8,10) · B9 (2,9). Gap check: spec's "restoring a group restores all members" (B3) — closed_tabs stores single tabs; group-restore ships when a whole group is closed via selection-Close (each member lands in the ring individually and can be reopened one-by-one). Recorded as acceptable P1 behavior; full group-snapshot restore folds into B8 Workspaces (v5).
- Type consistency: `TabManager(tabDao, closedTabDao, now)`, `newTab(url, incognito, groupId)`, `TabBadge.label`, `TabSearchFilter.filter`, `TabOrderPolicy.ordered`, `TabGroupPolicy.groupForNewTab` used identically across Tasks 6–10.
- Placeholder scan: clean — every code step shows the code; UI Task 10 is stepwise-buildable composition with exact component names and callbacks.
