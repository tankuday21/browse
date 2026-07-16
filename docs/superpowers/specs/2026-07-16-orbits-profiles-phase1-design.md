# Orbits (Profiles / Containers) — Phase 1 Design

**Status:** Approved (2026-07-16)
**Feature branch:** `feature/v4.2-orbits`
**Roadmap:** v4 "Fortress" signature feature (D7). Element Zapper (its pair) shipped in v4.0.

## Goal

Give Andromeda **Orbits** — user-created, named, colored browsing identities (Personal, Work, Shopping, …), each with its own logins/cookies/storage and its own set of open tabs. Signing into Work Gmail and Personal Gmail simultaneously, in one browser, with no cross-contamination — something effectively no mobile browser ships (Firefox containers are desktop-only).

## Scope — phased full isolation

Full Chrome-style isolation is the destination, delivered in phases so each ships polished and testable:

- **Phase 1 (THIS spec):** the Orbit model (create/name/color/delete), per-tab Orbit assignment, **cookie/login/web-storage isolation** via `ProfileStore`, isolated **tab sets**, the Orbit switcher UI, and an always-visible colored indicator. Phase 1 alone is a complete, useful feature: separate logins + separate tabs.
- **Phase 2 (later spec):** per-Orbit **history** isolation.
- **Phase 3 (later spec):** per-Orbit **bookmarks, home shortcuts, downloads** → full isolation.

**Explicitly out of scope for Phase 1:** history, bookmarks, downloads, and home-shortcut isolation. Those data sets stay **shared** across Orbits in Phase 1.

## Architecture (builds on existing foundations)

The app already isolates incognito via `androidx.webkit` `ProfileStore`:
`ProfileStore.getInstance().getOrCreateProfile(name)` + `WebViewCompat.setProfile(webView, name)`, gated on `WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)` (see `WebViewHolder.obtain`). Orbits generalize this from the single hardcoded `"incognito"` profile to **one profile per Orbit**.

`TabEntity` currently carries `isIncognito` but no Orbit association; Phase 1 adds `orbitId`.

### Data model

**New entity** `OrbitEntity` (`orbits` table):
| field | type | notes |
|-------|------|-------|
| `id` | `Long` PK autogen | |
| `name` | `String` | user-visible ("Personal", "Work") |
| `colorArgb` | `Int` | chosen from a curated palette; drives the ring/chip |
| `position` | `Int` | ordering in the switcher |
| `profileKey` | `String` | stable ProfileStore profile name; generated once at creation (e.g. `"orbit_" + System.currentTimeMillis() + "_" + count`), never reused |

**`TabEntity`** gains `orbitId: Long? = null` (nullable so migration + incognito tabs are clean; a null non-incognito tab resolves to the default Orbit).

**DataStore:** `activeOrbitId: Long` — which Orbit the user is currently in.

**Migration v13 → v14:**
1. `CREATE TABLE orbits (...)`.
2. `ALTER TABLE tabs ADD COLUMN orbitId INTEGER` (nullable).
3. Seed one default Orbit **"Personal"** (default color) with a fresh `profileKey`.
4. `UPDATE tabs SET orbitId = <personal.id> WHERE isIncognito = 0`.
5. `activeOrbitId` defaults to the Personal Orbit id (resolved at first read if unset).

Room schema exported as `14.json`.

### Login/cookie/storage isolation

`WebViewHolder.obtain(tabId, incognito, profileKey)` gains a `profileKey` argument. For a **non-incognito** tab, when `MULTI_PROFILE` is supported, set the WebView to that Orbit's profile:
`WebViewCompat.setProfile(webView, store.getOrCreateProfile(profileKey).name)`. Incognito continues to use the `"incognito"` profile.

The caller (VM/MainActivity) resolves `tab.orbitId → orbit.profileKey` before calling `obtain`.

**Feature fallback (`MULTI_PROFILE` unsupported):** Orbits still organize tabs (soft isolation), but cookies are shared. Surface a **one-time** informational note ("Update Android System WebView for full login separation between Orbits"). The feature stays usable; only true cookie isolation is unavailable. This mirrors the existing incognito graceful-degrade — never crash, never hide the feature.

### Tabs & switching

