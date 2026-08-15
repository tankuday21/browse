# Architecture

> **Last verified against:** v6.16 (`versionCode 44`), 2026-08-14.
> If you change a layer boundary, an invariant, or add a package, update this file in the same commit.

## At a glance

| | |
|---|---|
| Platform | Android, `minSdk 26`, `targetSdk 36`, `compileSdk 36` |
| Language / UI | Kotlin · Jetpack Compose (Material 3) |
| Pattern | MVVM, one ViewModel exposing a single observable UI state |
| Rendering | Android **System WebView** (see [ADR-0001](adr/0001-system-webview.md)) |
| Persistence | Room (schema **v22**, 16 entities, 15 DAOs) + DataStore Preferences |
| Modules | Single Gradle module (`:app`) |
| Source | 166 Kotlin files, ~26,800 LOC |
| Tests | 82 JVM test files, 4 instrumented |
| ABI | `arm64-v8a` only (see [ADR-0010](adr/0010-arm64-only-apk.md)) |

## Layer model

Dependencies point **downward only**. Nothing in `browser/` may import Compose or Android UI types.

```
┌───────────────────────────────────────────────────────────────┐
│ UI                ui/ · ui/components/ · ui/theme/            │
│                   Compose screens; stateless, driven by state  │
├───────────────────────────────────────────────────────────────┤
│ Presentation      BrowserViewModel  (single source of truth)   │
│                   StateFlows in, intent functions out          │
├───────────────────────────────────────────────────────────────┤
│ Platform bridge   ui/WebViewHolder   MainActivity              │
│                   live WebViews, permissions, PiP, sensors     │
├───────────────────────────────────────────────────────────────┤
│ Domain (pure)     browser/ · browser/adblock/ · translate/     │
│                   media/ · download/ · reading/                │
│                   plain Kotlin objects — JVM-unit-testable     │
├───────────────────────────────────────────────────────────────┤
│ Services          DownloadService · AndromedaPlayerService     │
│                   MediaHoldService · ReadAloudService          │
├───────────────────────────────────────────────────────────────┤
│ Data              data/ — Room DAOs, entities, migrations,     │
│                   SettingsRepository (DataStore), assets/      │
└───────────────────────────────────────────────────────────────┘
```

## Package map

Counts are `.kt` files under `app/src/main/java/com/udaytank/browse/`.

| Package | Files | Responsibility |
|---|---:|---|
| `browser/` | 43 | Domain logic: `TabManager`, `ReaderMode`, `SiteSettingsResolver`, `CredentialHostMatch`, `BookmarkSearch`, `ClearDataRange`, `ShakeDetector`, URL/host helpers. **Pure — no Android UI.** |
| `data/` | 35 | Room entities + DAOs, `BrowseDatabase` (migrations), `SettingsRepository`, backup/restore |
| `ui/components/` | 21 | Reusable Compose pieces (sheets, bars, chips, dialogs) |
| `ui/` | 20 | Screens: `BrowserScreen`, `TabSwitcherScreen`, `SettingsScreen`, `DownloadsScreen`, `HomePage`, `PlayerScreen`, plus `WebViewHolder` |
| `browser/adblock/` | 10 | From-scratch Adblock-Plus engine: parser, token index, cosmetic filtering, scriptlets |
| `media/` | 6 | `AndromedaPlayerService`, `MediaHoldService`, `SleepTimer`, queue/progress policies |
| `translate/` | 5 | On-device translation: `TranslateManager`, `TranslateScripts`, `TranslatePayload`, `TranslateLang` |
| `download/` | 5 | `DownloadService`, download engine, planner |
| `browser/feed/`, `data/feed/`, `feed/` | 9 | Home dashboard: RSS + weather sources and parsing |
| `ui/theme/` | 4 | Orbit design system: color, type, shape |
| `reading/` | 2 | Reading list + `ReadAloudService` (TTS) |
| `browser/zap/` | 2 | Element Zapper (per-site element hiding) |
| `ui/game/` | 1 | Offline-page Asteroid game |
| root | 3 | `MainActivity`, `BrowserViewModel`, `BrowseApplication` |

## Core invariants

These are load-bearing. Breaking one is a bug even if tests pass.

