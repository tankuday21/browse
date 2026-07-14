# Living Home & Full Incognito (v3.2 "Horizon") — Implementation Plan

> **For agentic workers:** execute task-by-task; each task ends with a compiling, testable
> deliverable. Steps use checkbox syntax. Build after every task with
> `JAVA_HOME="C:\Program Files\Android\Android Studio\jbr" ./gradlew.bat assembleDebug -q`.

**Goal:** A living, privacy-respecting home dashboard (cosmic backdrop + quick dials + RSS
news/sports + Open-Meteo weather) and a fully separate dark incognito mode, on a refreshed
premium type/color system.

**Architecture:** New `feed` data package (Room-cached RSS + weather), a rebuilt `HomePage`
scroll surface, an app-root theme switch for incognito, and a type/color refresh in the theme
layer. MVVM: repositories expose Flows; the ViewModel merges them into home state.

**Tech:** Kotlin · Compose M3 · Room (schema v10, exported + migration) · DataStore · WorkManager ·
`HttpURLConnection` + `XmlPullParser` (RSS) · Open-Meteo JSON · FusedLocation (coarse).

## Global Constraints (every task inherits)

- **Privacy:** feed/weather/quick-dials fetch **source-direct over HTTPS**, no third-party
  profiler, no keys, no analytics. Hard-gate all of it on `!isIncognito` AND the master feed
  toggle. Never fetch or render feed/dials/weather/stats in incognito. No DAO writes for
  negative/incognito tab ids.
- **Offline-first:** home renders instantly from cache; network refresh is background, throttled,
  failure-tolerant (a dead source is skipped, never fatal).
- **Orbit tokens only** (`OrbitSpacing/OrbitRadii/OrbitText/orbit()`); shared components.
- **No new secrets in VCS**; `keystore.properties`/`*.jks` stay gitignored.
- **Fixed WebView inset** — never resize the WebView on scroll (v3.1.2 lesson).

## File structure

- `res/font/` — `space_grotesk_medium.ttf`, `space_grotesk_bold.ttf`, `dm_sans_regular.ttf`,
  `dm_sans_medium.ttf` (+ `res/font/space_grotesk.xml`, `dm_sans.xml` families)
- `res/drawable-nodpi/home_backdrop.webp` — cosmic hero backdrop
- `ui/theme/Type.kt`, `Color.kt`, `Orbit.kt`, `Theme.kt` — type roles + refined palette + always-dark home scheme
- `browser/feed/` — `RssParser.kt`, `FeedModels.kt`, `FeedSources.kt` (presets), `QuickDialPolicy.kt`, `WeatherModels.kt` (all pure, unit-tested)
- `data/feed/` — `FeedDao.kt`, `FeedItemEntity.kt`, `RssSourceEntity.kt`, Room wiring + migration
- `data/FeedRepository.kt`, `data/WeatherRepository.kt`
- `feed/FeedRefreshWorker.kt` — WorkManager
- `ui/HomePage.kt` — rebuilt scroll surface; `ui/components/FeedCards.kt`
- `SettingsRepository.kt`, `ui/SettingsScreen.kt` — Home/feed prefs
- `MainActivity.kt` / app root — incognito theme switch + coarse-location permission

---

### Task 1 — Type, color & font refresh

**Files:** add 4 ttf to `res/font` + 2 family xml; modify `Type.kt`, `Color.kt`, `Orbit.kt`.

- Bundle Space Grotesk (Medium 500, Bold 700) + DM Sans (Regular 400, Medium 500) into `res/font`.
- `Type.kt`: define `val Display = FontFamily(Font(R.font.space_grotesk_medium, W500), Font(R.font.space_grotesk_bold, W700))` and `val Body = FontFamily(dm_sans_regular W400, dm_sans_medium W500)`. Wire Orbit text styles: `orbitDisplay`/`orbitTitle` → Display; `orbitBody`/`orbitCaption` → Body. Keep sizes; set the M3 `Typography` to use these.
- `Color.kt`: brighter primary text `#F2F3FF`; add `OrbitAccentViolet = #7A5CFF`; keep blue `#1E4FD8`/cyan `#35C3F3` (or brighten blue → `#2C5BE6`). Surfaces refined (`base #070716`, `surface #111228`, `elevated #1A1B3C`) — keep contrast ≥4.5:1.
- `Orbit.kt`: accent gradient becomes 3-stop `[blue, violet, cyan]`; expose `OrbitAccent.gradient` accordingly. `orbit()` unchanged API.
- **Test:** `OrbitTokensTest` still green; add an assertion that Display≠Body family and gradient has 3 stops.
- **Verify:** `assembleDebug` + `compileDebugUnitTestKotlin`. **Commit.**

### Task 2 — Cosmic always-dark home shell

**Files:** add `home_backdrop.webp`; modify `HomePage.kt`, `Theme.kt` (expose `darkOrbit` provider helper), `BrowserScreen.kt` (home always-dark).

- Convert `Andromeda_Homescreen.jpeg` → `home_backdrop.webp` (~1080px).
- Home renders under an always-dark scheme: wrap `HomePage` in `CompositionLocalProvider(LocalOrbit provides darkOrbit)` + matching M3 dark colors, regardless of app theme.
- `HomePage`: `LazyColumn` scroll surface. Item 0 = backdrop hero: `Box` with the webp drawn (`Image`, `ContentScale.Crop`, top-aligned) at alpha ~0.4, overlaid by `verticalGradient(transparent→base)`; the Space-Grotesk gradient wordmark + greeting sit on top. Keep existing shortcut row + Add. Fixed bottom inset = `OmniBarReservedHeight`.
- **Verify:** build; home shows backdrop + wordmark, still scrolls, focused mode (feed off) intact. **Commit.**

