# The Browser Landscape 2026 — Full Research Report

**Project:** Andromeda (v3 discovery)
**Date:** 2026-07-12
**Purpose:** Owner-requested research before v3 scoping: catalogue what every major browser does, how features are categorized and placed in the UI, and measure exactly where Andromeda v2.0 stands. Detailed per-browser dossiers live in [`browsers/`](browsers/) — this document is the synthesis.

**Browsers covered:** Chrome, Firefox, Edge, Samsung Internet, Brave, Opera/Opera GX, Vivaldi, DuckDuckGo, Kiwi (legacy), the AI-first wave (Comet, Arc Search, Dia, Atlas), plus the desktop feature superset (Chrome/Firefox/Edge desktop) and a dedicated cross-browser UI/UX structure study.

---

## 1. Executive summary

1. **The market is a pyramid.** Chrome (~65% mobile share) wins on defaults + Google integration, not feature count. Samsung Internet (~2.8%) is the one-handed UX benchmark. Brave is the ad-blocking benchmark. Firefox is the extensions/privacy benchmark. Opera is the "everything built-in" benchmark. Every successful non-Chrome browser survives by being *the best in the world at one thing* — nobody beats Chrome at being Chrome.
2. **2026's battleground is AI** — but the evidence says summarize/ask-the-page are the daily-driver winners, while agentic "browse for me" remains fragile and has unsolved prompt-injection security problems. OpenAI killed its own browser (Atlas) after 9 months.
3. **The UI consensus has settled**: bottom-anchored unified toolbar, grid tab switcher with a private pane, icon-row + short-list menus, editable shortcut-grid home pages (feeds are the most complained-about feature in the industry), quieter permission chips, and gestures as accelerators-not-requirements. **Andromeda v2's Orbit design already matches most of this consensus** — bottom Command Bar, thumbnail grid switcher, no feed. Our structure is modern; our *depth* is not.
4. **WebView draws a hard line.** Extensions, engine-level fingerprint defense, DNS-over-HTTPS choice, and uBlock-parity blocking are engine-only — permanently out of reach without a Chromium fork. But the research also identified the open lanes that are 100% buildable on WebView and under-served industry-wide: **multi-profile/containers, desktop-grade tab management, a real download engine, reading list + offline, per-site settings memory, omnibox power features, and assistant-tier AI.**
5. **Measured honestly against the ~200-feature universe in this report, Andromeda v2 covers roughly 25–30% of what a 2026 daily driver ships** — better than the owner's felt "5%," but the missing 70% includes the features users touch most (passwords/autofill, translate, tab groups, sync, PDF viewer, TTS). Section 6 maps every gap.

---

## 2. The feature taxonomy — what a browser IS in 2026

Synthesized across all ten dossiers, every browser feature falls into 16 categories. This is the master checklist for any roadmap:

