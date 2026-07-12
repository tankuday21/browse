# Andromeda v3 — "Daily Driver" — Design Spec

**Date:** 2026-07-12 · **Status:** pending owner review
**Baseline:** v2.0 (tag `v2.0`, DB schema v5, 82 unit + 12 instrumented tests green)
**Roadmap context:** [v3→v7 roadmap](2026-07-12-andromeda-roadmap-v3-v7.md) · feature IDs from the [candidate catalog](../../research/2026-07-12-v3-candidate-catalog.md)

## 1. Goal & success criteria

**Goal:** after v3, the owner (and any daily user) never misses Chrome in everyday use. v3 adds the features people touch every session: grown-up tabs, a real download engine, background media, a reading stack, visible safety, autofill, backup, and onboarding.

**Success =** owner uses Andromeda as sole default browser for a week and reports no "I had to open Chrome for X" moments in v3-scope areas.

## 2. Scope

**In (30 features):** B1 B2 B3 B5 B6 B7 B9 · G1 G2 G3 G4 G7 · H1 H3 H5 H6 H7 · D1 D2 D5 · F1 · J1 J2 J4 · C1 C3 · A2 A5 A6 · I3 · K1
**Out (later versions):** everything in v4–v7 per the roadmap. Explicitly NOT in v3: Orbits profiles, Black Hole, element zapper, translate, PDF viewer, omnibox @-scoping, sync, AI.

## 3. Phases

Six phases, each a feature branch → tests green → emulator verification → merge `--no-ff` → tag `v3-phase-N`.

### P1 — Tabs Power (B1 B2 B3 B5 B6 B7 B9)
- **Tab groups (B1):** named + colored groups; manual assign via switcher long-press/drag; **auto-islands** — a tab opened *from* another tab joins its group (toggleable in Settings > Tabs). Groups render as stacked cards in the switcher; open a group → group view.
- **Tab search (B2):** search field in switcher; matches title + URL (page-content search deferred — see §8).
- **Undo close (B3):** 100-deep recently-closed ring (persisted); snackbar undo on close; "Recently closed" sheet in switcher; restoring a group restores all members.
- **Pin + lock (B5):** pinned tabs sort first with a dot marker; locked tabs require a confirm to close.
- **List view (B6):** switcher layout toggle grid ⇄ list (persisted in settings).
- **Bulk actions (B7):** long-press → selection mode → close / group / share-as-list.
- **Badge (B9):** Command Bar tab count shows "∞" at 100+.
- **Data:** Room migration v5→v6 — `tab_groups` table (id, name, color, position); `tabs` gains `groupId`, `pinned`, `locked`, `position`; new `closed_tabs` table (ring, cap 100). Incognito tabs: groups allowed in-memory only, never persisted (existing negative-id rule).

### P2 — Download Engine + Media (G1 G2 G3 G4 G7)
- **Own engine (G1):** replaces system DownloadManager for http(s). Range-request segmented downloading (2–6 segments by size), pause/resume/cancel/retry, auto-resume on connectivity return (WorkManager), foreground service with progress + speed; Downloads screen gains live speed graph and per-download controls. Falls back to single-stream when server lacks Range support. Existing `downloads` Room table extended (migration v6→v7: totalBytes, segments, state, etag/resumeData).
- **Previews (G2):** tap completed download → in-app preview sheet (images, audio/video via MediaPlayer, text; APK → info + install intent; else → open-with chooser).
- **Background playback (G3):** per-site opt-in (Settings > Media + site toggle); when active media is playing and the app backgrounds, keep the WebView resumed, post a MediaSession notification (play/pause), hold audio focus. Off by default.
- **PiP (G4):** fullscreen video (`onShowCustomView`) + home → `enterPictureInPictureMode`.
- **Scheduling (G7):** "Download later" option (start on Wi-Fi / at chosen time) via WorkManager constraints.

### P3 — Reading Stack (H1 H3 H5 H6 H7)
- **Reading list (H1):** "Save for later" in menu + bar long-press; saving stores the *cleaned reader HTML* to disk for offline; Reading List screen (unread/read, swipe to delete); Room migration v7→v8 (`reading_list` table). Opens in ReaderOverlay, works in airplane mode.
- **Read-aloud (H3):** TTS on any reader page (play/pause/speed); **podcast mode** — play through the unread reading list sequentially with MediaSession notification, screen off.
- **Print / save-as-PDF (H5):** menu items via WebView print adapter.
- **Per-site memory (H6):** `site_settings` table (host, textZoom, forceDark, desktopMode overrides) applied on navigation; set from existing menu toggles ("remember for this site" chip). Migration shares v7→v8.
- **Reader polish (H7):** fix known title/nav leakage; add font size/theme/width controls to ReaderOverlay.

