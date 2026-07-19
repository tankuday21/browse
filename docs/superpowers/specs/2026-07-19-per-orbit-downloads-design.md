# Per-Orbit Downloads (v5.5)

**Goal:** The last table missing from the Orbits isolation invariant. History, bookmarks,
shortcuts, and credentials are all Orbit-scoped (v4.3–v4.7); downloads still show one global
list in every Orbit. v5.5 scopes them: the Downloads screen and the menu badge show the active
Orbit's downloads, and deleting an Orbit purges its download rows AND files.

## Data (schema v18 → v19)

- `DownloadEntry` gains `orbitId: Long? = null` (nullable INTEGER, exactly like history v4.3).
- `MIGRATION_18_19`: `ALTER TABLE downloads ADD COLUMN orbitId INTEGER` + backfill every
  existing row to the first Orbit (`orbits ORDER BY position ASC, id ASC LIMIT 1`) — same
  recipe as MIGRATION_15_16/16_17.
- `DownloadDao` additions: `observeForOrbit(orbitId)` (same ordering as observeAll),
  `getAllForOrbit(orbitId)`, `deleteForOrbit(orbitId)`. `observeAll`/`getActive`/`clearAll`
  stay (Black Hole and the engine need the global view).

## VM

- `downloads` StateFlow becomes `activeOrbitId.flatMapLatest { downloadDao.observeForOrbit(it) }`
  (the flatMapLatest pattern every other scoped flow uses). The menu's active-download badge
  derives from this flow, so it becomes per-Orbit automatically — desired: it should reflect
  what the Downloads screen will show.
- Both insert paths tag `orbitId = activeOrbitId.value`: `onStartDownload` (engine) and
  `onDownloadStarted` (legacy system-DM path).
- **Incognito decision (documented):** downloads stay persisted and are tagged with the ACTIVE
  Orbit even when initiated from an incognito tab — a download is an explicit user action
  producing a durable file on disk; Chrome's incognito behaves the same (downloads appear in
  the regular list). The `onDownloadRequested` listener carries no tab identity, and inventing
  incognito-scoped download rows would be privacy theater while the file sits in Downloads/.
- `onDeleteOrbit` purge order (files before rows — their paths live in the rows):
  1. cancel any RUNNING/PENDING/SCHEDULED/PAUSED download in the Orbit (PAUSED included —
     its notification must not outlive the Orbit);
  2. `getAllForOrbit(id)` → delete each `filePath` from disk; legacy system-DM rows
     (`filePath` null, `downloadId` > 0) go through `downloadManagerRemover` instead —
     the file is DownloadManager's and removal-by-id is the only cleanup path (review);
  3. `downloadDao.deleteForOrbit(id)`.
- Black Hole unchanged (already cancels all + deletes all files + `clearAll`).
- **Known residual race (documented, deferred):** a cancel arriving between a download's row
  insert and the service actually starting the transfer is a no-op in the engine; the transfer
  can then complete into an orphan file with no row. Same shape pre-exists in
  `onDeleteDownload`; the proper fix (re-check the row exists in `DownloadService`'s start
  path) is a service-side change deferred to a housekeeping pass.

## UI

`DownloadsScreen`: `OrbitScopeHeader(activeOrbit, scope = "downloads")` under the OrbitTopBar,
same as History/Bookmarks/Passwords; Orbit-aware empty state.

## Testing

- Unit (JVM): a started download row carries the active Orbit's id (engine path + legacy path);
  the `downloads` flow only surfaces the active Orbit's rows and switches with the Orbit;
  `onDeleteOrbit` purges that Orbit's rows and deletes their real temp files (JVM File),
  leaving other Orbits' rows/files intact.
- androidTest (compiles; run on device later): MIGRATION_18_19 creates the column and
  backfills existing rows to the first Orbit.
- On-device: download in Orbit A → visible in A, absent in B; badge counts only the active
  Orbit's active downloads; delete Orbit A → its files are gone from disk.
