# Design Spec — "Browse" Mobile Browser (working name)

**Date:** 2026-07-10
**Status:** Approved by owner
**Platform:** Android (native, Kotlin, Jetpack Compose)
**Engine:** Android System WebView (Chromium engine provided by the OS)

---

## 1. Purpose & Goals

A polished, full-featured mobile web browser for Android, in the spirit of Jio Browser / Kiwi Browser, built as a **portfolio and learning project**.

**Success criteria:**

- A browser the owner genuinely uses daily on their own phone.
- Release-quality polish: proper icon, animations, empty states, error screens — Play-Store-ready standard even if never published.
- The owner (a beginner in Kotlin/Android) understands every major component because key logic was written or decided by them.
- Each build phase ends with a working, demonstrable increment.

**Non-goals (explicitly out of scope):**

- iOS support.
- Chrome/Firefox extension support (requires GeckoView or a Chromium fork — see Decision Record below).
- Custom engine modifications (codecs, new web APIs).
- AI assistant features (considered, deferred; possible future phase).
- Sync/accounts/cloud backend. Everything is on-device.

---

## 2. Decision Record — Engine Choice

Three approaches were evaluated:

| | A: System WebView | B: GeckoView | C: Chromium fork |
|---|---|---|---|
| Engine | Chromium, provided & updated by Android/Google | Gecko, bundled in APK (~75 MB) | Chromium, owned/compiled by us |
| Extensions | No | Yes | Yes |
| Beginner-feasible | Yes | Painful | No |
| Engine security updates | Google's job | Our job | Our job (constant) |
| Learning transferability | Full (core Android) | Partial (Mozilla APIs) | Minimal |

**Decision: Approach A — System WebView.**
Rationale: Chrome-quality rendering with zero engine maintenance; every planned feature is achievable; smallest app (~15 MB); best-documented path; all learning transfers to general Android development. B is a candidate for a future "v2" project. C is not feasible for a solo developer.

---

## 3. Feature Scope

### Core (assumed baseline)
- Address bar with combined URL/search input (configurable search engine)
- Multiple tabs with previews, create/switch/close, state restoration after process death
- Bookmarks (add, list, search, delete)
- History (grouped by day; open, delete one, clear all)
- Downloads (background, progress notification, open completed files)
- Settings screen

### Signature features
1. **Ad blocker** — EasyList-style filter lists parsed into a fast matcher; every WebView network request checked via `shouldInterceptRequest`; per-site allow toggle; blocked-request counter.
2. **Privacy suite** — incognito tabs (no cookies persisted, no history, no cache), cookie controls, JavaScript toggle, "clear browsing data," HTTPS enforcement.
3. **Media & downloads plus** — full download manager (pause/resume/retry, notifications, file handling), video detection on pages with download option where legally appropriate.

### Screens (7)
1. **Browser** — address bar (top), WebView, progress bar, bottom toolbar (back / forward / tab count / menu)
2. **Tab switcher** — grid of tab cards with previews; normal + incognito sections
3. **Home page** — new-tab page: search box + speed-dial favorites grid
4. **Bookmarks**
5. **History**
6. **Downloads**
7. **Settings** — search engine, ad-block toggle + stats, privacy controls, theme

---

## 4. Architecture

**Pattern:** MVVM, single-activity, Jetpack Compose UI. Three layers; each layer depends only on the layer below.

```
┌─────────────────────────────────────────────┐
│  UI LAYER (Jetpack Compose)                 │
│  Screens + ViewModels                       │
├─────────────────────────────────────────────┤
│  BROWSER CORE (plain Kotlin)                │
│  TabManager · AdBlockEngine                 │
│  PrivacyManager · DownloadCoordinator       │
├─────────────────────────────────────────────┤
│  DATA LAYER                                 │
│  Room DB (bookmarks, history, downloads)    │
│  DataStore (settings) · filter-list files   │
└─────────────────────────────────────────────┘
```

### Core components (interface-first definitions)

