# Browse

A mobile web browser for Android, built from scratch in Kotlin.

**Status:** Phase 1 of 6 complete — a working browser with smart address bar, page navigation, and loading progress.

## Features

- ✅ Combined address/search bar (URL detection vs. search-engine query)
- ✅ Chromium rendering via Android System WebView
- ✅ Back / forward / reload with page-history-aware system back button
- 🔜 Tabs, bookmarks, history, downloads (Phase 2)
- 🔜 Privacy suite: incognito, cookie & JS controls (Phase 3)
- 🔜 Ad blocker with EasyList filter rules (Phase 4)
- 🔜 Enhanced download manager & media detection (Phase 5)

## Tech

Kotlin · Jetpack Compose · MVVM (single `StateFlow` UI state) · Android System WebView · JUnit (TDD)

## Architecture

```
UI layer        Compose screens + ViewModels
Browser core    TabManager · AdBlockEngine · PrivacyManager · DownloadCoordinator
Data layer      Room · DataStore · filter lists
```

Design decisions — including why System WebView over GeckoView or a Chromium fork — are documented in [docs/superpowers/specs](docs/superpowers/specs/2026-07-10-mobile-browser-design.md).

## Build

Open in Android Studio and run, or:

```
./gradlew assembleDebug
./gradlew testDebugUnitTest
```
