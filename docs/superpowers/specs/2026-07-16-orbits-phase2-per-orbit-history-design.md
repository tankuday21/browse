# Orbits Phase 2 — Per-Orbit History (v4.3)

**Goal:** Close the last major isolation leak in Orbits. Today tabs + cookies + WebView
storage are per-Orbit, but **browsing history is global** — your Work visits show up in
Personal's history, quick dials, and address-bar suggestions. Phase 2 scopes history to
its owning Orbit so the "containers" promise holds end to end.

## Isolation contract (the invariants this feature must uphold)

1. A visit is recorded against the **owning tab's `orbitId`** (never the global active id, to
   be correct for background/late `onPageFinished`). Incognito still records nothing.
2. Every history **read** is scoped to the active Orbit:
   - History screen list → active Orbit only.
   - Home quick dials (`topVisited`) → active Orbit only.
   - Address-bar suggestions (`SuggestionEngine.search`) → active Orbit only.
   - De-dup "same page reload" (`mostRecent`) → per Orbit, so a reload in Work never
     collapses onto a Personal row.
3. Deleting an Orbit **purges its history rows** (alongside the existing cookie + tab purge).
   This is a hard isolation requirement, mirrors deleteProfile, and must be irreversible.
4. Clearing history from the (Orbit-scoped) History screen clears **only the active Orbit**.
   The global "Clear all history" in Settings still wipes everything (explicit, all-Orbits).

## Data model

`history` gains `orbitId INTEGER` (nullable, to match the `tabs` column style and survive
migration). Every non-incognito write sets it; reads filter on it.

**Migration v15 → v16:** add the column, then backfill existing rows to the first Orbit
(`SELECT id FROM orbits ORDER BY position ASC, id ASC LIMIT 1`) — the seeded "Personal"
Orbit — so no history is orphaned. New DB version = 16; export schema; add instrumented
migration test row.

## DAO changes (`HistoryDao`)

- `observeForOrbit(orbitId)` replaces the History-screen use of `observeAll()`.
- `search(orbitId, query, limit)` — Orbit-scoped suggestions.
- `topVisited(orbitId, limit)` — Orbit-scoped quick dials.
- `mostRecent(orbitId)` — Orbit-scoped de-dup.
- `insert` takes a `HistoryEntry` whose `orbitId` is set by the caller.
- `clearForOrbit(orbitId)` — History-screen clear.
- `deleteForOrbit(orbitId)` — called on Orbit deletion.
- Keep `clearAll()` for Settings' global clear; keep `deleteById`, `updateTitleForUrl`
  (title is a URL property, cross-Orbit update is harmless and keeps titles fresh).

## ViewModel wiring

- `onPageFinished`: resolve the tab's `orbitId`; skip if incognito (unchanged); pass the
  orbitId into the `VisitPolicy` decision (`mostRecent(orbitId)`) and the insert.
- `historyEntries` StateFlow: `flatMapLatest` on `activeOrbitId` → `observeForOrbit(id)`.
- `onHomeShown`: `topVisited(activeOrbitId, 60)`.
- `suggest`: `SuggestionEngine.suggest(query, activeOrbitId)`.
- `onClearHistory` (from History screen) → `clearForOrbit(activeOrbitId)`.
- `onDeleteOrbit`: add `historyDao.deleteForOrbit(id)` before/with the tab close-out.

`SuggestionEngine.suggest` gains an `orbitId` parameter, threaded to `historyDao.search`.

## UI (ui-ux-pro-max) — History screen

The list is now implicitly the active Orbit's. Make the scope legible, not silent:

- A slim **scope header** at the top: the active Orbit's `OrbitAvatar` (reuse the shared
  component) + name + "history", so the user always knows whose history they're viewing.
  Tinted hairline under it in the Orbit color (matches the tab-switcher accent line).
- The "Clear" action reads **"Clear <Orbit> history"** and confirms before wiping (reuse
  the existing confirm pattern; destructive-emphasis danger color; `aria`/contentDescription).
- Empty state: friendly "No history in this Orbit yet" with the avatar, not a bare list.
- Respect existing motion tokens; no layout shift; 44dp touch targets.

## Out of scope (Phase 3+)

Per-Orbit bookmarks / downloads / shortcuts; a cross-Orbit "view another Orbit's history"
picker (isolation-by-default is the intended mental model for now).

## Testing

- `HistoryDao` Orbit-scoping (Robolectric/in-memory): writes land under the right Orbit;
  reads exclude other Orbits; `deleteForOrbit` / `clearForOrbit` scope correctly.
- VM: visit under active Orbit A is invisible after switching to B; deleting an Orbit
  removes its history; incognito still records nothing.
- `VisitPolicy` unchanged in logic but now fed per-Orbit `mostRecent` — a reload in A and a
  first visit to the same URL in B both behave correctly (bump in A, new row in B).
- Migration v15→v16 instrumented test: pre-seed rows, assert backfill to first Orbit.
