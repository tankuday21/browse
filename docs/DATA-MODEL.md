# Data model

> **Last verified against:** schema **v21**, v6.15, 2026-08-14.
> When you add an entity, column, or migration: update the table below **and** export the new
> schema JSON in the same commit.

## Stores

| Store | Used for | Location |
|---|---|---|
| **Room** (`BrowseDatabase`, v21) | Structured user data: tabs, history, bookmarks, downloads, credentials, … | `data/` |
| **DataStore Preferences** (`SettingsRepository`) | Scalar settings and small string-sets (toggles, reader prefs, `neverSaveSites`) | `data/SettingsRepository.kt` |
| **Files** (`ArticleStore`) | Offline cleaned article bodies for the reading list | app-private storage |
| **Bundled assets** | Ad-block filter lists (~5.1 MB of text), language-id model | `app/src/main/assets/` |

Schemas are exported to `app/schemas/com.udaytank.browse.data.BrowseDatabase/` via
`ksp { arg("room.schemaLocation", ...) }`. **Versions 5–21 are exported**; v1–v4 predate the export
setting, so `MigrationTestHelper` tests can only start from v5.

## Entities

16 entities, 15 DAOs. `Orbit?` = the column exists and is nullable (`null` = incognito or
pre-migration/unassigned).

| Table | Entity | Orbit-scoped | Notes |
|---|---|:---:|---|
| `tabs` | `TabEntity` | **yes** | Persisted tabs. Incognito tabs are **never** rows here (negative ids). |
| `history` | `HistoryEntry` | **yes** | `visitedAt` drives time-range clearing ([ClearDataRange](../app/src/main/java/com/udaytank/browse/browser/ClearDataRange.kt)) |
| `bookmarks` | `Bookmark` | **yes** | Unique `(url, orbitId)`; `folder` is a free-text label, not a first-class object |
| `home_shortcuts` | `HomeShortcutEntity` | **yes** | Editable home quick-dial grid |
| `credentials` | `CredentialEntity` | **yes** | Encrypted passwords; unique `(orbitId, host, username)` |
| `downloads` | `DownloadEntry` | **yes** | Full engine state: segments, etag, attempts, resumable |
| `orbits` | `OrbitEntity` | n/a | The profiles themselves; holds the `ProfileStore` `profileKey` |
| `site_settings` | `SiteSettingsEntity` | no *(by design)* | Per-host tri-state overrides (JS, cookies, force-dark, desktop, `blockImages`) — treated as device-level prefs |
| `zapped_elements` | `ZappedElementEntity` | no *(by design)* | Per-host hidden-element selectors, keyed by host |
| `favicons` | `FaviconEntity` | no *(by design)* | Icon cache, keyed by host |
| `feed_items` / `rss_sources` | `FeedItemEntity` / `RssSourceEntity` | no *(by design)* | Home dashboard news; content is public by nature |
| `tab_groups` | `TabGroupEntity` | no *(by design)* | Group metadata; membership lives on the scoped `tabs` row |
| `player_progress` | `PlayerProgressEntity` | no *(by design)* | Resume position keyed by `filePath`; purged whenever the download row is deleted |
| `closed_tabs` | `ClosedTabEntity` | **no — gap** | See below |
| `reading_list` | `ReadingListEntry` | **no — gap** | See below |

## Orbit scoping status

Orbits promise isolated browsing profiles. That promise is **fully kept for tabs, history,
bookmarks, shortcuts, credentials and downloads**, and **not kept** for two tables:

### `closed_tabs` — cross-Orbit visibility (open issue)

`ClosedTabEntity` has no `orbitId`, and the DAO reads unfiltered:

```kotlin
@Query("SELECT * FROM closed_tabs ORDER BY closedAt DESC, id DESC LIMIT :limit")
```

Consequence: a tab closed in Orbit *Work* appears in *Personal*'s "Recently closed" list, and
`onReopenClosed` reopens it into **whichever Orbit is active**
([BrowserViewModel.kt:1314](../app/src/main/java/com/udaytank/browse/BrowserViewModel.kt#L1314)).

**Incognito is not affected** — `TabManager.closeTab` gates the insert on `!isIncognitoId(id)`
([TabManager.kt:163](../app/src/main/java/com/udaytank/browse/browser/TabManager.kt#L163)), so
incognito tabs are never recorded. This is a *normal-browsing cross-profile leak*, not an incognito
leak. Tracked in [ROADMAP.md](ROADMAP.md).

### `reading_list` — shared across Orbits

Saved articles and their offline bodies are global. Defensible (a reading list is arguably personal
to the device, not the profile), but it is an **undocumented** asymmetry with history/bookmarks, so
it is recorded here as a decision to make rather than a fact to rely on.

## Migration chain

Room migrations are **explicit and additive**; there is no `fallbackToDestructiveMigration`. Every
step is in `BrowseDatabase.kt`'s companion object.

| Step | Change |
|---|---|
| 1 → 2 | create `tabs` |
| 2 → 3 | `tabs.isIncognito` |
| 3 → 4 | create `downloads` |
| 4 → 5 | `bookmarks.folder` (unused until v6.10 shipped folder UI) |
| 5 → 6 | `tabs.groupId/pinned/locked`; create `tab_groups`, `closed_tabs` |
| 6 → 7 | `downloads` gains the engine columns: `totalBytes`, `downloadedBytes`, `state`, `filePath`, `mimeType`, `etag`, `segments`, `segmentState`, `error`, `attempts` |
| 7 → 8 | create `reading_list`, `site_settings` |
| 8 → 9 | create `home_shortcuts` |
| 9 → 10 | create `feed_items`, `rss_sources` |
| 10 → 11 | create `zapped_elements` (+ host index) |
| 11 → 12 | `feed_items.description` |
| 12 → 13 | create `favicons` |
| **13 → 14** | **create `orbits`; `tabs.orbitId`** — Orbits introduced |
| 14 → 15 | `orbits.iconKey` |
| 15 → 16 | `history.orbitId` |
| 16 → 17 | `bookmarks.orbitId` + unique `(url, orbitId)` replacing unique `url`; `home_shortcuts.orbitId` |
| 17 → 18 | create `credentials` + unique `(orbitId, host, username)` |
| 18 → 19 | `downloads.orbitId` |
| 19 → 20 | create `player_progress` |
| 20 → 21 | `site_settings.blockImages` |

### Migration conventions

- **Backfill, don't orphan.** When adding `orbitId` to an existing table, existing rows are assigned
  to the first Orbit (`UPDATE <table> SET orbitId = $firstOrbit`) so nothing silently disappears.
- **Replace unique indexes deliberately.** Adding `orbitId` to a table with a unique `url` means
  dropping that index and creating a composite one — otherwise the same URL can't exist in two
  Orbits (this is exactly what 16 → 17 does for `bookmarks`).
- **Export the schema JSON** and add a `MigrationTestHelper` case in `app/src/androidTest/`.
  Instrumented tests need a device/emulator, so they are frequently *compiled but not run* — say so
  rather than implying they passed. See [TESTING.md](TESTING.md).

## Related documents

- [ARCHITECTURE.md](ARCHITECTURE.md) — where the data layer sits
- [SECURITY-PRIVACY.md](SECURITY-PRIVACY.md) — incognito invariants, credential encryption, Black Hole wipe
- [ROADMAP.md](ROADMAP.md) — the `closed_tabs` / `reading_list` scoping items
