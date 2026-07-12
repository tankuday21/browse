# Desktop browser feature superset (Chrome / Firefox / Edge)

*State as of mid-2026. Legend: C = Chrome, F = Firefox, E = Edge. "Mobile?" = does a mobile equivalent exist anywhere today (any browser, any platform).*

## 1. Feature universe by category

### Tab management
| Feature | C | F | E | Mobile? |
|---|---|---|---|---|
| Tab groups (color-coded, collapsible, named) | ✔ | ✔ (since FF137, 2025) | ✔ | ✔ Chrome/Edge/Samsung Internet Android |
| Saved/synced tab groups | ✔ | ✔ | ✔ | ✔ Chrome Android tab-group sync (2025) |
| AI auto-grouping / group-name suggestion | ✔ | ✔ (FF141, on-device local model) | ✔ (Copilot) | ✖ rare on mobile |
| Tab pinning | ✔ | ✔ | ✔ | Partial (Samsung Internet; Chrome Android late 2025) |
| Vertical tabs | ✖ (native experiment 2026) | ✔ (FF136 sidebar) | ✔ (native, pioneer) | ✖ (form factor) |
| Sleeping/discarded tabs (auto memory reclaim) | ✔ Memory Saver (ML-driven) | ✔ "Unload tabs" | ✔ Sleeping Tabs | ✔ implicitly (Android kills tabs) but not user-controllable |
| Workspaces (shared/persistent named window setups) | ✖ | ✖ | ✔ Edge Workspaces | ✖ |
| Tab search / fuzzy switcher | ✔ | ✔ | ✔ | ✔ Chrome Android (2025) |
| Split screen / side-by-side tabs in one window | ✔ (2025) | ✖ (WIP) | ✔ | ✔ Samsung Internet; OS-level split |
| Multi-tab selection & bulk actions | ✔ | ✔ | ✔ | ✔ Chrome Android |
| Recently closed tabs / reopen | ✔ | ✔ | ✔ | ✔ |
| Tab preview on hover | ✔ | ✔ | ✔ | ✖ |
| Per-tab audio mute | ✔ | ✔ | ✔ | ✖ mostly |
| Send tab to device | ✔ | ✔ | ✔ | ✔ |

### Bookmarks & reading
| Feature | C | F | E | Mobile? |
|---|---|---|---|---|
| Bookmarks manager (folders, tags, search) | ✔ | ✔ (tags, keywords — unique) | ✔ | ✔ basic |
| Reading list (separate from bookmarks) | ✔ | ✖ (Pocket killed mid-2025) | ✔ | ✔ Chrome/Safari mobile |
| Collections (rich cards, notes) | ✖ | ✖ | ✝ **Killed in Edge 149 (June 2026)** — cautionary tale | ✝ was on Edge mobile |
| Reader mode / reading view | ✔ (2026 full-page) | ✔ Reader View | ✔ Immersive Reader (richest) | ✔ Firefox/Edge/Samsung mobile |
| Bookmark keyword shortcuts | ✖ | ✔ (unique) | ✖ | ✖ |

### History & session
| Feature | C | F | E | Mobile? |
|---|---|---|---|---|
| Full session restore | ✔ | ✔ (deepest: scroll pos, form data) | ✔ | Partial (tabs persist) |
| History Journeys (topic-clustered history) | ✔ | ✖ | ✔ (mobile May 2026) | ✔ Edge mobile |
| AI natural-language history search | ✔ (opt-in) | ✖ | ✔ | Rolling out with Gemini Android |
| Synced history & open tabs across devices | ✔ | ✔ | ✔ | ✔ |

### Downloads
| Feature | C | F | E | Mobile? |
|---|---|---|---|---|
| Pause/resume downloads | ✔ | ✔ | ✔ | ✔ Chrome Android |
| Parallel downloading (multi-connection) | ✔ (flag) | ✖ | ✔ (flag) | ✔ Samsung Internet, third-party browsers |
| Download tray UX (progress ring, previews) | ✔ | ✔ | ✔ | Basic notification-based |
| Malware/dangerous-file scanning | ✔ (deep scan) | ✔ | ✔ SmartScreen | ✔ partial |
| Scheduled downloads | ✖ | ✖ | ✖ | ✔ Chrome Android "download later" (regional) |

