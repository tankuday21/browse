# Niche power browsers: Vivaldi · DuckDuckGo · Kiwi (Android)

# Vivaldi (Android)

## 1. Overview & positioning
Vivaldi on Android is the mobile companion to Vivaldi's desktop browser, built on Chromium/Blink by Vivaldi Technologies (Norway/Iceland) and positioned as the most feature-rich, customizable power-user browser on Android — "everything is an option." Actively developed as of mid-2026 (v8.0 released May 28, 2026). Free, no account required, funded by search/bookmark partner deals rather than user data.

## 2. Feature inventory by category
- **Navigation & address bar**: Combined search/address field; multiple built-in search engines with "search engine nicknames"; **custom search engines** (7.7, Nov 2025 — long-press a site's search field > "Add as Search Engine"); search-with-selected-text; address bar movable top or bottom; pull-to-refresh can be disabled (7.8).
- **Tabs & tab management**: Desktop-style **visible Tab Bar on phones and tablets** (optional); **Tab Stacks** (drag tab onto tab to group); **Two-Level Tab Bar** — world-first two rows of mobile tabs (Vivaldi 5.0): stacked tabs shown on a second row; renamable tab stacks; choice of **Accordion Tabs vs Two-Level** stack display (Accordion arrived on Android in 8.0); **pinned tabs on Android** (7.8); Tab Switcher with sections for normal/private/synced/recently-closed tabs; tab search (7.1); close-tab-by-swipe; clone tab, background tab opening.
- **Home / New-tab page**: **Speed Dial** start page with folders/groups, customizable columns and layout; unified Start Page combining **Widgets and Speed Dials** (7.7); "Add Page to…" menu (Speed Dial / bookmark / reading list / home screen).
- **Privacy & security**: Built-in **tracker and ad blocker** (per-site configurable, blocking levels: off / trackers / trackers+ads, custom filter lists); **Privacy Dashboard** showing blocked ads/trackers counts (7.8); private tabs; option to use third-party autofill/password manager (7.8); no user profiling by vendor; end-to-end encrypted sync; cookie-consent-blocker ("Cookie Crumbler") lists.
- **Ad/content blocking**: Integrated blocker (EasyList/EasyPrivacy-style lists plus custom list import) — no extensions needed; per-site exceptions.
- **Downloads & media**: Built-in download manager accessible from Panels; **integrated PDF viewer** (8.0 — PDFs open in-browser).
- **Reading & content**: **Reader View** (redesigned in 7.1); **Reading List**; **Notes** — full note manager with titles, synced with desktop notes; **Vivaldi Translate** — built-in private translator (Lingvanex-powered, hosted on Vivaldi servers); **page capture / full-page screenshot** from the menu.
- **Customization & appearance**: **Address Bar + Tab Bar position: top or bottom** (independently configurable); customizable **toolbar shortcut button**; themes, accent colors, app icon options; per-site UI theming from page colors; hide navigation bar on scroll; improved forced dark mode for web pages (7.7); disable Tab Bar entirely.
- **Sync & account**: **Vivaldi Sync** — end-to-end encrypted with a separate encryption password, free Vivaldi.net account: bookmarks, Speed Dials, passwords, autofill, history, tabs, notes, reading list, search engines.
- **AI features**: Deliberately none — Vivaldi has publicly taken an anti-"AI-in-browser" stance, marketed as a feature ("browse without AI interference").
- **Gestures & shortcuts**: Drag-down from top toolbar gestures; swipe-to-close tabs; swipe between tabs on address bar; long-press context actions throughout.
- **Extensions**: **Not supported** on Android (a top user complaint) — Vivaldi argues the built-in feature set replaces most extensions.
- **Unique extras**: Notes with sync; full-page capture; built-in translate; Reading List; Panels sidebar (Bookmarks, History, Notes, Downloads); granular per-site performance controls (7.7); Chromecast support.

## 3. UI/UX structure & feature placement
- Address bar top **or** bottom; optional desktop-style Tab Bar (top), which can also move with the address bar; on tablets/landscape, bottom-toolbar icons merge into the top toolbar for a desktop-like layout.
- Bottom toolbar (phones): Panels button (left) opens slide-out panel for Bookmarks/History/Notes/Downloads; back/forward, tab-switcher button, customizable shortcut button, and the **"V" Vivaldi menu** (a long scrollable menu of actions).
- Distinctive UX: the only major Android browser that reproduces a desktop tab strip + tab stacks on a phone; Speed Dial-centric start page; settings depth is unmatched (and notoriously deep).

## 4. Standout features
- Two-Level Tab Bar / Tab Stacks with Accordion mode — genuinely unique on mobile.
- Full toolbar/address-bar position customization predating Chrome's own bottom-bar experiments.
- Built-in ad/tracker blocker + Privacy Dashboard, notes sync, translate, page capture — "no extensions needed" bundle.
- E2E-encrypted sync without a Big Tech account.
- Explicitly AI-free positioning (differentiator in 2026).

## 5. Weaknesses & criticisms
- No extension support on Android; mobile lags desktop on advanced tab management/workspaces.
- Complexity/discoverability: features buried behind multiple menus/submenus; overwhelming settings for casual users.
- Performance: heavier than stock Chromium builds; sluggish on low-end devices.
- Smaller ecosystem/market share means occasional site-compat quirks; dated visual design per some reviewers.

---

# DuckDuckGo Browser (Android)

## 1. Overview & positioning
DuckDuckGo Private Browser is a privacy-first Android browser built on the system **WebView** (Blink) around DuckDuckGo's search engine, positioned as "privacy, simplified" — protection by default rather than power-user configuration. Free, with an optional paid Privacy Pro subscription (VPN, advanced Duck.ai models, identity protection). Deliberately minimalist. **Notable for Andromeda: it proves a serious browser can ship on system WebView.**

## 2. Feature inventory by category
- **Navigation & address bar**: Single combined search/address bar wired to DuckDuckGo Private Search; address bar position **top or bottom**; anonymous local autocomplete.
- **Tabs & tab management**: Basic tab switcher (grid/list); **swipeable tabs** (swipe on address bar to switch); tab previews; no tab groups — intentionally simple.
- **Home / New-tab page**: Minimal: search box + **Favorites** grid; recently a cleaner "focused" layout. No widgets/speed-dial folders.
- **Privacy & security** — the core of the product:
  - **3rd-Party Tracker Loading Protection** (blocks tracking scripts before load)
  - **App Tracking Protection** (Android-exclusive: a local on-device VPN service that blocks third-party trackers inside *other apps* — no traffic leaves the device)
  - **Fire Button** (one-tap flame icon burns all tabs + browsing data, with animation; per-site fireproofing to keep logins)
  - **Cookie Pop-Up Management** (auto-answers consent banners), **Smarter Encryption** (HTTPS upgrading), **Global Privacy Control (GPC)**, **Email Protection** (@duck.com aliasing), **Scam Blocker** (phishing/malware/scam-site blocking, expanded 2025), link tracking-parameter stripping, referrer trimming, fingerprinting mitigations, embedded-content protection.
- **Ad/content blocking**: Historically no general ad blocker (ads only disappeared as a side effect of tracker blocking). As of **July 2026**: built-in **video ad blocking incl. YouTube**, based on uBlock Origin community filter lists — manual enable on Android (Settings > Ad Blocking); DDG signaled expansion toward broader ad blocking.
- **Downloads & media**: Basic download manager; **Duck Player** — built-in YouTube player enforcing YouTube's strictest embedded privacy settings (no targeted ads, views don't feed recommendations).
- **Reading & content**: No reader mode, no reading list, no translate — notable gaps.
- **Customization & appearance**: Light/dark/system theme; address bar position; show/hide Duck.ai buttons; app icon variants; otherwise minimal by design.
- **Sync & account**: **Sync & Backup** — end-to-end encrypted, **no account needed** (device-pairing via QR code + recovery PDF); syncs bookmarks, passwords (built-in password manager), and Email Protection settings.
- **AI features**: **Duck.ai** — private access to multiple chat models (free tier: Claude Haiku, GPT minis, Mistral, open models; Privacy Pro tiers add frontier models) with anonymized proxying, no training on chats, chats stored locally only; optional AI-assisted search answers; **fully optional** — a master toggle hides all AI UI.
- **Gestures & shortcuts**: Swipe address bar to switch tabs; pull-to-refresh; long-press for link/image actions; Fire Button as the signature one-tap action.
- **Extensions**: None.
- **Unique extras**: App Tracking Protection (unique on Android); GPC; @duck.com email aliases; per-site Privacy Dashboard (tap the shield); paid **Privacy Pro**: VPN, Personal Information Removal, Identity Theft Restoration.

