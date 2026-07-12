# Firefox (Android)

## 1. Overview & positioning
Firefox for Android (codebase "Fenix") is the only major Android browser built on a non-Blink engine — Mozilla's **GeckoView** — making it the sole mainstream alternative rendering engine on the platform and the only major Android browser with an **open extension ecosystem**. Market share is small: roughly **0.6% of worldwide mobile sessions** (Statcounter, April 2026) and ~1.4% of US mobile, versus ~2.2–2.9% cross-platform — Firefox's base skews heavily desktop. Target user: privacy-conscious and power users who want uBlock Origin-class ad blocking, deep tracking protection, cross-device sync outside Google's ecosystem, and customization (address bar position, wallpapers, extensions). Positioning is "independent, private, yours" — explicitly anti-surveillance-capitalism, with 2026 messaging emphasizing *user-controlled, opt-in AI*.

## 2. Feature inventory by category

### Navigation & address bar
- Combined URL/search bar with search suggestions, history/bookmark/open-tab autocomplete
- Address bar placeable at **top or bottom** (bottom is the modern default for new installs)
- Auto-hide toolbar on scroll (toggleable in Settings > Customize)
- QR-code scanner built into the address bar
- Voice search microphone in the address bar
- Search-engine shortcuts (tap to search with a one-off engine from the address bar)
- Optional dedicated navigation bar (back/forward/home/tabs) added with the 2025 toolbar redesign; back/forward arrows on toolbar with long-press for tab history
- "Refreshed toolbar" (v148, Feb 2026): cleaner look plus toolbar customization options
- Shield icon in address bar shows Enhanced Tracking Protection status per site; padlock for permissions/connection info
- Summarize-page entry point in the address bar (152, where eligible)

### Tabs & tab management (incl. Collections)
- Tab tray (opened via numbered tab counter) with **List or Grid view**
- Three tab-tray pages: **normal tabs, private tabs, synced tabs** (from other devices)
- **Tab Groups** — color-coded, nameable groups; rolled out to Android with v152 (June 2026) after a Mozilla Connect co-design program; groups can be collapsed/reopened
- **Collections** — long-standing Fenix feature: save sets of open tabs into named collections (e.g., Travel, Shopping) that live on the home screen; add/rename/share/reopen; distinct from tab groups (collections are saved snapshots, groups are live tabs)
- **Inactive tabs** — tabs untouched for ~14 days auto-move to a collapsible "Inactive" section with one-tap close-all
- Close tabs automatically after one day/week/month (Settings > Tabs)
- Select multiple tabs (long-press) to move to collection, share, or close
- "Recently closed tabs" list under History; undo close-tab snackbar
- Swipe a tab in the tray to close it; "Close all tabs" in tray menu
- Private tabs wiped on exit option; private-browsing shortcut on Android home screen

### Home / New-tab page
- Firefox Home shows: **Shortcuts** (frequent sites, pinnable), **Sponsored shortcuts** (ads, can be disabled), **Recently visited**, **Recent bookmarks**, **Jump back in / recent tabs**, **Collections**, and **Stories/recommended articles** (Pocket-derived recommendations, English locales)
- Every section individually toggleable via Settings > Homepage / "Customize homepage"
- **Wallpapers** for the home screen (Classic + Limited Edition sets; no custom image from gallery)
- Opening-screen behavior setting: always Home, last tab, or Home after four hours of inactivity
- "You're protected" tracker-blocking message on home (tweaked in 152.0.4 to appear only after trackers actually blocked)
- World Cup live-scores widget on Home (151.0.4/152, June 2026)

### Search & discovery
- Multiple pre-installed engines (Google default revenue deal; DuckDuckGo, Bing, etc.); **add custom search engine** by URL template
- Separate default engine choosable for private browsing; toggle to show/hide search suggestions in private mode
- Search widget for the Android launcher home screen
- "Search" via text-selection context menu
- Autocomplete from history/bookmarks/clipboard ("fill link from clipboard")
- Firefox Suggest-style sponsored suggestions largely a desktop feature; Android keeps suggestions simpler

