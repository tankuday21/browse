# Phase 3: Privacy Suite Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Incognito tabs that leave no trace on the device (no history entries, no tab persistence, no cache/storage writes), privacy controls in settings (JavaScript toggle, cookie toggle, clear browsing data), and an SSL warning per spec §6 (never silently proceed).

**Architecture:** `TabEntity` gains `isIncognito` (DB migration v2→v3; incognito rows are **never written** — the column exists only for schema consistency since the same class is the in-memory model). `TabManager` assigns incognito tabs **negative ids** from an in-memory counter and skips all DAO write-through for them — process death erases them by construction. `WebViewHolder` configures incognito WebViews with no DOM storage and `LOAD_NO_CACHE`; global JS/cookie toggles apply via `applyPolicy`. History recording checks the tab's incognito flag in the ViewModel. SSL errors cancel the load (WebView default) AND surface a visible warning overlay.

**Honest limitation (documented, backlogged):** Android WebView cookies are global (`CookieManager` is a singleton) — first-party cookies set during incognito browsing are not isolated from normal tabs. True isolation needs the androidx.webkit `ProfileStore` API (WebView 113+); deferred to backlog. Incognito v1 guarantees: no history, no tab persistence, no cache/DOM-storage writes.

## Global Constraints

- Branch `feature/privacy-suite`, merge `--no-ff`, push, then tag nothing (phase-3 tag after acceptance)
- Migration v2→v3 required — installed devices have v2 data
- Spec §6: SSL errors must never silently proceed — we cancel AND warn; no "proceed anyway" option in v1

---

### Task 1: Migration v2→v3 — `isIncognito` column

- `TabEntity` gains `val isIncognito: Boolean = false`
- `BrowseDatabase` → version 3, add:

```kotlin
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `tabs` ADD COLUMN `isIncognito` INTEGER NOT NULL DEFAULT 0")
    }
}
```

- `BrowseApplication`: `.addMigrations(BrowseDatabase.MIGRATION_1_2, BrowseDatabase.MIGRATION_2_3)`
- Instrumented test: insert tab with `isIncognito = true`, read it back
- Run `connectedDebugAndroidTest`, commit `feat: isIncognito column with v2-to-v3 migration`

### Task 2: TabManager — in-memory incognito tabs

- `newTab(url: String, incognito: Boolean = false): Long` — incognito: `id = nextIncognitoId--` (starts at `-1`), no DAO insert/setActive; entity has `isIncognito = true`
- `switchTo`: skip `tabDao.setActive` when target id < 0
- `closeTab`: skip `tabDao.deleteById` for id < 0
- `onContentChanged`: skip `tabDao.updateContent` for id < 0
- Tests: incognito tab never appears in `FakeTabDao.stored`; ids are negative; close works; mixed normal+incognito list behaves; `initialize` after "process death" (new manager, same dao) restores only normal tabs
- Commit `feat: in-memory incognito tabs with negative ids`

### Task 3: Settings — JS toggle, cookie toggle; ViewModel privacy wiring

- `SettingsRepository`: `javaScriptEnabled: Flow<Boolean>` (default true), `cookiesEnabled: Flow<Boolean>` (default true) + setters (`booleanPreferencesKey`); update fake
- VM: expose both as `StateFlow` (Eagerly), `onJavaScriptToggled`, `onCookiesToggled`, `onNewIncognitoTab()`
- VM `onPageFinished`: skip history recording when the tab is incognito:

```kotlin
val isIncognito = tabs.value.find { it.id == tabId }?.isIncognito == true
if (!isIncognito) {
    viewModelScope.launch { /* existing VisitPolicy block */ }
}
```

- Tests: `incognito page visits are never recorded`, `incognito tab is not persisted`, toggles round-trip through fake
- Commit `feat: privacy settings and incognito history exclusion`

### Task 4: WebViewHolder — per-mode config, policy application, SSL, clear data

- `obtain(tabId: Long, incognito: Boolean = false)`: incognito → `domStorageEnabled = false`, `cacheMode = WebSettings.LOAD_NO_CACHE`; JS from current policy
- `applyPolicy(jsEnabled: Boolean, cookiesEnabled: Boolean)`: sets JS on all live WebViews + remembers for new ones; `CookieManager.getInstance().setAcceptCookie(cookiesEnabled)`
- `clearBrowsingData()`: `clearCache(true)` on all live WebViews, `CookieManager.removeAllCookies(null)`, `WebStorage.getInstance().deleteAllData()`
- Listener gains `fun onSslError(tabId: Long, url: String)`; override `onReceivedSslError`: `handler.cancel()` (never proceed) + notify
- VM: `onSslError(tabId, url)` → if active tab, `uiState.sslWarningUrl = url`; `onSslWarningDismissed()` clears it (add `sslWarningUrl: String? = null` to `BrowserUiState`)
- Commit `feat: privacy-aware WebView config, SSL blocking, clear browsing data`

### Task 5: UI — incognito entry points and styling, settings switches, SSL overlay

- Browser ⋮ menu: "New incognito tab" → `viewModel.onNewIncognitoTab()`
- Address bar placeholder when active tab incognito: "Search privately"
- Home page: when incognito, show "Incognito" title + "Pages you view won't appear in history" (pass `isIncognito` param)
- Tab switcher: incognito cards use `surfaceVariant` + a "Incognito" label; normal active-tab highlight unchanged
- Settings: "Privacy" section — Switch rows for JavaScript and Accept cookies; "Clear browsing data" row → confirmation dialog → clears history (VM) + holder.clearBrowsingData() (wired via lambda from MainActivity); Snackbar/Toast confirmation
- BrowserScreen: SSL warning overlay when `sslWarningUrl != null` — full-width error card: "Connection not secure — Browse blocked {host}" + "OK" dismiss
- MainActivity: `LaunchedEffect` collecting js/cookie StateFlows → `holder.applyPolicy(...)`; pass clear-data lambda to Settings route; `TabWebView` call passes `incognito = activeTab.isIncognito`
- Build + full unit suite; commit `feat: incognito UI, privacy settings screen, SSL warning overlay`

### Task 6: Acceptance, merge, push, tag

1. Migration: app opens over v2 install, tabs intact
2. ⋮ → New incognito tab → home page shows Incognito notice; address bar hints "Search privately"
3. Browse several pages in incognito → History shows **nothing** from that session
4. Tab switcher: incognito tab visibly distinct; normal tabs unaffected
5. Kill app → reopen → incognito tabs **gone**, normal tabs restored
6. Settings → disable JavaScript → JS-dependent page (e.g. google.com results interactivity) degrades; re-enable → works
7. Settings → Clear browsing data → history empties, sites forget you (logins gone)
8. Visit `https://expired.badssl.com/` → page does NOT load + warning overlay appears; dismiss works; normal sites unaffected
9. Regression: tabs, bookmarks, downloads, search engine, theme

Merge `--no-ff`, push, `git tag -a phase-3`, push tag.
