# Roadmap & backlog

> **Last updated:** 2026-08-14, after v6.16.
> This is the single list of what is *not* done. When you ship an item, delete it here and add it to
> [../CHANGELOG.md](../CHANGELOG.md) in the same commit.

## ⚠️ Verify before you build

Items here were transcribed from "Deferred:" notes written when each feature shipped. **Those notes go
stale — a later version often implements them without clearing the note.** While compiling this list on
2026-08-14, five items turned out to be already shipped (see [Already shipped](#already-shipped-do-not-rebuild)).

**Grep for the *behaviour*, not the symbol name you expect.** Find-in-page match counting was nearly
rebuilt at v6.12 because it lives in a `setFindListener` lambda, not a method named
`onFindResultReceived`.

Items marked **⚠ unverified** were carried over from old notes and not re-checked on 2026-08-14. Verify
first.

---

## Tier 1 — Correctness & privacy gaps

Defects and broken promises, not features.

| Item | Detail | Source |
|---|---|---|
| **`reading_list` is not Orbit-scoped** | Saved articles and their offline bodies are global. Decide explicitly: scope it (as `closed_tabs` was in v6.16), or document it as intentionally device-level. Currently it is neither. | Found 2026-08-14 |
| **User-Agent version drift** | Hardcoded in three places and out of sync: `Andromeda/5.9` in `SuggestionEngine`, `Andromeda/3.2` in `FeedRepository` **and** `WeatherRepository`. Extract one constant from `BuildConfig`. | v5.9 |
| **Bundle the full Mozilla PSL** | The curated suffix set can over-match between tenants of a SaaS provider it doesn't list. Bounded today by HTTPS + explicit tap + `@host` display, but the real fix is the full list. [ADR-0011](adr/0011-curated-public-suffix-list.md) | v6.5 |

## Tier 2 — Features

| Item | Detail | Source |
|---|---|---|
| **Translate: re-run on new content** | Translation snapshots the DOM at trigger time, so SPA / infinite-scroll pages (X, Reddit, news) leave later content untranslated. Options: a `MutationObserver`, or an explicit "translate new content" action. Also lift or paginate the 2,000-node cap. **Highest user-visible impact item on this list.** | v6.1 |
| **Passwords: per-reveal re-auth** | The Passwords *screen* is already gated (see below). A stricter option is re-authenticating per individual reveal. Nice-to-have, not a gap. | — |
| **Player: queue reorder** | Queue is auto-built by policy; no manual reordering. | v6.0 |
| **Player: sidecar subtitles** | Embedded tracks are selectable; an external `.srt` beside a video is not. | v6.0 |
| **Player: equaliser** | Audio EQ presets. | v6.0 |
| **Player: horizontal-drag scrub** | Deliberately deferred — the slider is the scrub control and the gesture-conflict risk is unverified. | v6.0 |
| **Shake sensitivity calibration** | The 2.7 g / 3-jolt threshold is fixed; sensor characteristics vary by device. | v6.2 |
| **QR: scan from a gallery image** ⚠ unverified | Camera-only, believed still open (generation shipped in v5.4). | v5.2 |
| **QR: non-QR barcodes, Wi-Fi auto-join** ⚠ unverified | ZXing supports more symbologies; Wi-Fi payloads are parsed but possibly not joined. | v5.2 |
| **Video capture from the file chooser** ⚠ unverified | `FileUploads.captureMode` handles explicit *image* types; video capture is believed missing. | v5.x |
| **File upload: drag-and-drop** ⚠ unverified | No drop-target support. | v5.x |
| **Per-site "always open app X"** ⚠ unverified | External-link handling asks every time. | v4.9 |
| **Mid-flow permission request / photo recovery** ⚠ unverified | Camera permission requested mid-flow, and `onSaveInstanceState` photo recovery after process death. | v5.3 |

## Tier 3 — Size & performance

| Item | Detail |
|---|---|
| **Trim adblock assets (~5.1 MB)** | `easylist.txt` 2.08 MB · `easyprivacy.txt` 1.47 MB · `annoyance-cookies.txt` 0.86 MB · `adguard-mobile.txt` 0.49 MB · `peter-lowe.txt` 0.09 MB. Options: ship compressed and inflate on first run, or precompile to a binary index at build time. **Prefer compression/precompilation over pruning** — pruning rules weakens blocking. This is the next-largest size lever after the ML Kit libraries. |
| **App Bundle (`.aab`) for Play** | Would cut the per-device download further *and* restore 32-bit support, but requires Play distribution and permanent Play App Signing. [ADR-0010](adr/0010-arm64-only-apk.md) |
| **`BrowserViewModel` is 2,568 lines** | Extract cohesive delegates (tabs, downloads, reader) that the ViewModel composes — **without** letting screens reach data directly. [ADR-0002](adr/0002-single-viewmodel-stateflow.md) |

## Tier 4 — Polish

Small, low-risk warm-up items. ⚠ All carried from v3-era notes; verify before starting.

- Tab list rows are too tall
- A reopened tab shows a placeholder thumbnail until it repaints
- Bulk-share from the tab switcher can include an incognito URL
- Selecting only incognito tabs can create an empty group
- Group header should toggle collapse on a whole-row tap, not only the chevron
- The move-to-group submenu still lists the group the tab is already in
- Bookmark search query is lost on process death (`rememberSaveable`)

## Already shipped — do not rebuild

Verified against code on 2026-08-14. These appeared in old deferral notes and are **done**.

| Was listed as deferred | Actually shipped in | Evidence |
|---|---|---|
| `closed_tabs` crossing Orbits | **v6.16** | `closed_tabs.orbitId` (migration 21 → 22), per-Orbit `observeRecent`/`trimTo`/`deleteForOrbit` |
| Biometric gate on passwords | **v5.1** | `_passwordsLocked` defaults to `true`; `LockGate` + `promptPasswordsUnlock()` before `PasswordsScreen` ([MainActivity.kt:1116](../app/src/main/java/com/udaytank/browse/MainActivity.kt#L1116)) |
| Manual add / edit credentials | **v5.1** | `onAddCredential`, `onEditCredential` in `BrowserViewModel` |
| QR code generation | **v5.4** | `browser/QrGenerate.kt`, `ui/components/QrShareSheet.kt` |
| `capture` attribute for uploads | **v5.3** | `FileUploads.captureMode(..., captureEnabled)`, `params.isCaptureEnabled` |
| Per-Orbit downloads | **v5.5** | `downloads.orbitId` (migration 18 → 19) |
| Cross-subdomain password fill | **v6.5** | `CredentialHostMatch` |
| Black Hole gesture trigger | **v6.2** | `ShakeDetector` |
| Find-in-page match count | early | `setFindListener` → `onFindResult` → `FindBar` |
| Wi-Fi-only translate model download | **v6.4** | `translateWifiOnly` setting |
| Player sleep timer | **v6.3** | `media/SleepTimer.kt` |

## Deliberately not doing

Recorded so they are not revisited by accident.

| Not doing | Why |
|---|---|
| WebExtensions / uBlock Origin | Impossible on System WebView — [ADR-0001](adr/0001-system-webview.md) |
| `$websocket` / `$ping` filter rules | `shouldInterceptRequest` never sees them |
| Procedural cosmetic filters (`:has-text`) | No extension APIs on WebView |
| Guaranteed zero YouTube in-stream ads | Server-stitched ads are a hard limit for every WebView browser |
| Cloud translation | Would transmit page content to a third party — [ADR-0009](adr/0009-mlkit-on-device-translate.md) |
| Persistent `@JavascriptInterface` | Standing attack surface on every page — [ADR-0013](adr/0013-no-persistent-js-interface.md) |
| Bundling FFmpeg | +20–40 MB and licensing obligations — [ADR-0008](adr/0008-media3-hybrid-player.md) |
| Analytics / crash reporting / accounts | No backend, by design |

## Verification debt

Releases are staged as APKs and installed manually by the owner. **v6.0 is the last release with
recorded on-device confirmation** (2026-07-22). v6.1 → v6.15 were staged, but their on-device
verification is not recorded. Per-version checklists live in each [spec](superpowers/specs/).