### Privacy & security (ETP, etc.)
- **Enhanced Tracking Protection** with Standard / Strict / Custom modes, per-site shield toggle, and Total Cookie Protection (cookie isolation per site)
- **Private browsing** with dedicated tab tray page, mask theming, optional lock (device auth), and no summaries/telemetry
- **HTTPS-Only Mode** (all tabs or private-only)
- **DNS over HTTPS** user-facing setting (surfaced v143, Sept 2025): Default/Increased/Max protection with provider choice
- **Cookie Banner Blocker** ("Reduce cookie banners") auto-rejects consent popups
- **Global Privacy Control** toggle
- **Site Isolation (Fission)** on Android from v147 (Jan 2026) — Spectre-class side-channel protection matching desktop
- **Certificate Transparency enforcement** and **CRLite** local revocation checking (v145, Nov 2025)
- **Post-quantum crypto**: ML-KEM (mlkem768x25519) in TLS 1.3/HTTP3 (v145)
- Delete browsing data on quit; granular "Delete browsing data" (tabs, history, cookies, cache, permissions, downloads)
- Password manager with breach-alert integration (Firefox Monitor lineage), biometric gate, autofill; third-party autofill service support
- Autofill for addresses and payment methods; sync of logins/addresses/cards (E2E encrypted)
- Tracker-blocking counts per site via the shield panel; SmartBlock unbreaks sites in Strict mode
- Remote-improvements opt-in decoupled from telemetry (v148) — can receive remote config without sending data

### Ad/content blocking
- **No built-in ad blocker** — by design Mozilla relies on ETP (blocks trackers, cryptominers, fingerprinters, tracking cookies/content in Strict)
- Full **uBlock Origin (Manifest V2)** available as an extension — the flagship reason many users choose Firefox on Android; Chrome Android offers no extensions at all
- Other blockers: AdGuard AdBlocker, Ghostery, Privacy Badger, NoScript, Consent-O-Matic, SponsorBlock for YouTube

### Downloads & media (background video/PiP)
- Revamped **download manager** (v143): real-time progress tracking, pause/cancel quick controls, notification integration
- **Background audio/video playback** (continues with screen off via media notification) — notably works for YouTube with extensions like "Video Background Play Fix"
- **Picture-in-Picture** video support (system PiP)
- Autoplay blocking (block audio only / block audio & video)
- Media session controls in notification shade / lockscreen
- Open PDFs inline (PDF.js); **share the PDF file itself or copy a link from the address bar** (v152); save page as PDF / print
- xHE-AAC high-quality audio codec support (v143)
- Downloads accessible from menu quick-row; files land in system Downloads