## 3. UI/UX structure & feature placement
- Single bar (top by default, bottom optional) + three-dot overflow menu; shield icon in the bar opens the per-site privacy dashboard (tracker list, protections toggle); **flame/Fire Button** prominent in the toolbar/menu — burning data triggers the signature fire animation.
- Tab icon opens a simple switcher; Settings organized around protections. App Tracking Protection runs as a local VPN with a notification of blocked in-app trackers.
- Distinctive UX: radical simplicity — closer to a search app than a power browser; zero-config privacy with the Fire Button ritual as its most memorable interaction.

## 4. Standout features
- App Tracking Protection (system-wide, on-device in-app tracker blocking) — unique on Android.
- Fire Button + fireproofing; per-site privacy dashboard.
- Duck.ai multi-model private AI chat with a true global off switch — the most opt-out-friendly AI integration of any major browser.
- Duck Player + new uBlock-list-based YouTube ad blocking (2026).
- Accountless E2E-encrypted sync; Email Protection aliases; GPC pioneer.

## 5. Weaknesses & criticisms
- **2022 Microsoft tracker scandal**: browser initially exempted Microsoft trackers due to the Bing syndication deal — a lasting trust dent.
- Feature-poor as a *browser*: no extensions, no reader mode, no translate, no tab groups; power users outgrow it quickly.
- Fingerprinting protection is API-spoofing rather than robust blocking; researchers showed persistent localStorage surviving data clearing on Android.
- App Tracking Protection occupies Android's one VPN slot (can't run alongside a real VPN) and can break some apps.
- YouTube ad blocking still not default-on for Android as of July 2026.

