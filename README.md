# Andromeda

A fast, private, power-user mobile web browser for Android, built from scratch in Kotlin.

**Status:** **v6.15** — 45 releases between 2026-07-10 and 2026-07-24. 26,800 lines of Kotlin across
166 files, 82 JVM test files, Room schema v21. Distributed as a sideloaded arm64 APK (23.6 MB).

Built as a learning project following a full professional SDLC: research → spec → phased plan → TDD →
adversarial review → tagged release. **Every architectural decision is recorded, including the ones
that were rejected** — see [docs/adr/](docs/adr/).

## Documentation

| Document | What it answers |
|---|---|
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | How the app is put together — layers, packages, invariants |
| [docs/DATA-MODEL.md](docs/DATA-MODEL.md) | What's stored, how it's scoped, the v1→v21 migration chain |
| [docs/SECURITY-PRIVACY.md](docs/SECURITY-PRIVACY.md) | Threat model and exactly what is guaranteed |
| [docs/adr/](docs/adr/) | **Why** each decision was made, and what was rejected |
| [docs/ROADMAP.md](docs/ROADMAP.md) | What is not done yet |
| [CHANGELOG.md](CHANGELOG.md) | What shipped, when |
| [docs/WORKFLOW.md](docs/WORKFLOW.md) | The per-feature ritual and release checklist |
| [docs/TESTING.md](docs/TESTING.md) | Test strategy and the traps that make tests lie |
| [docs/GOTCHAS.md](docs/GOTCHAS.md) | Environment and build traps worth minutes of your life |

Full index with maintenance rules: [docs/README.md](docs/README.md).

## Features

### Tabs & navigation
- 🌌 **Tabs** — instant switching with preserved page state; survive app restarts
- 🏝️ **Tab groups & auto-islands** — links auto-group with their opener; pin, lock, colour, collapse
- 🔎 **Tab search**, recently-closed reopen, duplicate tab, grid⇄list switcher
- 🕶️ **Incognito** — in-memory-only tabs that never touch disk, behind an optional biometric lock
- 🪐 **Orbits** — isolated profiles (Work / Personal) with **separate cookies** via per-Orbit WebView profiles, plus their own tabs, history, bookmarks, logins and downloads

### Ad & tracker blocking
- 🛡️ **Full Adblock-Plus engine, written from scratch** — `$domain`, `$third-party`, resource types, exceptions (`@@`), anchors, wildcards, hosts-format lists; token-indexed for O(tokens) matching over ~100k rules
- 🎨 **Cosmetic filtering** — generic and per-domain element hiding, injected at document start
- 📺 **YouTube / YT Music** — scriptlet ad-data pruning + auto-skip
- 🎯 **Element Zapper** — tap any element to hide it forever, per-site
- 📚 **Five bundled lists** — EasyList, EasyPrivacy, AdGuard Mobile, Peter Lowe's, cookie annoyances — auto-updated weekly, individually toggleable
- 📊 **Privacy stats** on the home page

### Privacy & safety
- 🔐 **Password manager** — AES-256-GCM, key in AndroidKeyStore, per-Orbit, behind a device-auth lock; HTTPS-only, tap-to-fill, cross-subdomain matching by registrable-domain equality
- 🕳️ **Black Hole** — one-tap panic wipe, optionally shake-triggered (opt-in, always confirmed)
- 🚨 **Safe Browsing** with a full-screen interstitial
- 🍪 **Cookie-banner auto-dismiss** and **Global Privacy Control**
- 🧹 **Clear browsing data by time range** — last hour / 24 hours / all time
- 🔒 Per-site JavaScript / cookies / images, hard SSL blocking, HTTPS-only mode

### Downloads & media
- ⬇️ **Own download engine** — parallel segments, pause/resume/cancel/retry, auto-resume across process death, live speed graph, Wi-Fi/later scheduling, multi-select share
- 🎬 **Andromeda Player** — Media3/ExoPlayer engine with fully custom UI: background audio, lock-screen controls, queue, resume, speed, track selection, PiP, sleep timer, gesture brightness/volume
- 🎵 **Background & lock-screen playback** for opted-in sites