### Reading & content (reader mode, translate, TTS)
- **Reader View** (book icon in address bar on article pages): font size/typeface, light/sepia/dark themes
- **Built-in full-page translation** — fully **on-device** (no cloud), auto-offers on foreign-language pages, translates continuously as you browse; settings for always/never translate per language/site; Zstandard-compressed models shrink downloads (v145); improved RTL/bidi handling (v145)
- No built-in text-to-speech / read-aloud (users rely on extensions like Read Aloud or Android's system Reading Mode)
- Find in page; copy link text without opening the page (v148)
- "Open in app" handoff and desktop-site toggle per tab

### Customization & appearance (address bar position!)
- **Address bar/toolbar position: top or bottom** — the signature customization Chrome long lacked; plus separate navigation-bar option
- Toolbar customization (which actions appear) via the 2025–26 "composable toolbar" work (v148+)
- Auto-hide toolbar toggle
- Theme: Light / Dark / Follow device / set by battery saver
- Home-screen wallpapers; homepage section toggles; shortcuts on/off
- Gestures settings (pull-to-refresh toggle)
- Custom add-on collection support (Nightly) to sideload arbitrary extension sets
- **Secret settings / debug menu**: tap Firefox logo 5x in Settings > About Firefox — exposes feature flags (e.g., disable the new tab tray/toolbar redesigns)
- **about:config available only on Nightly** (not Release/Beta) — a persistent power-user complaint

### Sync & account
- Mozilla account sync: bookmarks, history, open tabs, passwords, addresses, payment methods, settings — **end-to-end encrypted**
- **Send tab** to/from any signed-in device; synced-tabs page in the tab tray
- Sign-in via QR code pairing with desktop
- No multi-profile support on Android (profiles shipped on desktop v144, not mobile)

### AI features
- **Page summaries / "Shake to Summarize"** (v151–152, May–June 2026): shake the phone, or tap in the address bar / "Summarize page" in the menu — AI summary of the article; runs on **Mistral Small 3.1 via Mozilla's cloud**; adapts output (recipes → steps, sports → scores); English-only, pages under ~5,000 words that support Reader View; unavailable in Private Mode and behind paywalls
- **AI Controls hub** (v151): a single settings surface to turn every AI feature on/off or configure individually — deliberate "AI on your terms" positioning; turning AI off disables the shake gesture and summaries together
- No sidebar chatbot on Android (desktop-only); no AI-injected search results of its own

### Gestures & shortcuts
- Pull-to-refresh
- Swipe address bar horizontally to switch between adjacent tabs
- **Swipe vertically across the toolbar** to open the tab view / move between tabs (v151)
- **Shake to summarize** (v152)
- Long-press back/forward arrows for tab history
- Pinch-zoom (performance overhauled in v152, notably on low-end devices)
- Android app shortcuts (long-press launcher icon: new tab, new private tab)
- Custom Tabs support for links opened from other apps (with Firefox menu inside)

### Data saving & performance
- No data-saver/compression mode (unlike Opera/Chrome Lite lineage)
- Rust-based networking stack for QUIC + HTTP/3 (v145) for faster connections
- GeckoView-tuned rendering; benchmark reality: ~10–15% slower than Chrome on Speedometer, though roughly comparable in daily feel; some tests show lower per-tab RAM than Chrome
- Minimum Android version raised to 8.0 and 32-bit x86 dropped (v144, Oct 2025)

### Extensions/add-ons (which extensions work on mobile)
- **Open extension ecosystem since Dec 2023 (v121)** — any developer can publish Android-enabled extensions to addons.mozilla.org; launched with 450+, grown past 1,000+ by 2025; still far below desktop's ~40,000
- Marquee working extensions: **uBlock Origin, Dark Reader, Bitwarden, Privacy Badger, NoScript, Violentmonkey/Tampermonkey (userscripts), SponsorBlock, Ghostery, Consent-O-Matic, Read Aloud, Video Background Play Fix, Simple Tab Groups, TWP/Linguist translators, Search by Image, ClearURLs, Decentraleyes/LocalCDN**
- Extensions managed via the main menu's Extensions section (with recommendations) and Settings; per-extension private-mode permission
- MV2 preserved — full-strength blocking APIs that Chrome's MV3 curtailed
- Nightly + custom AMO collection trick allows installing almost any desktop extension (unsupported)

### Accessibility
- Font size follows Android system settings or custom scaling ("Automatic font sizing")
- **Zoom on all websites** override (force pinch-zoom even where sites block it)
- TalkBack/screen-reader support; voice input; reader view as a de-cluttering aid
- High-contrast/dark theme support; menu redesign (v141) explicitly targeted accessibility/reachability

### Unique extras
- Only Gecko engine on Android; only major Android browser with real extensions
- Collections (unique save-tabs-to-project model)
- On-device translation (no cloud) — privacy-unique among major mobile browsers
- Custom Tabs + "Open links in apps" control
- Add to Home screen / PWA install (shortcut-based; no WebAPK, so weaker than Chrome's PWA integration)
- about:debugging via USB for remote devtools inspection of the phone from desktop Firefox (Settings > Remote debugging via USB)
- Firefox Nightly/Beta channels co-installable for early features
- World Cup widget (2026, opt-in home component)

## 3. UI/UX structure & feature placement

- **Address bar position and contents**: user choice of top or bottom (Settings > Customize; also asked in onboarding). Contains: shield (ETP) icon, padlock/site info, URL/search field, summarize entry (eligible pages), reader-view icon on articles, refresh, tab counter, three-dot menu. Optional separate navigation bar (back/forward/home/tabs/menu) from the 2025 redesign; auto-hides on scroll unless disabled.

- **Tab switcher/tab tray**: tap the numbered tab counter. Redesigned (2025) tray is a full/half sheet with three tabs/pages: **normal, private (mask icon), synced**. Grid or List layout (Settings > Tabs). Contains: new-tab FAB/button, three-dot tray menu (select tabs, share all, close all, tab settings, recently closed), Inactive-tabs collapsible section, tab-group chips (152+), banner to save selected tabs to a Collection. Redesign criticized for lower density (~4 tabs visible vs ~8 before).

- **Main menu (three-dot; redesigned v141, July 2025)**, roughly in order:
  1. Mozilla account sign-in/sync banner (top)
  2. Navigation row: **Back, Forward, Refresh, Share**
  3. Quick-access row: **Bookmarks, History, Downloads, Passwords**
  4. **Extensions** (expandable; shows installed + recommended add-ons)
  5. Page section: **New tab / New private tab** (contextual), **Switch to desktop site**, **Find in page**, **Translate page**, **Summarize page** (152+)
  6. **Save** group: Bookmark this page, Add to shortcuts, Add to Home screen, Save to collection, Save as PDF
  7. **Tools** group: Reader View, Print, Open in app, Report broken site
  8. **Settings** (pinned at the bottom)
  Older (pre-141) menu was a long flat list; the redesign grouped it with submenus and cut taps for common actions.

- **Settings screen — top-level sections in order** (approximate current structure):
  - **General**: Search · Tabs · Homepage · Customize (theme, toolbar position, gestures) · Passwords · Autofill (addresses, payment methods) · Accessibility · Language · Translations · Set as default browser
  - **Privacy and security**: Private browsing · HTTPS-Only Mode · Reduce cookie banners · Enhanced Tracking Protection · AI controls (151+) · Site permissions · Delete browsing data · Delete browsing data on quit · Notifications · Data collection
  - **Advanced**: Extensions/Add-ons · Remote debugging via USB
  - **About**: Rate on Google Play · About Firefox (5 taps on logo = debug/secret settings)

- **Long-press / context menus**:
  - Link: Open in new tab / Open in private tab / Copy link / **Copy link text** (148+) / Share link / Download link / Bookmark link
  - Image: Open image in new tab / Save image / Copy image location / Share image
  - Address bar: paste / paste & go / copy URL; clipboard-fill suggestion chip
  - Tab (in tray): select mode for multi-tab actions; toolbar back/forward long-press = session history
  - Text selection: copy, share, search, translate (and Private Search)
  - Launcher icon: New tab / New private tab shortcuts

- **Gestures supported**: pull-to-refresh; horizontal swipe on toolbar to change tabs; vertical swipe across toolbar for tab view (151+); shake-to-summarize (152+); pinch zoom (always-allowed option); scroll-to-hide toolbar; swipe-to-close tabs in tray.

- **First-run/onboarding flow**: card-based sequence — (1) welcome + **set as default browser** (deep-links to Android default-apps setting), (2) **choose address bar position (top/bottom)** on newer builds, (3) **sign in to sync** (QR pairing option), (4) notification permission request, (5) optional add-search-widget card; ends with "Start browsing." Telemetry/data-collection notice with opt-out; onboarding reruns only after clearing app data.

## 4. Standout features (what Firefox does best)
- **Real extensions on mobile** — uBlock Origin at full MV2 strength; no other major Android browser has an open extension ecosystem
- **Best-in-class tracking protection defaults**: Total Cookie Protection, ETP Strict, GPC, cookie-banner rejection — and ~2x better fingerprinting resistance than Chrome out of the box in third-party tests
- **On-device translation** — private by architecture, no text leaves the phone
- **Address bar position choice + toolbar customization** — long a differentiator vs Chrome
- **Engine independence** (Gecko) — sync ecosystem and rendering unbeholden to Google; MV2 ad-blocking APIs preserved
- **Power-user escape hatches**: Nightly with about:config, secret settings, remote USB debugging, custom add-on collections
- **Opt-in, clearly-controlled AI** (AI Controls hub) — contrast with rivals baking AI in with weak off-switches
- **Collections + tab groups + inactive tabs** — arguably the deepest tab-organization stack on Android now
- Background media playback + PiP handled unusually well (esp. with extensions)

## 5. Weaknesses & common criticisms
- **Tiny market share** (~0.6% mobile worldwide) → some sites/banks UA-sniff for Chrome, occasional compatibility glitches on content-heavy sites (patched repeatedly, e.g., 146.0.1)
- **Performance gap vs Chromium**: ~10–15% slower Speedometer, slower first paint on some sites; historically janky scrolling on low-end hardware (152's pinch-zoom work is a response)
- **Weaker PWA story**: no WebAPK integration; "Add to Home screen" is closer to a shortcut; fewer web-app APIs than Chrome
- **UI redesign backlash (2025–26)**: new tab tray density ("4 tabs where 8 fit before"), forced rollout without opt-in, reversion only via secret/debug settings — heavy criticism on Mozilla Connect and r/firefox, including accessibility complaints
- **No about:config on Release** — power users must run Nightly
- **Extension catalog still limited** (1,000+ vs ~40,000 desktop); some flagship desktop extensions lack Android support or proper mobile UIs
- **No built-in ad blocker or data-saver mode** — relies on extensions; heavier out-of-box than Brave for ad-averse users
- Menu/quick-actions redesign polarizing (screen space, muscle-memory breakage)
- Mozilla-org distrust threads: sponsored shortcuts/ads on Home by default, telemetry defaults, AI feature creep (partly answered by AI Controls and the 148 telemetry decoupling)
- Historic Android churn (Fennec→Fenix regression memories) still colors reviews

## 6. Recent additions (2024–2026)
- **Dec 2023 / through 2024**: open extension ecosystem matured (hundreds → 1,000+ Android add-ons); translation rolled out broadly (v126–130); Global Privacy Control; ongoing menu-redesign experiments
- **2025**:
  - **v141 (Jul)**: redesigned main menu (quick-access row, back/forward in menu, expandable Extensions, Settings at bottom)
  - **2025 toolbar/tab-tray overhaul** ("composable toolbar"): new tab manager tray, toolbar customization, share button surfaced, auto-hide toolbar, optional navigation bar
  - **v143 (Sep)**: new download manager with live tracking; user-facing **DNS over HTTPS** settings; xHE-AAC audio
  - **v144 (Oct)**: min Android 8.0, 32-bit x86 dropped; tab-group groundwork
  - **v145 (Nov)**: Certificate Transparency enforcement, **CRLite**, **ML-KEM post-quantum TLS**, Rust QUIC/HTTP3 stack, lighter Zstandard translation models
- **2026**:
  - **v147 (Jan)**: **Site Isolation (Fission)** on Android — desktop-grade Spectre protection
  - **v148 (Feb)**: refreshed toolbar look + customization; **copy link text**; remote improvements decoupled from telemetry
  - **v150 (Apr)**: stability/smoothness maintenance release
  - **v151 (May)**: **AI Controls hub**; vertical toolbar-swipe tab gesture; **page summaries** rollout begins
  - **v151.0.4 (Jun)**: World Cup live-scores widget
  - **v152 (Jun)**: **Shake to Summarize / page summaries for all (English)** via Mistral Small 3.1; **Tab Groups on Android**; PDF file sharing; pinch-zoom performance overhaul
  - **v152.0.4 (Jun)**: "You're protected" home message only after trackers actually blocked