| Component | One-line contract | Key dependencies |
|---|---|---|
| **TabManager** | Owns the ordered list of open tabs; create/switch/close/restore. Each tab = one WebView + metadata (URL, title, incognito flag). | PrivacyManager (to configure new WebViews) |
| **AdBlockEngine** | `shouldBlock(requestUrl, pageUrl): Boolean` — answers for every network request. Loads and parses filter lists at startup. | Filter-list files (data layer) |
| **PrivacyManager** | Configures each WebView per policy (normal vs incognito); implements "clear browsing data." | Settings (DataStore) |
| **DownloadCoordinator** | Receives download requests from WebView; runs them in background with notifications; records to DB. | Room DB, Android notification/service APIs |

Consumers use these contracts only; internals can change freely.

### Data model (Room entities)
- `Bookmark(id, title, url, createdAt)`
- `HistoryEntry(id, title, url, visitedAt)`
- `DownloadEntry(id, fileName, url, filePath, status, totalBytes, downloadedBytes, createdAt)`
- Settings in Jetpack DataStore (search engine, adBlockEnabled, JS enabled, cookie policy, theme).
- Incognito data is never written to any of the above.

---

## 5. Data Flow (canonical example)

Typing `bbc.com` and pressing Go:

1. Address bar → **BrowserViewModel** receives text.
2. ViewModel classifies input: URL vs search query.
3. ViewModel → **TabManager**: load URL in active tab.
4. WebView loads; every sub-request passes through `shouldInterceptRequest` → **AdBlockEngine** verdict.
5. Progress callbacks → ViewModel state → progress bar.
6. On page finish: record `HistoryEntry` — skipped if incognito (**PrivacyManager** policy).

All features follow this shape: UI event → ViewModel → core component → data layer.

---

## 6. Error Handling

| Failure | Behavior |
|---|---|
| No network / unreachable host | Friendly in-app error page with Retry — never a blank screen |
| SSL certificate error | Full warning screen; **never silently proceed** (security rule) |
| WebView renderer crash | Recreate tab and reload automatically; app must not crash |
| Download failure (network/storage) | Notification shows failed state with retry action |
| Malformed filter rule | Skip rule, log, continue loading remaining rules |

---

## 7. Testing Strategy

- **Unit tests** (JVM, no device): AdBlockEngine matching & filter parsing; URL-vs-search classification; download state transitions.
- **Database tests:** Room DAOs for bookmarks/history/downloads.
- **Manual checklist per phase** — a phase is done only when its checklist passes (e.g., Phase 2: tabs survive process death).
- Tests are written within each phase, not deferred to the end.

---

## 8. Build Phases

| Phase | Deliverable | Definition of done |
|---|---|---|
| 1. Walking skeleton | Address bar + WebView + back/forward/reload + progress bar | Can browse any site usably |
| 2. Real browser | Tabs, bookmarks, history, basic downloads, settings, home page | Tabs survive app restart; data persists |
| 3. Privacy suite | Incognito, cookie/JS controls, clear data, HTTPS enforcement | Incognito leaves zero traces on device |
| 4. Ad blocker | Filter lists, interception, per-site toggle, blocked counter | EasyList loads; ads visibly blocked on test sites |
| 5. Media & downloads plus | Full download manager, notifications, video detection | Pause/resume works; downloads survive app close |
| 6. Polish & release | Icon, animations, empty/error states, store-listing assets | Passes release checklist end-to-end |

Phases are strictly ordered; each ends demonstrable.

---

## 9. Working Agreement (learning mode)

The owner is a beginner in Kotlin/Android learning through this project. Scaffolding and boilerplate are generated; **meaningful decisions and small core-logic pieces (5–10 lines) are written by the owner** — e.g., URL-vs-search rule, incognito policy details, SSL error UX. Every phase includes explanations of *why*, not just *what*.

---

## 10. Open Items

- **App name:** working name "Browse"; final name to be chosen before Phase 6.
- **Search engine default:** decided in Phase 2 settings work.
- **Filter list source & update cadence:** decided at Phase 4 start (candidate: EasyList + EasyPrivacy, bundled snapshot + optional refresh).
