# Living Home & Full Incognito Mode — Design

**Date:** 2026-07-14
**Version target:** v3.2 ("Horizon")
**Status:** Approved direction, pending spec review

## Goal

Turn Andromeda's home page from a static logo screen into a **living, privacy-respecting
dashboard** (cosmic logo hero + quick dials + an RSS-powered feed of news, sports, and
weather), and make **incognito a fully separate dark experience** rather than a per-tab label.

Two parts, one cohesive release. Each part is independently shippable and testable.

## Non-negotiable principles (Global Constraints)

Every task inherits these:

- **Privacy is the product.** The feed must never route through a third-party profiler or ad
  network. All content fetches go **directly to the source publisher over HTTPS**. No API keys,
  no analytics, no tracking pixels, no telemetry on what the user reads.
- **Incognito never leaks.** No feed, no quick dials, no weather, no privacy stats, no history
  reads, and **no network feed fetches** while any incognito context is active or being rendered.
  No DAO writes for negative/incognito tab ids. (Existing incognito guarantees stay intact.)
- **Everything is opt-in and reversible.** The user can turn the whole feed off and return to
  today's calm "Focused" home. Feed sections toggle independently.
- **No new secrets in VCS.** Open-Meteo needs no key. `keystore.properties` / `*.jks` stay
  gitignored. No credentials are introduced.
- **Orbit design system only.** All new UI uses `OrbitSpacing`/`OrbitRadii`/`OrbitText`/
  `orbit()` tokens and shared components (`OrbitTopBar`, `OrbitListRow`). No ad-hoc literals.
- **Offline-tolerant.** Every feed section degrades gracefully (cached content, then a quiet
  "couldn't refresh" state) — the home page must always render instantly from cache, never block
  on network.

## Design system refresh (type & color)

Chosen with the ui-ux-pro-max "Tech Startup / Dark-OLED" guidance; applied app-wide, not just the
home.

- **Typography (bundled, offline):** ship **Space Grotesk** (display — wordmark, section labels,
  numerals, top-bar titles) and **DM Sans** (body/UI text) as `res/font` assets (both open-license;
  no runtime download). Extend `Type.kt` with a display vs. body role split; existing `orbitDisplay`/
  `orbitTitle` use Space Grotesk, `orbitBody`/`orbitCaption` use DM Sans.
- **Refined Orbit palette** (`Color.kt`): brighter premium whites (primary text ~`#F2F3FF`),
  enrich the blue→cyan brand gradient with the hero's **cosmic violet** as a midpoint —
  `#2C5BE6 → #7A5CFF → #46D0F5` — used for the wordmark and emphasis. Surfaces stay deep-space
  (`base ~#070716`, `surface ~#111228`, `elevated ~#1A1B3C`). Keep contrast ≥ 4.5:1 (verified),
  and keep the existing light scheme coherent (home is always-dark regardless).
- **Effect discipline:** minimal glow only (subtle drop-shadow on the wordmark), hairline borders
  over heavy shadows (the v3.1.1 direction). No emoji as structural icons.

## Tech stack

Kotlin · Jetpack Compose (Material3) · MVVM single-`StateFlow` · Room (new feed-cache tables,
schema bump + exported migration) · DataStore (feed prefs) · WorkManager (throttled refresh) ·
`HttpURLConnection`/OkHttp for fetch · Android's `XmlPullParser` for RSS/Atom · Open-Meteo JSON
for weather · System WebView (unchanged).

---

## Part 1 — Living Home (normal mode)

### Layout (top → bottom, scrolls under the bottom OmniBar)

1. **Cosmic backdrop + wordmark hero** — the new `Andromeda_Homescreen` artwork is a **full-bleed,
   borderless, low-opacity backdrop** (~0.4 alpha) anchored to the top ~66% of the home, masked to
   **fade into the base color** by mid-screen so feed content below stays clean and readable. No
   bordered logo box. Over it sit the gradient **"Andromeda" wordmark** + greeting. Because the
   artwork is a dark-space image, **the home renders always-dark** (its own dark Orbit scheme
   regardless of the app's light/dark setting — like Chrome/Edge's dark new-tab page); the rest of
   the app still follows the system theme. Incognito keeps its own bare treatment (Part 2).
2. **Greeting** (existing `showGreeting` pref) + the existing **shortcut row/grid**.
3. **Quick dials** — auto most-visited tiles derived from history (non-incognito only), shown
   alongside manual shortcuts. Capped (e.g. top 8), de-duplicated against manual shortcuts.
4. **Feed** — a vertically scrolling column of Orbit cards:
   - **Weather card** (top): current conditions + short forecast from Open-Meteo. Location via
     **opt-in coarse location** (`ACCESS_COARSE_LOCATION`); if denied or unset, a tap-to-set-city
     fallback. Never blocks; shows cached last reading.
   - **News**: headline cards (title, source, thumbnail if the item provides one, relative time),
     tap opens the article in a normal tab. Sources/topics user-selectable; sensible defaults.
   - **Sports**: same card style, sourced from sports RSS feeds (latest sports *news*, not live
     scoreboards — documented limitation). User picks leagues/interests from a preset list.
5. **Feed is one scroll surface.** The hero/shortcuts/quick-dials/feed live in a single
   `LazyColumn` with a bottom content inset of `OmniBarReservedHeight` (fixed — never resized on
   scroll; see the scroll-stability lesson in v3.1.2).

### Feed behavior

- **Instant render from cache.** On open, the home shows the last cached feed immediately, then
  kicks a throttled background refresh (min interval, e.g. 30 min) that updates cards in place.