**1. Orbit-scoped tables carry `orbitId` and are always queried through it.**
Orbits are isolated browsing profiles. Seven tables are scoped — `tabs`, `history`, `bookmarks`,
`home_shortcuts`, `credentials`, `downloads`, `closed_tabs` (v6.16) — and every query filters on the
active Orbit. The rest are intentionally device-global (`site_settings`, `favicons`, `feed_items`,
`rss_sources`, `zapped_elements`, `tab_groups`, `player_progress`) **except one known gap**
(`reading_list`) — see [DATA-MODEL.md](DATA-MODEL.md#orbit-scoping-status) and
[ROADMAP.md](ROADMAP.md). Do not assume a table is scoped; check the entity.

**2. Incognito never touches disk.**
Incognito tabs get **negative ids** and `orbitId = null`; `TabManager` never persists negative ids, and `persistRegisteredTab` early-returns on them. No history, no credentials, no favicons, no player progress. See [SECURITY-PRIVACY.md](SECURITY-PRIVACY.md).

**3. Domain logic is pure and JVM-testable.**
Anything with a decision in it (matching, parsing, ranking, cutoffs, policies) lives in `browser/` etc. as a plain object/enum with no Android dependency, and has a unit test. UI and Services stay thin. See [ADR-0005](adr/0005-pure-testable-cores.md) and [TESTING.md](TESTING.md).

**4. One ViewModel, one state surface.**
`BrowserViewModel` (2,568 lines) owns all app state as `StateFlow`s and exposes intent functions (`onX(...)`). Screens are stateless and never talk to DAOs. See [ADR-0002](adr/0002-single-viewmodel-stateflow.md).

**5. Live WebViews live outside the Compose tree.**
`WebViewHolder` (1,229 lines) owns one real `WebView` per tab so page state survives recomposition and tab switches. Compose hosts them via `AndroidView`; the holder is the only place WebView settings are applied.

**6. Per-Orbit WebView isolation via `ProfileStore`.**
Each `OrbitEntity` has a stable `profileKey`; `androidx.webkit.ProfileStore` gives each Orbit its own cookie/storage partition. Profile deletion only succeeds after every WebView bound to it is destroyed — hence the deferred-delete handshake in `BrowserViewModel`/`MainActivity`.

## Services

All foreground, all declared in `AndroidManifest.xml`.

| Service | Type | Purpose |
|---|---|---|
| `download/DownloadService` | `dataSync` | Own download engine: segments, pause/resume/retry, survives process death |
| `media/AndromedaPlayerService` | `mediaPlayback` | `MediaSessionService` owning the single ExoPlayer ([ADR-0008](adr/0008-media3-hybrid-player.md)) |
| `media/MediaHoldService` | `mediaPlayback` | Keeps opted-in web media playing when locked |
| `reading/ReadAloudService` | `mediaPlayback` | TTS read-aloud / podcast mode over the reading list |

## Data flow: a page load

```
user types URL
  → BrowserScreen calls viewModel.onNavigate(url)
      → BrowserViewModel resolves search vs URL, updates tab StateFlow
          → WebViewHolder.loadUrl on that tab's live WebView
              → shouldInterceptRequest → AdBlockEngine (token index) allow/block
              → onPageStarted  → applySiteSettings (SiteSettingsResolver)
                               → inject cosmetic filters + zapped elements
                               → resetTranslateState for the active tab
              → onPageFinished → title/favicon → HistoryDao.insert (skipped if incognito)
                               → BrowserViewModel updates state → UI recomposes
```

## Where complexity concentrates

The largest files, and why. Treat these as refactor candidates, not as a pattern to copy.

| File | LOC | Note |
|---|---:|---|
| `BrowserViewModel.kt` | 2,568 | Deliberate single-source-of-truth; the cost of [ADR-0002](adr/0002-single-viewmodel-stateflow.md) |
| `ui/TabSwitcherScreen.kt` | 1,403 | Grid + list modes, groups, selection mode, context menus |
| `ui/WebViewHolder.kt` | 1,229 | Every WebView callback and setting funnels here by design |
| `MainActivity.kt` | 1,193 | Permissions, PiP, sensors, file chooser, Orbit-profile handshake |
| `ui/SettingsScreen.kt` | 1,184 | Grows with every feature toggle |
| `ui/BrowserScreen.kt` | 1,147 | Toolbar, sheets, overlays, find bar |

## Related documents

- [DATA-MODEL.md](DATA-MODEL.md) — entities, `orbitId`, migration chain v1→v21
- [SECURITY-PRIVACY.md](SECURITY-PRIVACY.md) — threat model and privacy invariants
- [TESTING.md](TESTING.md) — how these layers are tested
- [adr/](adr/) — why the architecture is this way, and what was rejected
- [GOTCHAS.md](GOTCHAS.md) — build/environment traps
