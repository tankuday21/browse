# Orbits Phase 3 — Per-Orbit Bookmarks + Home Shortcuts (v4.4)

**Goal:** Finish the container isolation story. After v4.3 (history), the remaining shared
"saved places" are **bookmarks** and **home-screen shortcut tiles**. Phase 3 scopes both to
their owning Orbit so each container has its own saved web, matching the tabs/cookies/history
isolation already in place.

## Isolation contract

1. A bookmark / shortcut is created against the **active Orbit** and is visible only there.
2. Every read is Orbit-scoped: the Bookmarks screen, the star/bookmarked-state of the current
   page, address-bar **bookmark** suggestions, and the home shortcut grid.
3. Deleting an Orbit purges its bookmarks + shortcuts (alongside tabs/cookies/history).
4. The same URL may be bookmarked independently in two Orbits (Work's and Personal's stars are
   separate). This requires the bookmarks unique index to move from `url` to `(url, orbitId)`.
5. New Orbits start with an **empty** shortcut grid (user-curated); legacy bookmarks/shortcuts
   backfill to the first (Personal) Orbit.
6. Backup **restore** imports into the **active Orbit** at restore time (orbit ids are
   device-local and can't be assumed to match across installs). Backup export is unchanged
   (whole-DB); this is a conscious, documented simplification.

## Data model + migration (v16 → v17, DB version 17)

- `bookmarks`: add `orbitId INTEGER`. Drop `index_bookmarks_url`; create UNIQUE
  `index_bookmarks_url_orbitId` on `(url, orbitId)`. Backfill `orbitId` to the first Orbit
  (`SELECT id FROM orbits ORDER BY position ASC, id ASC LIMIT 1`).
- `home_shortcuts`: add `orbitId INTEGER`; same backfill.
- `Bookmark` entity: `indices = [Index(value = ["url","orbitId"], unique = true)]`, add
  `orbitId: Long? = null`. `HomeShortcutEntity`: add `orbitId: Long? = null`.
- Export schema 17.json; add instrumented migration test.

## DAO changes

**BookmarkDao** — every query gains an `orbitId` filter:
- `observeForOrbit(orbitId)`, `getAllForOrbit(orbitId)` (backup export stays `getAll()` = all).
- `observeIsBookmarked(orbitId, url)`.
- `search(orbitId, query, limit)` (Orbit-scoped bookmark suggestions).
- `setFolder(orbitId, url, folder)`, `deleteByUrl(orbitId, url)`.
- `deleteForOrbit(orbitId)` (Orbit deletion purge).
- Keep `getAll()` (unscoped, for backup export) and `insert` (caller sets `orbitId`).

**HomeShortcutDao**:
- `observeForOrbit(orbitId)`, `getAllForOrbit(orbitId)`.
- `deleteForOrbit(orbitId)`; `replaceAllForOrbit(orbitId, list)` (Transaction: delete this
  Orbit's rows, insert the reindexed list) — reorder must not touch other Orbits.
- Keep `getAll()` (backup export) and `insert`.

## ViewModel wiring

- `bookmarks`, `homeShortcuts` StateFlows: `flatMapLatest` on `activeOrbitId`.
- `isBookmarked`: combine `currentUrl` + `activeOrbitId` → `observeIsBookmarked(orbitId, url)`.
- `onToggleBookmark`, `onAddShortcut`, `onRemoveShortcut`, `onMoveShortcutToFront`,
  `onDeleteBookmark`: use `activeOrbitId.value`; shortcut dedupe/reorder scoped to the Orbit.
- `SuggestionEngine.suggest(query, orbitId)`: bookmark search now also passes `orbitId`.
- Backup/restore: `getAll()` for export; restore sets `orbitId = activeOrbitId.value` on every
  imported bookmark/shortcut; dedupe against the active Orbit's existing rows only.
- `onDeleteOrbit`: add `bookmarkDao.deleteForOrbit(id)` + `homeShortcutDao.deleteForOrbit(id)`.

## UI (ui-ux-pro-max)

- **Bookmarks screen**: same Orbit **scope header** as History (avatar + "<name> · bookmarks"
  + Orbit-tinted hairline) and an Orbit-aware empty state ("No bookmarks in <name> yet").
- **Home shortcuts**: already read `homeShortcuts` → automatically per-Orbit; the "Add to home"
  and remove flows are unchanged besides scoping. No new chrome needed on home (the Orbit
  identity already shows in the top bar).

## Out of scope

Per-Orbit downloads (files are shared storage; the list view isolation is low value and
deferred). Cross-Orbit bookmark move/copy. Orbit-preserving backup format.

## Testing

- BookmarkDao / HomeShortcutDao Orbit-scoping + `deleteForOrbit` + composite-unique-index
  (same url bookmarkable in two Orbits) — instrumented.
- Migration v16→v17: pre-seed bookmarks/shortcuts, assert backfill + new index name.
- VM: bookmark/shortcut created in A invisible in B; delete-Orbit purges both; restore lands
  in the active Orbit.
- SuggestionEngine: bookmark from another Orbit never suggested.
