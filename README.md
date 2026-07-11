# Andromeda

A fast, private mobile web browser for Android, built from scratch in Kotlin.

**Status:** v1.0 — all six planned phases complete.

## Features

- 🌌 **Tabs** — instant switching with preserved page state; survive app restarts
- 🕶️ **Incognito** — in-memory-only tabs: no history, no persistence, gone on close
- 🛡️ **Ad blocking** — 52k+ EasyList domain rules, per-site allow toggle, per-page counter
- 🔒 **Privacy controls** — JavaScript/cookie toggles, clear browsing data, hard SSL blocking
- ⭐ **Bookmarks & history** — smart visit dedup, speed-dial home page
- ⬇️ **Downloads** — in-app manager with live progress; long-press to save images
- 🔍 **Choice of search engine** (Google / DuckDuckGo / Bing) and theme (system / light / dark)

## Tech

Kotlin · Jetpack Compose · MVVM (single `StateFlow` UI state) · Room (4-version migration chain) · DataStore · Android System WebView · JUnit (TDD, 61 unit + 12 instrumented tests)

## Architecture

```
UI layer        Compose screens + ViewModel (one immutable UiState)
Browser core    TabManager · AdBlockEngine · VisitPolicy · TabClosePolicy
Data layer      Room · DataStore · bundled filter lists
Platform        WebViewHolder (live WebView per tab, outside the compose tree)
```

Design decisions — engine choice (why System WebView over GeckoView or a Chromium fork), phased delivery, and documented limitations — live in [docs/superpowers/specs](docs/superpowers/specs/2026-07-10-mobile-browser-design.md) and the per-phase plans in [docs/superpowers/plans](docs/superpowers/plans/).

## Known limitations (documented, by design)

- Ad blocking is domain-level (display ads/trackers); in-stream video ads need cosmetic filtering + scriptlets — see backlog
- Incognito does not isolate cookies from normal tabs (Android WebView's CookieManager is global; ProfileStore isolation backlogged)

## Build

```
./gradlew assembleDebug          # debug build
./gradlew testDebugUnitTest      # unit tests
./gradlew connectedDebugAndroidTest  # device tests (emulator required)
./gradlew assembleRelease        # signed release (requires keystore.properties)
```

Built as a learning project following a full professional SDLC: spec → phased plans → TDD → feature branches → tagged releases.