- Each non-incognito tab belongs to an Orbit (`orbitId`). The tab switcher shows **only the active Orbit's tabs** — the exact pattern Incognito already uses (separate screen). `normalTabs` becomes `tabs.filter { !it.isIncognito && it.orbitId == activeOrbitId }`.
- **Switching Orbit** sets `activeOrbitId` and makes the active tab that Orbit's most-recent tab; if the Orbit has no tabs, open a fresh home tab in it.
- **New tabs** inherit the active Orbit (incognito new-tabs stay incognito, no Orbit).
- **"Open link in another Orbit":** the existing link long-press context sheet gains an "Open in <Orbit>" action per other Orbit (opens the url as a new tab assigned to that Orbit, and switches to it). Small, high-delight.

### UI

- **Tab switcher top control:** the current Tabs/Incognito segmented pill becomes an **Orbit selector** — a horizontally scrollable row of **colored chips** (Orbit name + tab count), a **"+"** chip to create a new Orbit, and an **Incognito** chip at the end. The selected chip fills with the Orbit's `colorArgb`; unselected chips are tonal. Keeps the sliding/animated feel of the current pill.
- **Colored indicator:** a small ring/dot in the active Orbit's color on the command bar (web) and the home top bar. Tapping it opens a **quick-switch bottom sheet** listing Orbits (with color + name + tab count) plus a "Manage Orbits" entry.
- **Manage Orbits** (bottom sheet or small screen): **add** (name field via `OrbitTextField` + color from a curated palette of ~8 Orbit colors), **rename**, **delete**. Delete shows a confirm dialog; on confirm it closes that Orbit's tabs, deletes its ProfileStore profile (wiping its cookies/storage), and removes the row. **At least one Orbit always remains** — deleting the last Orbit is disallowed (the action is disabled/hidden when only one exists).
- All new UI uses the Orbit design tokens (`orbit()`, `OrbitSpacing`, `OrbitRadii`, orbit type styles) and the shared components (`OrbitTextField`, tonal surfaces, no hairline-bordered rectangles), consistent with the v4.1 "Chrome-smooth" language.

### Incognito relationship

Incognito stays a **separate, ephemeral mode** — *not* an Orbit. It remains its own chip in the switcher, always dark, in-memory, never persisted, `orbitId = null`. Orbits are persistent identities; incognito is a throwaway session. The two concepts do not merge.

## Components / files

- **Data:** `data/OrbitEntity.kt` (entity + `OrbitDao`), `data/OrbitRepository.kt`; `data/TabEntity.kt` (+`orbitId`); `data/BrowseDatabase.kt` (v14 + migration + dao); `data/SettingsRepository.kt` (+`activeOrbitId`); `BrowseApplication.kt` (repo wiring).
- **VM:** `BrowserViewModel.kt` — expose `orbits`, `activeOrbit`, tab filtering by Orbit, `onSwitchOrbit`, `onCreateOrbit`, `onRenameOrbit`, `onDeleteOrbit`, `onOpenLinkInOrbit`; resolve `profileKey` for `obtain`.
- **WebView:** `WebViewHolder.kt` — `obtain(..., profileKey)`, set per-Orbit profile; `MainActivity.kt` passes the resolved key.
- **UI:** `TabSwitcherScreen.kt` — Orbit selector; a new `ui/components/OrbitSwitcherChips.kt`, `ui/components/OrbitQuickSwitchSheet.kt`, `ui/components/ManageOrbitsSheet.kt`; `CommandBar.kt` + home top bar — colored Orbit indicator; context sheet in `BrowserScreen.kt` — "Open in <Orbit>".

## Testing

- **Unit:** `OrbitDao`/`OrbitRepository` CRUD; tab filtering by active Orbit; `activeOrbitId` persistence + default resolution; "last Orbit cannot be deleted"; `profileKey` generated stable + unique; new-tab inherits active Orbit.
- **Instrumented:** migration v13→v14 (orbits table created, Personal seeded, existing tabs assigned, incognito tabs untouched).
- Follow the project **build-verify ritual** (clean build + dex-verify + md5-match before claiming installed).

## Security / privacy constraints (must hold)

- Incognito never persisted, never assigned an Orbit, never writes to any DAO — unchanged.
- Deleting an Orbit must actually wipe its ProfileStore data (cookies/storage), not just the row.
- No profileKey reuse (a deleted Orbit's key is never handed to a new Orbit — avoids inheriting stale cookies).
- Keystore/signing files remain gitignored — never committed.

## Documented limitations (Phase 1)

- History, bookmarks, downloads, home shortcuts are **shared** across Orbits (Phases 2–3).
- True cookie isolation requires a `MULTI_PROFILE`-capable WebView; older WebViews get soft (tab-only) separation with a one-time note.