| # | Category | Table-stakes examples | Benchmark browser |
|---|---|---|---|
| 1 | **Navigation & address bar** | Combined omnibox, suggestions, voice, QR scan, position choice | Chrome (omnibox intelligence) |
| 2 | **Tabs & tab management** | Grid switcher, groups, search, recently-closed, inactive-tab archiving, pinning | Opera (3 layouts, Islands, 100-undo) / Chrome (sync + declutter) |
| 3 | **Home / new-tab page** | Editable shortcut grid, optional (off-by-default) feed, wallpapers | Brave (privacy stats) / Opera (speed dial) |
| 4 | **Search & discovery** | Engine choice, custom engines, suggestions, visual/voice search | Chrome (Lens) / Brave (own index) |
| 5 | **Privacy & security** | Tracker blocking, private mode + biometric lock, HTTPS-only, Safe Browsing, cookie controls, permission management | Firefox (ETP/TCP) / Brave (farbling) |
| 6 | **Ad/content blocking** | Filter lists, cosmetic filtering, per-site toggle, element zapping, custom lists | **Brave (Rust engine, uBO lists, scriptlets)** |
| 7 | **Passwords & autofill** | Password manager, generator, breach alerts, passkeys, address/payment autofill | Chrome (GPM) / Samsung (Knox vault) |
| 8 | **Downloads & media** | Pause/resume, parallel, background video, PiP, video assistant, offline pages | Samsung (Video Assistant) / Brave (background YouTube) |
| 9 | **Reading & content** | Reader mode, reading list, full-page translate, TTS/read-aloud, PDF viewer, find-in-page | Edge (Immersive Reader + Read Aloud + Translate) |
| 10 | **Customization & appearance** | Theme, bar position, toolbar/menu customization, force-dark, wallpapers | **Samsung (drag-and-drop 7-slot toolbar + menu)** |
| 11 | **Sync & account** | Bookmarks/history/tabs/passwords sync, E2E encryption, send-to-device, no-account pairing | Brave/DDG (accountless QR chains) / Firefox (E2E by design) |
| 12 | **AI features** | Summarize, ask-the-page, AI omnibox, cross-tab reasoning, voice; (agentic = frontier) | Comet (reference) / Edge (deepest incumbent) |
| 13 | **Gestures & shortcuts** | Bar-swipe tab switch, pull-to-refresh, long-press menus, app shortcuts | Opera GX (FAB + haptics) |
| 14 | **Performance & data** | Preloading, tab sleeping, data saver, battery-friendly dark | Opera Mini (compression) |
| 15 | **Extensions / scripting** | Full extensions (engine-only), content-blocker plugins, user scripts | Firefox (open ecosystem) — ⛔ engine-bound |
| 16 | **Accessibility & extras** | Text scaling, force-zoom, high contrast, TalkBack, error-page delight, PWA install | Edge (a11y) / Chrome (dino, WebAPK) |

---

## 3. UI/UX structure — the industry consensus (and where features live)

The full study is in [`browsers/09-uiux-patterns.md`](browsers/09-uiux-patterns.md). The 15 evidence-backed rules, condensed:

**Layout**
1. Unified address bar + toolbar at the **bottom** (thumb-zone research; Safari/Arc/Comet default bottom; Chrome/Edge/Firefox/Samsung all offer it). Dock it — never float. Never split bar and toolbar across edges. *(Andromeda: ✅ already bottom-docked.)*
2. Persistent toolbar = address field + max 4 buttons (tabs, menu, + up to two more), ideally with **one user-configurable slot** (the Chrome/Vivaldi/Firefox compromise; Samsung's full drag-and-drop reorder is the power-user gold standard).

**Tabs**
3. 2-column grid of preview cards, list-view option, swipe-to-close, segmented private pane, **theme-shift for private mode** so the mode is legible at a glance.
4. Tab count badge, two characters max — Chrome shows ":D" at 100+ (delight from constraint).

**Menus** — the three-way division of labor every browser obeys:
5. **Menu** = weekly actions (new tab, history, downloads, bookmarks, share, find, translate, desktop site). Consensus shape: icon action-row on top + short grouped list below (Chrome M3 and Firefox 141 converged independently). Samsung/Edge use customizable icon grids instead.
6. **Settings** = rarely-visited preferences, 4 shallow groups: Account → Basics (search engine, bar position, theme) → Privacy & security → Advanced. Settings search box appears once settings get big (Samsung v27).
7. **Long-press** = ALL contextual actions (links, images, selection, address bar, home tiles). Never in the main menu.

**Home page**
8. Search box + user-editable shortcut grid. **No feed by default** — content feeds are the most complained-about browser feature industry-wide (Edge MSN, Chrome Discover, Firefox Pocket). User-editable = accepted; algorithm-pushed = resented.

**Gestures** (standard vocabulary users already know)
9. Pull-to-refresh · horizontal bar-swipe = tab switch · card swipe-to-close · long-press everywhere · never fight Android's predictive back. Every gesture must have a visible-control equivalent. *(Andromeda: ✅ bar-swipe + swipe-up already shipped.)*

**Flows**
10. Onboarding ≤3 skippable screens: value pitch → import from old browser → default-browser ask. Defer sign-in and permissions to contextual moments.
11. Permission prompts: non-modal chip anchored to the address bar (Chrome found ~85% of prompts get ignored — quiet UI won).
12. Error pages: friendly illustration + plain cause + retry + one moment of delight (Chrome's dino = 270M players/month). *(Andromeda: ✅ "Lost in space" page; 🟡 no delight/game yet.)*

---

## 4. Cross-browser highlights worth stealing (per browser)

- **Chrome:** tab auto-archiving after N days; tab-group sync; omnibox action chips; Listen-to-this-page; quieter permission chip; incognito biometric lock *(we have this!)*.
- **Firefox:** Collections (save tab-sets as named projects); on-device translation; AI Controls hub (every AI feature behind one master switch — trust-building); top/bottom bar choice in onboarding.
- **Edge:** Read Aloud + Immersive Reader + Translate as one reading stack; customizable menu; lesson in what NOT to do (feature churn — Collections/Drop/Sidebar all killed; bloat reputation).
- **Samsung Internet:** drag-and-drop toolbar + menu customization (7 slots); Secret mode with its own anti-tracking level; Video Assistant; Quick Exit panic button; settings search.
- **Brave:** Shields panel anatomy (per-site toggle + counters + advanced expander); aggressive/standard block levels; Forgetful Browsing (auto-logout per site); element zapper; NTP privacy stats as brand reinforcement.
- **Opera:** three tab-switcher layouts; Tab Islands auto-grouping; 100-tab undo; cookie-banner auto-dismisser; Flow (QR-paired, accountless device-to-device sharing).
- **Vivaldi:** Notes in the browser; full-page screenshot capture; per-site blocking levels; "AI-free" as an actual marketed feature.
- **DuckDuckGo:** Fire Button (one-tap wipe with fireproofed exceptions) — the most memorable privacy ritual in any browser; proof a WebView browser can be a serious product.
- **Comet/AI wave:** bottom assistant drawer over the page (not a separate screen); long-press link → "Ask AI"; colored screen border + step log during agent runs; Library of past AI conversations.

---

## 5. The WebView boundary — honest engineering limits

From the desktop-superset analysis ([`browsers/10-desktop-superset.md`](browsers/10-desktop-superset.md)):

**⛔ Impossible without switching to a Chromium fork (do not promise):**
- Real WebExtensions support (Kiwi/Firefox model) — nearest substitute: user-script injection, positioned as "scripting," never "extensions"
- uBlock-Origin-parity blocking (response-body filtering, full webRequest)
- Engine-level fingerprint resistance (Brave farbling, Firefox RFP) — don't market security we can't implement
- DNS-over-HTTPS provider choice, ECH, network-stack controls
- Full in-browser DevTools (remote chrome://inspect works; injected Eruda console possible)

**✅ Fully buildable on WebView, and under-served industry-wide (the open lanes):**
1. **Multi-profile / containers** — `androidx.webkit` ProfileStore (we already use it for incognito!); almost no mobile browser has real profiles. Firefox containers exist nowhere on mobile.
2. **Desktop-grade tab power** — groups, pinning, tab search, auto-archive, workspaces: all chrome-layer UI.
3. **Own download engine** — parallel segmented, pause/resume, scheduling; beats Chrome Android easily.
4. **Reading list + offline reader** — Readability-extraction + stored clean HTML + Android TTS read-aloud. Pocket's death left a vacuum.
5. **Per-site settings memory** — zoom, desktop mode, dark mode, JS per-origin. Chrome barely does this.
6. **Full-page screenshot + annotation.**
7. **In-app PDF viewer** (`PdfRenderer`) — most Android browsers punt to external apps.
8. **Brave-lite blocking upgrades** — scriptlets, per-site levels, element zapper, custom filter lists (80% of Brave, honestly labeled).
9. **Omnibox power** — custom search keywords, @tabs/@history scoping, inline calculator, quick actions. No mobile browser does this well.
10. **Assistant-tier AI** — summarize/ask-the-page via cloud API or on-device Gemini Nano (ML Kit GenAI). $0.001–0.01/page with efficient models; hybrid on-device+cloud is the 2026 pattern.
11. **Translate** — ML Kit on-device translation + DOM rewriting (imperfect on dynamic pages, works).
12. **Media polish** — PiP for fullscreen video, MediaSession notification, background audio.

---

## 6. Andromeda v2.0 gap matrix — "the 5%" measured

Legend: ✅ have · 🟡 partial/basic · ❌ missing (buildable) · ⛔ engine-impossible on WebView

### 6.1 Navigation & address bar
| Feature | Status | Notes |
|---|---|---|
| Combined omnibox + suggestions (history/bookmarks/web) | ✅ | v2 P3 |
| Bottom-anchored bar | ✅ | Orbit Command Bar — matches 2026 consensus |
| Top/bottom position **choice** | ❌ | consensus says offer the toggle |
| Voice search | ❌ | RecognizerIntent — easy |
| QR scanner in bar | ❌ | deferred from v2; ML Kit |
| Omnibox quick actions / @-scoping / calculator | ❌ | open lane, no one does it well on mobile |
| Copy-URL / share from bar long-press | 🟡 | share exists in menu |

### 6.2 Tabs
| Feature | Status | Notes |
|---|---|---|
| Grid thumbnail switcher | ✅ | v2 P2 |
| Bar-swipe tab switch + swipe-up switcher | ✅ | v2 P2 |
| Incognito pane separation + theme shift | ✅ | negative-id design |
| Tab groups (named/colored) | ❌ | now table stakes (Chrome/Edge/Samsung/Firefox/Opera all have it) |
| Tab search | ❌ | easy win |
| Recently-closed / undo close | ❌ | Opera keeps 100 |
| Inactive-tab auto-archive | ❌ | Chrome's declutter |
| Pinned tabs / tab lock | ❌ | |
| List-view option | ❌ | consensus: grid default + list option |
| Multi-select bulk actions | ❌ | |

### 6.3 Home / new-tab
| Feature | Status | Notes |
|---|---|---|
| Clean search-first home, no feed | ✅ | matches anti-feed consensus |
| Bookmarks speed dial | 🟡 | v1; not freely editable/orderable shortcuts |
| Editable shortcut grid (pin/reorder/remove) | ❌ | the accepted NTP pattern |
| Wallpapers / home customization | ❌ | |
| Privacy-stats block (Brave-style) | ❌ | we already count blocked ads — surfacing = free brand value |

### 6.4 Privacy & security
| Feature | Status | Notes |
|---|---|---|
| Incognito + biometric lock | ✅ | v2 P5 — Chrome-parity |
| ProfileStore cookie isolation | ✅ | feature-gated |
| HTTPS-only mode | ✅ | v2 P5 |
| Site permission prompts | ✅ | v2 P5 |
| JS/cookie toggles, clear data, SSL warning | ✅ | v1 |
| Safe Browsing | ❌ | `setSafeBrowsingEnabled` — nearly free win |
| Tracker blocking as distinct tier + per-site dashboard | 🟡 | ad block exists; no tracker-specific lists/panel |
| Cookie-banner auto-dismiss | ❌ | Opera/Firefox/Brave have it; filter-list based |
| Forgetful browsing (per-site auto-forget) | ❌ | Brave's; CookieManager + storage clear |
| Fire-button style panic wipe / Quick Exit | ❌ | DDG/Samsung ritual feature |
| Global Privacy Control header | ❌ | header injection — trivial |
| Fingerprint resistance | ⛔ | engine-bound — do not market |
| DoH provider choice | ⛔ | system Private DNS only |
| Multi-profile / containers | ❌ | **ProfileStore makes us uniquely positioned — top differentiator candidate** |

### 6.5 Ad/content blocking
| Feature | Status | Notes |
|---|---|---|
| Domain filter lists (EasyList) + exceptions | ✅ | v1 P4 |
| Generic cosmetic ## rules | ✅ | v2 P4 |
| Per-site allowlist + counter | ✅ | |
| Path/regex rules, $options | ❌ | ad-block v2 backlog |
| Per-site blocking levels (standard/aggressive/off) | ❌ | Brave/Vivaldi pattern |
| Element zapper (tap-to-hide) | ❌ | Brave was first on mobile — high wow |
| Custom filter-list subscription + user rules | ❌ | |
| Scriptlet injection (anti-adblock defense) | ❌ | partial feasibility |
| Filter-list auto-update | ❌ | ours is a bundled snapshot |
| uBlock parity / response filtering | ⛔ | engine-bound |

### 6.6 Passwords & autofill — **the biggest daily-driver hole**
| Feature | Status | Notes |
|---|---|---|
| Password save/fill | ❌ | Android Autofill framework integration is the pragmatic path |
| Passkeys | ❌ | Credential Manager API |
| Address/payment autofill | ❌ | |
| Breach alerts | ❌ | HIBP-style API |

### 6.7 Downloads & media
| Feature | Status | Notes |
|---|---|---|
| System DownloadManager + downloads screen | ✅ | v1 P5 |
| Long-press image/link download | ✅ | |
| Own engine: pause/resume/parallel/schedule | ❌ | deferred twice; fully feasible |
| In-app file previews | ❌ | |
| Background video/audio playback | ❌ | Brave's biggest Android draw |
| Picture-in-Picture | ❌ | needs onShowCustomView handling |
| Video assistant (floating controls, gestures) | ❌ | Samsung signature |
| Offline pages (save full page) | ❌ | saveWebArchive |

### 6.8 Reading & content
| Feature | Status | Notes |
|---|---|---|
| Reader mode | ✅ | v2 P4 (minor leakage known) |
| Find in page | ✅ | v2 P3 |
| Desktop-site toggle | ✅ | v2 P3 |
| Reading list (save-for-later + offline) | ❌ | Pocket vacuum; pairs with our reader |
| Full-page translate | ❌ | ML Kit on-device |
| TTS / read-aloud | ❌ | Android TTS + our reader extraction |
| PDF viewer in-app | ❌ | industry-wide mobile gap |
| Print / save-as-PDF | ❌ | WebView print adapter — easy |
| Per-site zoom/text-size memory | ❌ | |

### 6.9 Customization
| Feature | Status | Notes |
|---|---|---|
| Light/dark/system theme | ✅ | |
| Force-dark websites | ✅ | v2 P4 |
| Own design language (Orbit) | ✅ | brand asset |
| Toolbar customization (even 1 slot) | ❌ | consensus compromise |
| Menu customization | ❌ | Samsung/Brave/Edge pattern |
| Text scaling / force-zoom | ❌ | accessibility table stakes |
| High contrast | ❌ | |

### 6.10 Sync & account
| Feature | Status | Notes |
|---|---|---|
| Any sync | ❌ | needs our own backend — v3+ decision; accountless QR-pair (Brave/DDG model) is the trust-friendly route |
| Bookmark HTML import/export | ✅ | v2 P6 — the manual fallback |
| Backup/restore to file | ❌ | deferred from v2; no backend needed |
| Send-to-device / Flow-style sharing | ❌ | backend-dependent |

### 6.11 AI
| Feature | Status | Notes |
|---|---|---|
| Summarize page | ❌ | assistant tier — feasible on-device (Gemini Nano flagships) or cloud |
| Ask-the-page | ❌ | |
| AI omnibox answers | ❌ | |
| Agentic browsing | ❌ | industry-wide fragile + prompt-injection unsolved — not recommended |
| AI master off-switch (Firefox AI Controls) | n/a | if we ever add AI, ship the off-switch with it |

### 6.12 Extras
| Feature | Status | Notes |
|---|---|---|
| Friendly error page | ✅ | "Lost in space" |
| Offline-game / delight moment | ❌ | dino lesson: cheapest brand surface |
| Onboarding flow | ❌ | we have none; ≤3 screens + import + default-ask |
| Import from other browser at first run | ❌ | only manual HTML import |
| PWA / add-to-home-screen | ❌ | shortcut-level feasible |
| Full-page screenshot + annotate | ❌ | Vivaldi/Edge pattern |
| Settings search | ❌ | once settings grow |
| App shortcuts (launcher long-press) | ❌ | trivial manifest addition |

### 6.13 Scorecard

| Category | Coverage vs 2026 daily-driver bar |
|---|---|
| Navigation & bar | 🟡 ~55% |
| Tabs | 🟡 ~40% (structure ✅, management depth ❌) |
| Home | 🟡 ~40% |
| Privacy & security | 🟡 ~55% (strong for our size; missing Safe Browsing, banner-blocker, profiles) |
| Ad blocking | 🟡 ~45% (domain+cosmetic done; levels/zapper/custom missing) |
| **Passwords & autofill** | ❌ **0% — single biggest gap** |
| Downloads & media | 🟡 ~30% |
| Reading & content | 🟡 ~35% |
| Customization | 🟡 ~45% |
| **Sync** | ❌ ~5% (manual export only) |
| AI | ❌ 0% (deliberate, per owner: v3 = power browser) |
| Extensions | ⛔ engine-bound (user-scripts lane open) |
| Accessibility | ❌ ~15% |
| Extras/delight | 🟡 ~30% |

**Overall: ~25–30% of the 2026 daily-driver feature bar — with the right skeleton.** The v2 architecture (bottom bar, WebViewHolder, ProfileStore, filter engine, Room/DataStore) is exactly the right foundation for the missing 70%; almost nothing needs re-architecting.

---

## 7. Strategic readout for v3 (input to discovery — decisions belong to the owner)

Ranked by (user-felt impact × feasibility on WebView × differentiation):

**Tier 1 — daily-driver credibility (the features users touch every session):**
1. Tab management depth: groups, search, recently-closed undo, list view, bulk actions, auto-archive
2. Passwords & autofill (Android Autofill + passkeys via Credential Manager)
3. Own download engine (parallel, pause/resume, previews) — deferred twice, now due
4. Text scaling / force-zoom + per-site settings memory
5. Safe Browsing toggle + cookie-banner auto-dismiss + editable home shortcut grid

**Tier 2 — the "never leave" layer:**
6. Reading stack: reading list + offline + translate (ML Kit) + read-aloud (TTS) on top of our reader
7. Ad-block v2: per-site levels, element zapper, custom lists, auto-updating lists, path rules
8. Media: background playback + PiP (Brave's single biggest Android draw)
9. PDF viewer + print/save-as-PDF
10. Backup/restore to file (no backend), onboarding + import flow, QR scanner, voice search

**Tier 3 — differentiators nobody on mobile does well:**
11. **Profiles/containers via ProfileStore** — genuinely rare; we already ship the API for incognito
12. Omnibox power features (@tabs/@history, custom search keywords, calculator)
13. Toolbar/menu customization (start with one configurable slot)
14. Full-page screenshot + annotate; Quick Exit/panic wipe; privacy stats on home
15. Sync (accountless QR-chain model) — biggest lift, defer until the above land

**Explicitly not for v3:** extensions (⛔ engine), fingerprint-resistance claims (⛔), agentic AI (industry-proven fragile + insecure), content feeds (industry-proven resented), crypto/rewards (bloat lesson from Brave/Opera reviews).

---

## 8. Sources & method

Ten parallel research agents (July 12, 2026), each web-searching vendor release notes, support docs, UX teardowns, and 2024–2026 tech press. Full per-browser detail with source links: [`browsers/01-chrome.md`](browsers/01-chrome.md) · [`browsers/02-firefox.md`](browsers/02-firefox.md) · [`browsers/03-edge.md`](browsers/03-edge.md) · [`browsers/04-samsung-internet.md`](browsers/04-samsung-internet.md) · [`browsers/05-brave.md`](browsers/05-brave.md) · [`browsers/06-opera.md`](browsers/06-opera.md) · [`browsers/07-vivaldi-duckduckgo-kiwi.md`](browsers/07-vivaldi-duckduckgo-kiwi.md) · [`browsers/08-ai-browsers.md`](browsers/08-ai-browsers.md) · [`browsers/09-uiux-patterns.md`](browsers/09-uiux-patterns.md) · [`browsers/10-desktop-superset.md`](browsers/10-desktop-superset.md)