### Privacy & security
| Feature | C | F | E | Mobile? |
|---|---|---|---|---|
| Per-site process sandboxing / Site Isolation | ✔ | ✔ (Fission) | ✔ | Partial on Android; WebView: inherited, not configurable |
| Safe Browsing / SmartScreen | ✔ (+Enhanced real-time) | ✔ | ✔ | ✔ (WebView has Safe Browsing API) |
| Built-in password manager + generator | ✔ | ✔ | ✔ | ✔ |
| Passkeys + export | ✔ | ✔ | ✔ | ✔ (Android Credential Manager) |
| Password breach/leak alerts | ✔ | ✔ | ✔ | ✔ Chrome Android |
| DNS-over-HTTPS (configurable) | ✔ | ✔ (pioneer) | ✔ | ✔ browser-level Chrome/Firefox Android; Android Private DNS is DoT system-wide |
| Tracker blocking by default | ✖ | ✔ ETP Strict + Total Cookie Protection | ✔ (3 tiers) | ✔ Firefox/Edge/Samsung mobile |
| Fingerprint resistance | ✖ | ✔ | Partial | ✔ Firefox Android; Brave farbling |
| Built-in ad blocker | ✖ | ✖ | ✖ (ABP bundled on mobile) | ✔ many Android browsers |
| HTTPS-Only / HTTPS-First | ✔ | ✔ (pioneer) | ✔ | ✔ |
| One-time permissions + safety check hub | ✔ | ✔ | ✔ | ✔ Chrome Android |
| VPN built-in / bundled | ✖ | ✔ (paid, separate) | ✔ Secure Network | ✔ Edge mobile; Opera VPN |
| Scam detection (on-device AI) | ✔ (Gemini Nano) | ✖ | ✔ | Rolling to Chrome Android |

### Profiles & identity
| Feature | C | F | E | Mobile? |
|---|---|---|---|---|
| Multiple profiles | ✔ | ✔ (FF144, Oct 2025) | ✔ (work/personal) | ✖ largely — **big gap on mobile** (WebView has multi-profile API since androidx.webkit 1.9!) |
| Guest mode | ✔ | ✖ | ✔ | ✖ |
| Container tabs (per-tab cookie identities) | ✖ | ✔ Multi-Account Containers (unique) | ✖ | ✖ nowhere on mobile — **differentiation opportunity** |

### Sync ecosystems
- All three: full sync (bookmarks, passwords, history, tabs, settings, extensions), payment/address autofill sync, cross-device handoff. Firefox is E2E-encrypted by design; Chrome offers a passphrase; Edge partial.

