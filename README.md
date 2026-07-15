# Andromeda

A fast, private, power-user mobile web browser for Android, built from scratch in Kotlin.

**Status:** v3.2 "Horizon" — a living home. v3.2 adds a privacy-first home dashboard (RSS news/sports + Open-Meteo weather + most-visited quick dials, all fetched source-direct with no keys or trackers), a fully separate dark incognito mode, and an official type system (Space Grotesk + DM Sans) on a refined "Orbit" palette. v3.1 unified the app on the Orbit design system (shrink-not-hide bottom bar, customizable home, bottom-sheet menu); v3.0 added a real download engine, a reading stack, a safety shield, and a from-scratch full-strength ad blocker.

## Features

### Tabs & navigation
- 🌌 **Tabs** — instant switching with preserved page state; survive app restarts
- 🏝️ **Tab groups & auto-islands** — links auto-group with their opener; pin, lock, color, collapse
- 🔎 **Tab search**, recently-closed reopen, and a grid⇄list switcher
- 🕶️ **Incognito** — in-memory-only tabs: no history, no persistence, never touches disk
- 📜 **Chrome-style toolbar** — centered home search that drops to a bottom bar, auto-hiding on scroll

### Ad & tracker blocking (v3, rebuilt)
- 🛡️ **Full Adblock-Plus engine** — network rules with `$domain`, `$third-party`, resource types, exceptions (`@@`), anchors, wildcards, and hosts-format lists; token-indexed for O(tokens) matching over ~100k rules
- 🎨 **Cosmetic filtering** — generic + per-domain element hiding, injected at document start
- 📺 **YouTube / YT Music** — scriptlet-based ad-data pruning + auto-skip (in-stream ads other WebView browsers can't touch)
- 📚 **Four bundled lists** — EasyList, EasyPrivacy, AdGuard Mobile, Peter Lowe's — auto-updated weekly, per-list toggles
- 📊 **Privacy stats** on the home page (lifetime blocked + estimated data saved)

### Downloads & media
- ⬇️ **Own download engine** — parallel segments, pause/resume/cancel/retry, auto-resume across process death, live speed graph, scheduling (Wi-Fi / later)
- 🎵 **Background & lock-screen playback** — opted-in sites keep playing when locked, with lock-screen Play/Pause/Prev/Next and auto-advance to the next track
- 🎬 **Picture-in-Picture** for fullscreen video

### Reading
- 📖 **Reading list** with offline cleaned-article copies (read in airplane mode)
- 🗣️ **Read-aloud + podcast mode** — TTS with a media notification, plays through your unread list
- 🖨️ **Print / Save as PDF**, per-site display memory, and a polished reader (font / theme / width)

### Safety
- 🚨 **Safe Browsing** with a custom full-screen warning for phishing/malware
- 🍪 **Cookie-banner auto-dismiss** and **Global Privacy Control**
- 🔒 **Privacy controls** — JavaScript/cookie toggles, clear data, hard SSL blocking, HTTPS-only

### Foundation & delight
- ⭐ **Bookmarks & history**, editable **home shortcut grid**
- 💾 **Backup & restore** to a single JSON file (SAF)
- 👋 **Onboarding**, launcher **app shortcuts**, **voice search**, clipboard chip, global text scaling
- 🚀 **Asteroid game** on the offline error page

## Tech

Kotlin · Jetpack Compose · MVVM (single `StateFlow` UI state) · Room (9-version migration chain, exported schemas) · DataStore · WorkManager · Android System WebView · platform MediaSession/TextToSpeech · JUnit + MockWebServer (TDD, 344 unit + instrumented migration/DAO tests)

## Architecture

```
UI layer        Compose screens + ViewModel (immutable UiState)
Browser core    TabManager · AdBlockEngine (ABP parser + token index) · DownloadPlanner
                ReaderMode · SiteSettingsResolver · TtsQueue · BarScrollPolicy · pure & unit-tested
Services        DownloadService · MediaHoldService · ReadAloudService (all foreground)
Data layer      Room · DataStore · bundled + auto-updated filter lists
Platform        WebViewHolder (live WebView per tab, outside the compose tree)
```

Design decisions, the v3→v7 roadmap, and documented limitations live in [docs/superpowers/specs](docs/superpowers/specs/) and the per-phase plans in [docs/superpowers/plans](docs/superpowers/plans/). Browser-landscape research is in [docs/research](docs/research/).

## Known limitations (documented, by design)

- WebView `shouldInterceptRequest` doesn't see WebSockets, so `$websocket`/`$ping` rules are unenforceable; procedural cosmetics (`:has-text`, scriptlet filters beyond the YouTube pack) aren't supported — extension APIs don't exist on WebView
- YouTube in-stream ads are mitigated (data pruning + auto-skip), not guaranteed zero — server-stitched ads are a hard limit for every WebView browser
- Incognito does not isolate cookies from normal tabs (WebView's CookieManager is global; ProfileStore isolation backlogged for v4)
- Background media on WebView can still be killed by aggressive OEM battery managers (foreground service mitigates, doesn't guarantee)

## Build

```
./gradlew assembleDebug              # debug build
./gradlew testDebugUnitTest          # unit tests
./gradlew connectedDebugAndroidTest  # device tests (emulator required)
./gradlew assembleRelease            # signed release (requires keystore.properties)
```

Built as a learning project following a full professional SDLC: research → spec → phased plans → TDD / subagent-driven build → reviewed feature branches → tagged releases.
