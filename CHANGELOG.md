# Changelog

All notable changes to Andromeda. Newest first.

Compiled from git tags and merge commits. **Update this file when you tag a release** — same commit as
the version bump. Dates are release (tag) dates.

Development ran 2026-07-10 → 2026-07-24: **45 releases, 339 commits**.

---

## v6.x — Depth & polish

### v6.16 — 2026-08-14 · Recently-closed tabs are Orbit-scoped
- **Privacy fix.** `closed_tabs` had no `orbitId` and was read unfiltered, so a tab closed in *Work* appeared in *Personal*'s "Recently closed" list and reopened into whichever Orbit was active. Found while writing the documentation suite.
- `closed_tabs.orbitId` (schema **v21 → v22**); `observeRecent` filters by active Orbit; entries are filed under the **closing tab's own** Orbit; reopen lands in `entry.orbitId`.
- The 100-entry ring is now **per Orbit** — a busy Orbit could previously evict another's entries.
- Deleting an Orbit purges its closed tabs.
- **Migration discards legacy rows rather than backfilling them:** they were global, so attributing them to the first Orbit would have preserved the leak.
- Incognito was never affected — the insert was already gated on `!isIncognitoId(id)`.

### v6.15 — 2026-07-24 · arm64-only APK
- **Build:** dropped `armeabi-v7a` from `abiFilters`. APK **35.35 MB → 23.62 MB** (−33%), no functional change. arm32-only devices can no longer install (accepted). [ADR-0010](docs/adr/0010-arm64-only-apk.md)

### v6.14 — 2026-07-23 · Clear browsing data by time range
- Clear-data dialog offers **Last hour / Last 24 hours / All time** instead of all-or-nothing.
- Cookies and cache are always cleared in full — WebView has no time-range API, and the dialog says so.
- The range resets to "Last hour" on each open, so a stale "All time" can't cause an accidental wipe.

### v6.13 — 2026-07-23 · Reader typeface
- Serif (Georgia) / sans choice in reader mode, persisted alongside theme, size and width.

### v6.12 — 2026-07-23 · Bookmark search
- Search field on the Bookmarks screen filtering by title or URL as you type; blank query restores the folder-grouped view.

### v6.11 — 2026-07-23 · Reading-time estimate
- "N min read" byline under the reader-mode title (200 wpm, omitted when there are no words).

### v6.10 — 2026-07-23 · Bookmark folders
- Collapsible folder sections, per-row move-to-folder, and a "New folder…" dialog — surfacing the `bookmarks.folder` column that had existed unused since v1.
- No schema change.

### v6.9 — 2026-07-23 · Duplicate tab
- Tab long-press → **Duplicate**, inheriting the source tab's Orbit, incognito state and group.

### v6.8 — 2026-07-23 · Download cancel-race fix
- Closed a race where cancelling a queued/starting download could be overwritten by `RUNNING`, leaving an orphaned file and an "undead" download. Fixed with an atomic DB claim. [ADR-0012](docs/adr/0012-atomic-db-claim-for-races.md)

### v6.7 — 2026-07-23 · Data saver
- Global "Data saver" plus a per-site **Block images** tri-state override. Schema **v20 → v21**.

### v6.6 — 2026-07-23 · Never save for this site
- **Never** button on the save-password bar; a per-host suppression list with an undo path in Passwords.

### v6.5 — 2026-07-23 · Cross-subdomain password fill
- A login saved on `example.com` now fills on `login.example.com`, matched by **registrable-domain equality** — never suffix containment, so `example.com.evil.net` does not match. [ADR-0011](docs/adr/0011-curated-public-suffix-list.md)

### v6.4 — 2026-07-23 · Translate over Wi-Fi only
- Optional Wi-Fi-only restriction for language-model downloads (default off).

### v6.3 — 2026-07-23 · Player sleep timer
- "Stop after N min" and end-of-track, on an absolute deadline so a skipped tick self-corrects.

### v6.2 — 2026-07-23 · Black Hole shake gesture
- Shake-to-erase, opt-in and default off. The shake only **arms the confirmation dialog**; it is foreground-only and suppressed while incognito is locked.

### v6.1 — 2026-07-22 · Full-page on-device translation
- ML Kit translation and language ID running **entirely on-device** — page text is never transmitted. [ADR-0009](docs/adr/0009-mlkit-on-device-translate.md)
- **APK ~6 MB → ~37 MB** from ML Kit's per-ABI native libraries; x86/x86_64 dropped in the same release (~74 MB → ~37 MB).

### v6.0 — 2026-07-21 · Multi-share downloads + Andromeda Player
- Downloads multi-select with batch share/delete (every URI on `ClipData` for OEM permission grants).
- **Andromeda Player:** Media3/ExoPlayer engine with fully custom UI — background audio, lock-screen controls, queue, resume, speed, track selection, PiP, gesture brightness/volume. Schema **v19 → v20**. [ADR-0008](docs/adr/0008-media3-hybrid-player.md)

## v5.x — Integration with the platform