- **Manual refresh** via pull-to-refresh on the home scroll surface (distinct from the WebView's).
- **Per-source fetch** with a short timeout; a failed source is skipped, not fatal. Partial feeds
  are fine.
- **Card actions:** tap → open in new/current tab; long-press → "Open in new tab", "Copy link",
  "Hide this source".

### Feed controls (Settings → Home, extends the existing section)

- Master **"Show feed"** toggle (off = today's Focused home).
- Per-section toggles: News / Sports / Weather / Quick dials.
- **Source & topic pickers**: choose from a curated preset list of reputable RSS sources per
  category, and add a **custom RSS URL**. Weather: set city / use location.
- Refresh cadence is fixed (not user-facing) to keep it simple.

### Data architecture

- **`RssSource`** (id, url, category, title, enabled) — presets bundled, plus user-added.
- **`FeedItem`** (source id, title, link, published time, thumbnail url, category) — cached in a
  new Room table; pruned to a rolling window (e.g. 100 items / 3 days).
- **`FeedRepository`**: reads cache (Flow) for the UI; `refresh()` fetches enabled sources,
  parses via `XmlPullParser` (RSS 2.0 + Atom), upserts, prunes. Pure parser is unit-tested with
  bundled sample XML (no network in tests).
- **`WeatherRepository`**: Open-Meteo call (lat/lon → current + daily), cached with timestamp;
  coarse-location resolver with a manual-city fallback.
- **Quick dials**: a history-DAO query (top hosts by visit count, non-incognito), mapped to tiles.
- **Refresh**: a WorkManager worker (reuses the existing filter-list update infra pattern),
  throttled; also an on-open trigger guarded by a "last refreshed" timestamp.

### Privacy specifics

- Feed/weather/quick-dials code paths are **hard-gated on `!isIncognito`** and on the master
  feed toggle.
- Requests carry a neutral, non-identifying User-Agent; no cookies stored for feed fetches; GPC
  where applicable. No third-party domains beyond the chosen publishers + `api.open-meteo.com`.
- The RSS fetch honors the same cleartext-blocked policy as the rest of the app (HTTPS only).

---

## Part 2 — Full Incognito Mode (separate & dark)

### Behavior

- When the **active tab is incognito**, the **entire browser UI renders in the dark Orbit
  scheme** — OmniBar, menus, home, library entry points — not just the tab switcher. Achieved by
  providing `darkOrbit` (and the matching M3 dark color scheme) at the app content root whenever
  the active tab is incognito, so the whole tree recolors without duplicating components.
- **Incognito home** = the private screen only: the "You've gone Incognito" explainer + the
  incognito privacy note. **No feed, no quick dials, no weather, no stats, no most-visited.**
- A persistent, unobtrusive **incognito indicator** in the OmniBar (mask glyph) so the mode is
  always obvious.
- **Entering/leaving:** via the existing tab-switcher toggle and "New incognito tab". Switching
  the active tab between a normal and an incognito tab flips the whole UI theme accordingly.
- The tab-switcher separation shipped in v3.1.2 stays; this extends the dark treatment app-wide.

### Privacy specifics

- The incognito theme switch is **driven purely by the active tab's `isIncognito`** — no new
  persistence. Nothing about incognito browsing is written to disk.
- All Part-1 feed/dials/weather/stats surfaces are already gated off in incognito (Global
  Constraints) — this part must not introduce any surface that re-enables them.

---

## UX / visual details

- Feed cards: `OrbitRadii.card` corners, `surfaces.surface` fill, subtle border (matching the
  v3.1.1 hairline treatment), `orbitTitle`/`orbitBody`/`orbitCaption` type. Thumbnails clipped to
  the card radius, with a gradient placeholder when absent (reuse the tab-card placeholder idea).
- Section headers: small `orbitCaption` label ("News", "Sports") with an optional "More" affordance.
- Entrance: the home's existing fade+rise applies to the whole surface (keep it subtle for a long
  list — animate the hero/shortcuts, not every card).
- Hero/backdrop asset: convert `Andromeda_Homescreen.jpeg` → an optimized `res/drawable-nodpi`
  WebP, sized for a full-width backdrop (~1080px wide). Rendered at ~0.4 alpha with a vertical
  fade-to-transparent gradient (Compose `Brush`/alpha) so it never competes with feed text.

## Testing

- **Pure/unit (run):** RSS/Atom parser (bundled sample XML → `FeedItem`s), feed pruning window,
  quick-dial ranking/de-dup, weather JSON mapping, incognito gating (feed repo returns empty /
  no-op in incognito). MockWebServer for repository fetch paths.
- **Build-verify** the Compose UI (per the project's build-first workflow); manual device pass
  for the visual/scroll/permission behaviors.
- **Regression:** existing incognito privacy tests still green; Room migration test for the new
  tables; home renders with feed off (Focused mode) unchanged.

## Out of scope / documented limitations

- **Live sports scoreboards** (need a tracking API) — sports is latest *news* via RSS for now.
- Full-text article extraction for feed items (reuses existing reader mode when the user opens one).
- Feed personalization/ranking beyond user-chosen sources + recency (no behavioral profiling — by design).
- Push notifications for feed items.

## Rollout

- Room schema bump with an exported migration + migration test.
- New runtime permission: `ACCESS_COARSE_LOCATION` (opt-in, only prompted when the user enables
  location-based weather).
- Version: **v3.2**, versionCode 7. Ships after review + on-device verification, signed release.