### Reading & translation
- 📖 **Reading list** with offline cleaned-article copies (readable in airplane mode)
- 🗣️ **Read-aloud + podcast mode** — TTS with a media notification
- 📰 **Reader mode** — theme, size, width, serif/sans, reading-time estimate
- 🌐 **Full-page translation, fully on-device** (ML Kit) — page text is never transmitted; works offline and in incognito
- 🖨️ **Print / Save as PDF**

### Foundation & delight
- ⭐ Bookmarks with folders and search; history; editable home quick-dial grid
- 🏠 **Home dashboard** — RSS news + Open-Meteo weather + most-visited, all fetched source-direct with no keys or trackers
- 📷 **QR scanner and generator** — on-device, offline
- 💾 **Backup & restore** to a single JSON file (SAF)
- 👋 Onboarding, launcher shortcuts, voice search, clipboard chip, global text scaling
- 🚀 **Asteroid game** on the offline error page

## Tech

Kotlin · Jetpack Compose (Material 3) · MVVM with a single `StateFlow` state surface · Room (21-version
migration chain, exported schemas) · DataStore · WorkManager · Android System WebView ·
`androidx.webkit` ProfileStore · Media3/ExoPlayer · ML Kit (on-device) · CameraX + ZXing ·
JUnit + MockWebServer + coroutines-test

**No backend. No analytics. No crash reporting. No API keys. No accounts.** Every network call the app
makes on its own behalf is HTTPS and goes source-direct.

## Architecture at a glance

```
UI            Compose screens (stateless) + reusable components
Presentation  BrowserViewModel — all state as StateFlows, intent functions in
Bridge        WebViewHolder (a live WebView per tab, outside the Compose tree)
Domain        browser/ · adblock/ · translate/ · media/ · download/ — pure, JVM-tested
Services      DownloadService · AndromedaPlayerService · MediaHoldService · ReadAloudService
Data          Room (16 entities) · DataStore · bundled filter lists
```

Details in [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Build

```bash
export JAVA_HOME="C:/Program Files/Android/Android Studio/jbr"

./gradlew assembleDebug              # debug build
./gradlew testDebugUnitTest          # unit tests (the suite that actually runs)
./gradlew connectedDebugAndroidTest  # migration/DAO tests — requires a device
./gradlew assembleRelease            # signed release — requires keystore.properties
./gradlew :app:signingReport         # verify signing config resolves
```

Release signing reads a gitignored `keystore.properties` (`storeFile`, `storePassword`, `keyAlias`,
`keyPassword`). Without it, debug builds still work and the release signing config is simply skipped.
See [ADR-0014](docs/adr/0014-keystore-outside-vcs.md).

The APK is **arm64-v8a only** ([ADR-0010](docs/adr/0010-arm64-only-apk.md)) — arm32-only devices cannot
install it.

## Known limitations — by design, not bugs

These follow from building on the System WebView ([ADR-0001](docs/adr/0001-system-webview.md)):

- **No extension support.** uBlock Origin and WebExtensions are impossible; hence the hand-written engine. Procedural cosmetic filters (`:has-text`) are unreachable.
- `shouldInterceptRequest` never sees WebSockets, so `$websocket` / `$ping` rules are unenforceable.
- YouTube in-stream ads are mitigated, not guaranteed zero — server-stitched ads are a hard limit for every WebView browser.
- Translation is a **snapshot at trigger time**: SPA / infinite-scroll content added later stays untranslated until re-run (2,000-node cap).
- Background media can still be killed by aggressive OEM battery managers.
- Two tables (`closed_tabs`, `reading_list`) are **not** Orbit-scoped — tracked in [docs/ROADMAP.md](docs/ROADMAP.md).