| Version | Date | Change |
|---|---|---|
| v5.9 | 2026-07-19 | Per-engine search suggestions (Google/DDG/Bing OpenSearch) **and a fixed incognito leak** — keystrokes had been going to Google from incognito tabs |
| v5.8 | 2026-07-19 | Custom search engines |
| v5.7 | 2026-07-19 | Add to home screen (launcher pins) |
| v5.6 | 2026-07-19 | `window.opener` via true popup adoption |
| v5.5 | 2026-07-19 | Per-Orbit downloads (schema 18 → 19) |
| v5.4 | 2026-07-19 | Share page as a QR code |
| v5.3 | 2026-07-19 | Camera capture for file uploads |
| v5.2 | 2026-07-18 | On-device QR scanner (ZXing + CameraX, fully offline) |
| v5.1 | 2026-07-18 | Passwords Phase 2 — **biometric lock** (defaults locked) + manual add/edit |
| v5.0 | 2026-07-18 | `window.open` / `target=_blank` open as real tabs |

## v4.x — "Fortress": profiles, passwords, privacy

| Version | Date | Change |
|---|---|---|
| v4.9 | 2026-07-18 | External app link handling |
| v4.8 | 2026-07-18 | `<input type=file>` upload support |
| v4.7 | 2026-07-16 | **Passwords & autofill Phase 1** — AES-256-GCM vault, key in AndroidKeyStore (schema 17 → 18) |
| v4.6 | 2026-07-16 | Offline weather cache |
| v4.5 | 2026-07-16 | **Black Hole** panic wipe |
| v4.4 | 2026-07-16 | Orbits Phase 3 — per-Orbit bookmarks + shortcuts (schema 16 → 17) |
| v4.3 | 2026-07-16 | Orbits Phase 2 — per-Orbit history (schema 15 → 16) |
| v4.2 | 2026-07-16 | **Orbits Phase 1** — profiles/containers with per-Orbit `ProfileStore` isolation (schema 13 → 14) · [ADR-0003](docs/adr/0003-orbits-profile-isolation.md) |
| v4.1 | 2026-07-16 | "Fortress polish" — Chrome-smooth UI, favicon capture, new-tab home search |
| v4.0 | 2026-07-15 | **Element Zapper** — tap any element to hide it forever, per-site, re-applied at document start |

## v3.x — "Horizon" and full-strength blocking

| Version | Date | Change |
|---|---|---|
| v3.2 | 2026-07-15 | **Horizon** — privacy-first home dashboard (RSS + Open-Meteo weather + quick dials, all source-direct), separate dark incognito, Space Grotesk / DM Sans type system |
| v3.1 | 2026-07-13 | Unified on the **Orbit design system** — shrink-not-hide bottom bar, customisable home, bottom-sheet menu |
| v3.0.3 | 2026-07-13 | Live timeline on the lock-screen player (position/duration + seek) |
| v3.0.1–3.0.2 | 2026-07-13 | Keep WebView audio/video playing when the screen locks |
| v3.0 | 2026-07-13 | Media JS bridge scoped to its owning kept-alive tab |
| v3-media-lockscreen | 2026-07-12 | Lock-screen media playback |
| v3-youtube-ux | 2026-07-12 | YouTube ad blocking (scriptlet data pruning + auto-skip) + bar UX |
| **v3-adblock-max** | 2026-07-12 | **Full-strength ad blocking** — ABP parser, token index, cosmetic filtering, four bundled lists · [ADR-0006](docs/adr/0006-own-adblock-engine.md) |
| v3-phase-6 | 2026-07-12 | Delight — Asteroid offline game, polish pass |
| v3-phase-5 | 2026-07-12 | Foundation — backup/restore, onboarding, app shortcuts, voice search |
| v3-phase-4 | 2026-07-12 | Safety shield — Safe Browsing, cookie-banner dismiss, Global Privacy Control |
| v3-phase-3 | 2026-07-12 | Reading stack — reading list, offline articles, read-aloud/podcast mode, print/PDF |
| v3-phase-2 | 2026-07-12 | **Own download engine** — segments, pause/resume, scheduling, previews; background media, PiP · [ADR-0007](docs/adr/0007-own-download-engine.md) |
| v3-phase-1 | 2026-07-12 | Tabs power — groups, search, undo close, pin/lock, grid⇄list, bulk actions, new logo |

## v2.x — Daily driver

| Version | Date | Change |
|---|---|---|
| v2.0 | 2026-07-12 | Housekeeping / release (V2-P6) |
| v2-phase-5 | 2026-07-12 | Privacy upgrades |
| v2-phase-4 | 2026-07-12 | Power features |
| v2-phase-3 | 2026-07-12 | Daily-driver essentials |
| v2-phase-2 | 2026-07-12 | Tab experience |
| v2-phase-1 | 2026-07-11 | **Orbit foundation** — the design system's first form |

## v1.0 — 2026-07-11 · First release

| Phase | Change |
|---|---|
| Phase 6 | Polish and release — **Andromeda v1.0** |
| Phase 5 | Context menu |
| Phase 4 | First ad blocker |
| Phase 3 | Privacy suite |
| Phase 2 | Settings, home, downloads |
| Phase 1 | Browser screen — address bar, progress, navigation |

---

## Conventions

- **`vX.Y`** tags mark releases; `versionCode` increments by one each time.
- Merges use `--no-ff`, so every feature is a visible unit in history.
- Schema changes are noted inline as `schema N → N+1`; the full chain is in [docs/DATA-MODEL.md](docs/DATA-MODEL.md).
- Design decisions live in [docs/adr/](docs/adr/); per-feature specs in [docs/superpowers/specs/](docs/superpowers/specs/).