---

# Kiwi Browser (Android) — legacy

## 1. Overview & positioning
Kiwi Browser was a Chromium-based Android browser created in 2018 by solo developer Arnaud Granal, famous as the **first mobile browser with full desktop Chrome-extension support**. **Discontinued early 2025** (developer burnout; ~500k active users); pulled from Google Play, GitHub repo archived, extension-support code **donated to Microsoft Edge for Android**. Covered as legacy.

## 2. Feature inventory (legacy, final builds)
- **Navigation**: Chrome-style omnibox; revived "Chrome Home" **bottom address bar** option; custom search engines. (Caveat: search-redirect controversy, §5.)
- **Tabs**: Standard Chrome tab switcher/grid, tab groups inherited from Chromium.
- **Home / NTP**: Chrome-like NTP with configurable background, option to remove sponsored/suggested content.
- **Privacy & security**: Pop-up blocker, **cryptojacking/cryptominer blocking**, blocking of intrusive notification prompts, ability to **disable AMP**; otherwise inherited Chromium's model. Not a privacy-hardened fork.
- **Ad/content blocking**: Built-in ad blocker (plus most users layered uBlock Origin as an extension — its real superpower).
- **Downloads & media**: Chromium download manager; **background video/audio playback** (YouTube audio with screen off — a headline feature).
- **Reading & content**: Chromium simplified/reader view; built-in Translate; night/dark rendering.
- **Customization**: **Advanced Night Mode** — customizable forced dark mode with contrast/grayscale controls (AMOLED-friendly), one of the earliest and best implementations; bottom toolbar option; export/import settings.
- **Sync**: **None** — no cross-device sync of its own; bookmarks import/export only. A frequently cited gap.
- **Extensions — the signature**: Installed extensions **directly from the Chrome Web Store** with a near-desktop chrome://extensions page; supported uBlock Origin, Dark Reader, Tampermonkey, Bitwarden, Stylus, SponsorBlock, etc.; most reliable with **Manifest V2** extensions — which Google is phasing out, so the frozen final build ages badly.
- **Unique extras**: Open-sourced (2023); served as the reference implementation that seeded extension support in **Microsoft Edge for Android**.

## 3. UI/UX structure
- Essentially **Chrome's UI with power toggles**: top omnibox with optional bottom bar; standard three-dot menu extended with extension entries, night mode, translate; standard Chrome tab grid.
- Extensions surfaced via the overflow menu and a desktop-style chrome://extensions page.
- Distinctive UX: zero learning curve for Chrome users — its identity was *removing Chrome's mobile limitations* (extensions, background play, night mode, bottom bar).

## 4. Standout features
- Full Chrome Web Store extension support on a phone — uBlock Origin + Tampermonkey on mobile was its killer app.
- Background YouTube playback for free; advanced night mode; cryptominer blocking; bottom address bar years before Chrome.
- Legacy impact: its extension code lives on in Microsoft Edge for Android.

## 5. Weaknesses & criticisms
- **Discontinued and frozen**: no security patches on an aging Chromium base — actively unsafe to keep using in 2026.
- **Monetization/privacy controversy**: documented claims that Kiwi shipped fake "Yahoo/Bing" search engine entries that routed queries through Kiwi's own servers for revenue.
- Chronically behind Chromium upstream even while alive; solo-maintainer bus factor was the ultimate cause of death.
- No sync; MV2 dependence means even nostalgia use degrades.
- Successor landscape is fragmented: Edge for Android (curated extensions), Quetta, Lemur/Mises forks, or Firefox for Android — none replicates Kiwi's open model.