### Task 3 — Full incognito dark mode

**Files:** `BrowserScreen.kt` (root theme switch), `ui/HomePage.kt` (incognito branch already bare), `ui/components/OmniBar.kt`/`CommandBar.kt` (incognito indicator).

- At the app content root, when the **active tab is incognito**, provide `darkOrbit` + M3 dark scheme for the whole tree (toolbar, menus, home). Drive purely off `activeTab.isIncognito` — no persistence.
- Incognito home stays the bare explainer (no feed/dials/stats — already gated).
- OmniBar shows a persistent mask indicator while incognito.
- **Verify:** build; toggling to an incognito tab flips whole UI dark; normal tab restores theme. **Commit.**

### Task 4 — Quick dials (most-visited)

**Files:** `browser/feed/QuickDialPolicy.kt` (pure), `HistoryDao` (add top-hosts query), `BrowserViewModel` (expose `quickDials` Flow, gated non-incognito), `HomePage.kt` (tiles row).

- `QuickDialPolicy.rank(history): List<Dial>` — top hosts by visit count, de-duped against manual shortcuts, capped 8. Pure + unit-tested.
- Tiles reuse the shortcut tile look; tap opens URL.
- **Test:** ranking/de-dup/cap. **Verify + Commit.**

### Task 5 — Feed data layer (Room + RSS parser + repository)

**Files:** `browser/feed/{FeedModels,RssParser,FeedSources}.kt`, `data/feed/{FeedItemEntity,RssSourceEntity,FeedDao}.kt`, Room DB (v9→v10 migration + exported schema + migration test), `data/FeedRepository.kt`.

- `RssParser.parse(xml): List<FeedItem>` via `XmlPullParser` — supports RSS 2.0 `<item>` (title/link/pubDate/enclosure or media:thumbnail) and Atom `<entry>`. Pure; **unit-tested with bundled sample XML** (no network).
- `FeedItemEntity` (sourceId, title, link, publishedAt, thumbnailUrl, category) + `RssSourceEntity` (id, url, category, title, enabled). `FeedDao`: upsert, itemsByCategory Flow, prune (keep newest ~100 / 3 days).
- `FeedSources` presets: a small curated set per category (News/Sports) with real RSS URLs.
- `FeedRepository`: cache Flow for UI; `suspend refresh()` fetches enabled sources via `HttpURLConnection` (HTTPS, short timeout, neutral UA), parses, upserts, prunes; **no-op in incognito / feed-off**.
- Room migration 9→10 (add two tables) + exported schema + `MigrationTest`.
- **Tests:** parser (RSS+Atom samples), pruning window, migration. **Verify + Commit.**

### Task 6 — Weather (Open-Meteo + coarse location)

**Files:** `browser/feed/WeatherModels.kt` (pure JSON mapping), `data/WeatherRepository.kt`, permission wiring in `MainActivity`, `AndroidManifest` (`ACCESS_COARSE_LOCATION`).

- `WeatherRepository.load(lat,lon)` → Open-Meteo current + daily; cached with timestamp. City fallback (a small bundled city→lat/lon list or a simple geocode via Open-Meteo's free geocoding). Coarse location opt-in via FusedLocation; denied → city fallback.
- Pure JSON→model mapping unit-tested with a sample response.
- **Tests:** weather mapping. **Verify + Commit.**

### Task 7 — Feed UI (cards + sections)

**Files:** `ui/components/FeedCards.kt` (WeatherCard, NewsCard, SectionLabel), `HomePage.kt` (compose feed into the LazyColumn).

- Cards on Orbit tokens (card radius, hairline border, Space-Grotesk labels/numbers, DM-Sans body). Thumbnail with gradient placeholder when absent. Tap → open URL; long-press → open-new-tab / copy / hide-source.
- Order: hero → dials → Weather → News → Sports. All gated on feed toggle + non-incognito.
- **Verify:** build; home shows cached feed. **Commit.**

### Task 8 — Feed refresh (WorkManager + on-open)

**Files:** `feed/FeedRefreshWorker.kt`, `MainActivity`/App (schedule), `BrowserViewModel` (on-open throttle).

- Weekly-style periodic worker + on-open trigger guarded by a "last refreshed" timestamp (min 30 min). Reuse the existing filter-list updater pattern. No-op in incognito/feed-off.
- **Verify + Commit.**

### Task 9 — Settings → Home controls

**Files:** `SettingsRepository.kt` (feed prefs: showFeed, per-section toggles, sources, weather city/location), `ui/SettingsScreen.kt` (Home section UI + source/topic picker + custom RSS URL add).

- Master "Show feed" + per-section toggles; source picker (presets + add custom RSS URL); weather: use-location toggle / set-city. Persist in DataStore.
- **Tests:** prefs defaults/roundtrip. **Verify + Commit.**

### Task 10 — Final wiring, review & release

- Merge state into `BrowserScreen`/`BrowserViewModel`; confirm all privacy gates; run full unit suite; dispatch a whole-branch code review (privacy gates, incognito leaks, migration, offline behavior); fix findings.
- Bump `versionCode 7 / versionName "3.2"`; build **signed release**; verify signature; produce `Andromeda-v3.2-release.apk`.

## Testing summary (run)

Parser (RSS+Atom), pruning window, quick-dial ranking, weather mapping, incognito gating
(repos return empty/no-op), Room 9→10 migration, home-prefs roundtrip. Compose UI build-verified;
device pass for backdrop/scroll/permission/feed/incognito.