### Developer tools
- Full DevTools suites desktop-only. Mobile: remote debugging only (WebView supports chrome://inspect). No in-browser devtools on any mobile browser — Eruda-style injected consoles are the workaround.

### Performance
- Desktop: Memory Saver tiers (C), Sleeping Tabs (E), tab unloading (F); Energy saver (C/E); startup boost (C/E); preloading everywhere. Mobile: engine-level only, no user-facing controls.

### Media
| Feature | C | F | E | Mobile? |
|---|---|---|---|---|
| Picture-in-Picture | ✔ | ✔ (auto-PiP, subtitles — best) | ✔ | ✔ Android OS PiP |
| Global media controls hub | ✔ | ✖ | ✔ | ✔ via Android MediaSession notifications |
| Cast to device | ✔ (Chromecast) | ✖ | ✔ (Miracast/DLNA) | ✔ Chrome Android |
| Live Captions | ✔ (incl. translation) | ✖ | ✔ | ✔ Android OS-level |

### Search & omnibox tricks
| Feature | C | F | E | Mobile? |
|---|---|---|---|---|
| Site search shortcuts / custom engines | ✔ | ✔ | ✔ | ✔ Firefox Android; Vivaldi |
| @-shortcuts (@bookmarks, @history, @tabs) | ✔ | ✔ (%, *, ^ tokens) | ✔ | ✖ mostly — **open lane** |
| Inline calculator / unit conversion | ✔ | ✔ | ✔ (full converter) | ✔ partially via search suggest |
| "Switch to tab" action chips | ✔ | ✔ | ✔ | ✖ |
| Quick actions ("clear history") | ✔ Pedals | ✔ | ✔ | ✖ |

### Page tools
| Feature | C | F | E | Mobile? |
|---|---|---|---|---|
| Full-page translate | ✔ | ✔ (fully on-device — unique) | ✔ | ✔ Chrome/Edge Android; Firefox on-device |
| Screenshot tool (full-page + annotate) | Partial | ✔ (native full-page) | ✔ Web Capture with ink | ✔ Firefox Android; OS screenshot |
| PDF viewer with annotation | ✔ | ✔ (edit, draw, sign — 2025) | ✔ (Adobe engine) | ✖ weak — most Android browsers hand off to external app (**big gap**) |
| Per-site zoom memory | ✔ | ✔ | ✔ | ✖ mostly |
| Find in page (match count, highlight all) | ✔ | ✔ (highlight all, whole-word) | ✔ | ✔ |
| Save page (HTML/MHTML) | ✔ | ✔ | ✔ | ✔ Chrome offline pages; WebView saveWebArchive |
| QR code generator for current page | ✔ | ✖ | ✔ | ✔ Chrome Android |
| PWA install & window management | ✔ (richest) | ✖ | ✔ | ✔ WebAPK (Chrome), Add-to-home-screen |

### AI features (2026 snapshot)
- Assistant sidebars everywhere (Gemini/Copilot/BYO-chatbot in Firefox); multi-tab reasoning (C/E); agentic browsing (C auto-browse, E Copilot Actions); page/PDF/video summarization universal; AI writing help (C/E); on-device small-model features (C Gemini Nano; F local models for translation/tab grouping/alt-text). Mobile is catching up fast — Gemini in Chrome Android and Copilot in Edge mobile both shipped 2026.

### Accessibility
- Read aloud with natural voices (E best-in-class, C reading mode); Live captions (C/E + Android OS); reading modes with dyslexia fonts/line focus (E Immersive Reader richest); full keyboard nav/screen-reader tuning everywhere; AI alt-text (C for screen readers, F local model).

### Extensions & theming
- C: Chrome Web Store (MV3 only). F: AMO, MV2+MV3 — uBlock Origin full strength. E: Edge Add-ons + CWS compat. Mobile: Firefox Android (1,000+ open ecosystem), Edge Android (curated store), Samsung (content blockers only), Chrome (none).

---

## 2. Which desktop features are migrating to mobile (trendline 2024–2026)

1. **Tab groups + tab group sync** — now table stakes on mobile.
2. **Extensions** — the biggest structural shift. Firefox Android opened its ecosystem (Dec 2023); Edge for Android testing desktop extensions; Chrome building extension support into desktop-class Android builds (Aluminium OS convergence, 2026).
3. **AI assistants** — fastest migration ever: Copilot fully in Edge mobile (May 2026); Gemini in Chrome + agentic auto browse on Android (June 2026). Desktop-first → mobile within ~12 months.
4. **Journeys / AI history** — Edge Journeys reached mobile May 2026.
5. **On-device translation** — Firefox's local-model translation shipped to Android (2025).
6. **Passkeys & breach alerts** — parity achieved via Android Credential Manager.
7. **Reading/PDF tools** — still incomplete vs desktop.
8. **Counter-trend — desktop features being killed, not migrated**: Edge Collections, Sidebar apps, and Drop all removed in Edge 149 (June 2026); Mozilla killed Pocket (2025). Lesson: curation/collection features lost to AI-centric surfaces. **Don't clone dead-ends.**
9. **Not migrating**: full DevTools, workspaces, vertical tabs, global media controls UI, Lighthouse.

---

## 3. Desktop features that would differentiate an Android browser (ranked, WebView-practical)

Ranked by differentiation-per-effort for a **WebView-based** browser. ⚠ = engine-limited; ❌ = needs a full engine.

1. **Multi-profile / container identities** — almost no mobile browser has real profiles; Firefox containers exist nowhere on mobile. WebView supports this natively via `androidx.webkit` **Profile / ProfileStore API** (separate cookies, storage, service workers per profile). High wow, fully feasible. *Top pick.*
2. **Desktop-grade tab management**: named/colored saved tab groups, pinned tabs, tab search, "workspaces" (persistable window sets), auto-sleeping with per-tab memory badge — all pure browser-chrome UI, engine-independent. Feasible: tabs are your own state; "sleeping" = destroy WebView, keep state bundle + thumbnail.
3. **Real download manager** — parallel segmented downloads, pause/resume, scheduling, in-app previews. WebView only gives you `DownloadListener`; you own the downloader anyway, so you can beat Chrome Android easily. Fully feasible.
4. **Reading list + reader mode with offline caching** — inject Readability.js, store cleaned HTML for offline; add read-aloud via Android TTS. Pocket's death left a vacuum. Fully feasible.
5. **Per-site settings memory** (zoom level, desktop mode, dark mode, JS toggle, permissions) — Chrome Android barely does this. Trivial with WebView settings applied per-origin. Fully feasible.
6. **Screenshot + full-page capture + annotate** — full-page via drawing the WebView beyond viewport; annotation UI is yours. Feasible.
7. **In-page PDF experience** (view, annotate) — Android browsers punt PDFs to external apps; embedding a PDF renderer (`PdfRenderer`/pdfium) closes a real gap. Feasible, independent of WebView.
8. **Tracker/ad blocking** ⚠ — `shouldInterceptRequest` + filter lists + injected cosmetic CSS gets you a solid "Brave-lite." Limits: no true webRequest API, can't modify response bodies, performance cost on huge lists, and **uBlock-Origin-parity is not achievable** — full parity needs an engine. Ship the 80% version; still a differentiator vs Chrome Android.
9. **Send-to-device / cross-device sync** — you must build your own sync backend. Feasible but backend-heavy; encrypt E2E (Firefox-style) as a trust differentiator.
10. **AI layer (summarize, multi-tab compare, page Q&A)** — extract page text via `evaluateJavascript`, feed to a cloud LLM or Gemini Nano on-device via AICore. Assistant-tier feasible; agentic auto-browse partially feasible but fragile. Do summaries + tab-grouping AI first.
11. **Omnibox power features** — custom search keywords, @tabs/@history/@bookmarks scoping, quick actions, inline calculator. Pure UI. Cheap, delightful, no mobile browser does this well.
12. **Global media hub + PiP polish** — MediaSession notifications + Android PiP for fullscreen video feasible (custom `onShowCustomView` handling). Casting via Cast SDK feasible for detectable media URLs; ⚠ DRM streams can't be recast.
13. **Safe Browsing + breach alerts** — WebView has built-in Safe Browsing (`setSafeBrowsingEnabled`, default on) — free win; leak alerts via HIBP-style API for saved logins.

**Flag: needs a full engine — do not attempt with WebView:**
- ❌ **Extensions (WebExtensions API)** — no WebView support, period. Nearest substitutes: user-script injection (Via/Quetta model) and a curated "script store." Position it as "scripting," never "extensions."
- ❌ **Full DevTools UI** — remote debugging works (`setWebContentsDebuggingEnabled`); Eruda-style injected consoles possible; native DevTools = engine.
- ❌ **DNS-over-HTTPS provider choice / network-stack controls** — WebView's network stack is not configurable (system Private DNS only).
- ❌ **Site isolation tuning, fingerprint resistance (RFP), Total-Cookie-Protection semantics** — engine internals. Don't market security you can't implement.
- ❌ **HTTP-level features**: ECH toggles, response-body filtering.
- ⚠ **On-device translation (Firefox-style)** — approximable with ML Kit Translate + DOM text-node rewriting (works, imperfect on dynamic pages).

**Strategic read**: WebView caps you on privacy-engineering and extensions — the two axes Firefox/Brave own. The open lanes are **organization (profiles/containers/workspaces/tab power), downloads, reading/offline, per-site control, and pragmatic AI** — all chrome-layer, all under-served on Android, all fully buildable on WebView.
