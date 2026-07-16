# Black Hole — Panic Wipe (v4.5)

**Goal:** One deliberate action erases **every** browsing trace on the device and returns the
browser to a factory-clean state: no tabs, no Orbits (bar a fresh default), no history,
bookmarks, shortcuts, downloads, reading list, closed tabs, tab groups, site settings, zapped
elements, favicons, feed items, cookies, web storage, cache, thumbnails, or downloaded files.

## UX / trigger

A **Danger Zone** entry at the very bottom of Settings: a red "Black Hole — erase everything"
row. Tapping it opens a firm confirm dialog that lists exactly what is erased and warns it is
**irreversible**, with a red "Erase everything" confirm and a "Cancel". No accidental one-tap;
no undo. (A faster gesture trigger is deferred — safety first for v1.)

## Design: wipe, then restart the process

The robust, race-free approach (avoids fighting live in-memory WebView/tab state): perform the
full wipe, then **relaunch the app process**. On cold start the existing init path
(`ensureDefault` seeds "Personal"; `TabManager.initialize` on an empty DB creates one home tab)
rebuilds a pristine state automatically — no manual re-seed, no half-torn-down WebViews.

### Sequence

1. **User confirms** → `viewModel.onBlackHole()`.
2. VM captures every profile key first: `orbits.value.map { it.profileKey } + "incognito"`.
3. VM wipes all Room tables (see below), deletes on-disk trace files, resets settings keys.
4. VM emits `blackHoleReady(profileKeys)` (a `replay = 1` SharedFlow, mirroring
   `orbitProfileToDelete`).
5. MainActivity collects it → `holder.destroyAll()` → `holder.deleteProfile(key)` for every key
   (orbit profiles + `"incognito"`) → `holder.clearBrowsingData()` (cookies/web storage/cache) →
   `holder.clearThumbnails()` → **restart the process** (relaunch intent + `Runtime.exit(0)`).
6. Fresh process cold-starts → clean Personal Orbit + one home tab.

Profiles must be deleted only after `destroyAll()` (ProfileStore refuses a profile still in use).
Deleting each profile is required because per-profile partitioned cookie/DOM storage is not
guaranteed to be cleared by the global `CookieManager`/`WebStorage` calls alone.

## Data wipe (Room)

Add a global `clearAll()` (`DELETE FROM <table>`) to the DAOs lacking one:
bookmarks, home_shortcuts, downloads, reading_list, tab_groups, site_settings,
zapped_elements, favicons, tabs, orbits. Reuse existing: `HistoryDao.clearAll()`,
`ClosedTabDao.clear()`, `FeedDao.clearItems()`. (rss_sources = seeded defaults, kept.)

## On-disk trace files

- **Downloaded files**: iterate `downloadDao.getAll()`, delete each non-null `filePath`, then
  clear the table. (Active transfers: cancel via the download controller if present.)
- **Reading-list article HTML**: `ArticleStore.clearAll()` — delete the `reading_list` dir
  contents (new method).
- **Thumbnails**: `WebViewHolder.clearThumbnails()` — delete the `cacheDir/thumbnails` dir and
  drop the in-memory LRU (new method); driven from MainActivity alongside teardown.

## Settings reset

Reset browsing-trace preference keys: `active_orbit_id` (→ 0, re-resolves to the new default),
`lifetime_blocked`, `ad_allowed_sites`, `background_media_sites`. Leave device-UX flags
(onboarding done, seen-orbit-note, theme, search engine).

## Files

- `data/*Dao.kt` — add `clearAll()` where missing.
- `reading/ArticleStore.kt` — `clearAll()`.
- `ui/WebViewHolder.kt` — `clearThumbnails()`.
- `BrowserViewModel.kt` — `blackHoleReady` SharedFlow + `onBlackHole()`; capture keys, wipe DB,
  delete download files (via `ArticleStore`/`downloadDao`), reset settings, emit.
- `SettingsRepository.kt` — add setters/reset for the trace keys if missing.
- `MainActivity.kt` — collect `blackHoleReady`; teardown + process restart.
- `ui/SettingsScreen.kt` — Danger Zone row + confirm dialog.

## Out of scope

Gesture/shortcut trigger; selective wipe; secure file shredding (plain delete only).

## Testing

- VM: `onBlackHole` clears every DAO (assert each fake empty), resets `activeOrbitId`, and emits
  `blackHoleReady` carrying all orbit profile keys + `"incognito"`.
- Download-file + reading-file deletion invoked (fakes/temp dirs).
- Confirm the emitted key set includes the incognito profile and every orbit.