### P4 — Safety & Annoyance Shield (D1 D2 D5 C3)
- **Safe Browsing (D1, upgraded per owner's malicious-site question):**
  1. Verify WebView Safe Browsing fires on our builds using Google's official test URLs (emulator, documented evidence in plan).
  2. Handle `onSafeBrowsingHit` → Orbit-styled full-screen warning ("This site may steal your information" + threat type + big **Go back**, small "proceed anyway"), replacing the stock interstitial.
  3. Settings > Privacy: "Safe Browsing" toggle (default ON) + one-line explainer.
- **Cookie-banner auto-dismiss (D2):** bundle an annoyance filter snapshot (EasyList Cookie / Fanboy Annoyance subset) through the existing FilterListParser + cosmetic injection path; Settings toggle (default ON).
- **GPC (D5):** send `Sec-GPC: 1` on main-frame navigations (loadUrl extra headers) + `navigator.globalPrivacyControl` JS shim (which is what most sites actually check); Settings toggle. Honest limit documented: sub-resource requests don't carry the header on WebView.
- **Privacy stats (C3):** home page block — total ads/trackers blocked (lifetime counter in DataStore), estimated data saved (blocked count × 50 KB heuristic, labeled "estimated").

### P5 — Foundation (F1 J1 J2 J4 C1 A2 A5 A6 I3)
- **Autofill (F1):** verify/enable Android Autofill in WebView with the user's existing password manager (Google/Samsung/Bitwarden); fix focus/structure issues found in testing. No credential storage of our own (that's v4 F2).
- **Backup/restore (J1):** Settings > "Back up" → single JSON file via SAF (bookmarks incl. folders, settings, reading-list metadata, tab groups, shortcuts); "Restore" merges with confirm. Versioned schema in the file.
- **Onboarding (J2):** first-run only, 3 skippable screens: (1) Orbit value pitch, (2) import bookmarks (reuse BookmarkIO + file picker), (3) set-default-browser ask (existing RoleManager flow).
- **App shortcuts (J4):** launcher long-press → New tab / New incognito tab.
- **Home shortcut grid (C1):** replaces bookmarks-speed-dial with a user-managed grid: add current page, long-press to reorder/remove; `home_shortcuts` table (migration v8→v9); "add to home grid" in menu.
- **Voice search (A2):** mic icon in Command Bar edit state → RecognizerIntent → search.
- **Bar long-press (A5):** copy URL / paste-and-go / share.
- **Clipboard chip (A6):** on bar focus, if clipboard holds a URL, show a "Go to copied link" suggestion (reads clipboard only on focus).
- **Text scaling (I3):** Settings > Accessibility: text size slider (WebSettings.textZoom, live preview) + force-enable pinch zoom.

### P6 — Delight + Improve Pass + Release (K1 + standing rule 1)
- **Asteroid game (K1):** on the "Lost in space" error page when offline: tap to play — starship dodges asteroids (Compose Canvas, high score in DataStore).
- **Improve pass:** thumbnail quality/refresh polish; menu reorganization for the ~12 new entries (icon action row + grouped list per research consensus); animation/jank pass on switcher + new screens.
- **Release:** full regression (all unit + instrumented tests), version 3.0 (versionCode 3), R8 signed release APK, README + screenshots refresh, tag `v3.0`, owner phone acceptance checklist.

## 4. Architecture notes

- **No re-architecture needed.** All features hang off existing seams: `WebViewHolder` (engine boundary — G3/G4/D1/F1 live here), `BrowserViewModel` + `pendingCommand` (UI commands), Room + migrations (persistence), FilterListParser/AdBlockEngine (D2), ReaderMode (H1/H3/H7).
- **New modules (pure logic, unit-tested):** `TabGroupPolicy` (auto-island + close semantics), `ClosedTabRing`, `DownloadPlanner` (segment math, resume validation), `SiteSettingsResolver`, `BackupCodec` (round-trip), `AnnoyanceRules` (list subset), `TtsQueue` (podcast ordering).
- **New Android services:** `DownloadService` (foreground), `MediaHoldService` (G3 audio focus + notification).
- **DB:** migrations v5→v6 (P1), v6→v7 (P2), v7→v8 (P3), v8→v9 (P5) — chained, each with an instrumented migration test (existing pattern).
- **Permissions added:** `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MEDIA_PLAYBACK`/`DATA_SYNC`, `POST_NOTIFICATIONS` (runtime ask at first download), `RECORD_AUDIO` already present (A2 uses system intent — no new mic permission needed).

## 5. Testing

- TDD (red-green) for every pure-logic module in §4; fakes extended (FakeDownloadDao etc.).
- Instrumented: migration chain v5→v9; Room DAOs for new tables.
- Per-phase emulator verification with screenshots (existing adb workflow), incl. D1 verified against Google's Safe Browsing test URLs and G1 verified against a large real file (pause/resume/kill-network mid-download).
- Regression rule unchanged: no phase merges with failing tests.

## 6. Risks & honest notes

- **G3 background playback** is the riskiest item: WebView isn't designed for it; OEM battery managers may kill it. Mitigation: per-site opt-in, foreground service, documented device caveats. If it proves unreliable on real hardware, it ships flagged "experimental."
- **F1 autofill** depends on WebView's built-in autofill provisioning quality; scope is verify-and-fix, not build-from-scratch.
- **D2** annoyance lists can break site layouts; per-site disable rides the existing allowlist mechanism.
- **G1** replaces a battle-tested system service with our code; keep "use system downloader" as a hidden fallback setting for one version.
- Six phases is a marathon (larger than v2). Owner may run it autonomously (as v2) or phase-by-phase with acceptance between.

## 7. Acceptance (owner, on phone, per phase or at v3.0)

P1: create/rename/color a group; auto-island on link-open; search a tab; undo a close; pin survives restart. P2: pause+resume a 100 MB download; kill Wi-Fi mid-download and watch auto-resume; play a YouTube video with screen off (opt-in site); PiP. P3: save an article, airplane mode, read it, listen to it; site remembers desktop-mode. P4: open Google's Safe Browsing test URL → our styled warning; a consent-banner site shows no banner. P5: autofill a login with your password manager; back up, wipe, restore; fresh-install onboarding imports Chrome bookmarks. P6: offline → play the asteroid game; overall polish.

## 8. Deferred decisions (recorded)

- Tab-content search (B2 extension) → v5 with omnibox power mode.
- Encrypting the J1 backup file → decide during P5 planning (adds a password prompt; may hurt usability).
- G5 video assistant explicitly punted to v5 (G3+G4 cover the core need).
