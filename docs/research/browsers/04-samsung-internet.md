# Samsung Internet (Android)

*(Rebranded to **"Samsung Browser"** starting with the Galaxy S26 / One UI 8.5 in early 2026; the rebrand is rolling back to older Galaxy devices via One UI 8 updates. Sources still use both names — this report treats them as the same product.)*

## 1. Overview & positioning

- **What it is:** Samsung's first-party Chromium/Blink browser, default on all Galaxy phones and tablets. Debuted 2012 (Galaxy S3, AOSP-based), Chromium-based since 2013 (Galaxy S4), published on Google Play since 2017 so it installs on any Android 10+ device (non-Samsung included, with some Galaxy-only feature gaps).
- **Scale:** ~2.3–2.8% of *global* mobile browser share as of early-to-mid 2026 (StatCounter ~2.81% April 2026) — 3rd-largest mobile browser after Chrome and Safari and the **largest non-default mobile browser**; 1B+ Play Store downloads. Peak share was ~7.4% (Jan 2019); the decline reflects Chrome-as-default pressure, not feature regression.
- **Positioning:** the "power-user Chrome alternative that ships by default" — identity pillars: (1) one-handed ergonomics (bottom bar, customizable toolbar), (2) privacy defaults (Smart Anti-Tracking on by default, biometric Secret mode), (3) content-blocker plugin ecosystem Chrome lacks, (4) Galaxy ecosystem tie-ins (Samsung Pass/Knox, Galaxy AI Browsing Assist, cross-device sync).
- **Version cadence:** roughly monthly-to-quarterly major versions; as of June 2026 the Android stable is **v30.0.0.67**. Also now on **Windows** ("Samsung Browser for Windows," beta Oct 30 2025, GA 2026) with full Chrome-extension support and Samsung Account sync.
- **Reputation:** widely used as a UX benchmark for one-hand mobile browsing; reviewers repeatedly note "Galaxy users who consciously switch to Samsung Internet rarely switch back."

## 2. Feature inventory by category

### Navigation & address bar
- **Bottom or top address bar** — user-selectable position; bottom placement predates Chrome's equivalent by years.
- **Combined or separated tab/address bar layout** — "Together" (one line) or "Separate" via Settings > Layout and menu.
- **Swipe-on-address-bar tab switching** — swipe left/right on the address/bottom bar to move between tabs.
- **Settings search** — search box inside Settings to find any option (added v27).
- **QR code scanner** — launchable from the toolbar/menu (assignable button).
- **Toolbar blur effect** — One UI 8.5/9 adds a translucent blurred toolbar background (floating, layered look).

### Tabs & tab management
- **Tab groups** — create, name, rename, reorder, share (shares all URLs in group), and close groups.
- **Tab group sync** — tab groups sync across devices via Samsung account (since v23, Oct 2023).
- **Three tab-switcher layouts** — List, Stack, or Grid view ("View as" option).
- **Tab locking** — lock individual tabs against accidental closure.
- **Auto-close unused tabs** — automatically closes tabs untouched for a set period (2025 addition).
- **Reopen closed tabs / recently closed list.**
- **Tab bar on large screens** — desktop-style visible tab strip on tablets/foldables (optional on phones).
- **Multiple windows** — One UI 9 (late 2026) adds true simultaneous multi-window browser instances, even on regular phones.

### Home / New-tab page (Quick access)
- **Quick access grid** — user-editable icon grid of favorite sites; add/remove/reorder; can be set as homepage, or homepage can be Current page / Custom URL / blank.
- **News feed on Quick access** (region-dependent) — personalizable by language, location, and topic categories; can be fully disabled ("None").
- **Home screen shortcuts** — pin any site to the Android launcher (improved in v26).
- **Redesigned home screen** — refreshed layout shipped during 2025.

### Search & discovery
- **Switchable search engines** — Google (default), Bing, Yahoo, DuckDuckGo, Yandex, and regional options.
- **Search suggestions & unified URL/search field.**
- **In-page search (Find on page)** via main menu.
- **Video history** — dedicated history of videos watched in-browser, separate from page history.
- **History sorting options** and longer history retention (expanded 2023–2025).

### Privacy & security (Secret mode, Smart anti-tracking)
- **Secret mode** — private browsing that saves no history/cookies/search records; lockable with **password + biometrics (fingerprint or face)** — a differentiator vs. Chrome Incognito's simple lock.
- **Secret mode options** — allow/deny screenshots in Secret mode (screenshots allowed since v26), option to stay in Secret mode after closing browser, separate anti-tracking strength for Secret mode.
- **Smart Anti-Tracking** — AI-driven intelligent tracking prevention, **on by default**; blocks cross-site trackers, deletes tracking cookies/web traces, resists circumvention techniques; adjustable protection levels (Basic/Strong); includes automatic CAPTCHA-verification assistance.
- **Privacy Dashboard** — real-time count/report of blocked trackers and privacy status.
- **Per-site permissions** — camera, mic, location controls per site.
- **Warn-before-malicious-sites / anti-phishing protections**; passkey support (WebAuthn) with device biometrics.
- **End-to-end encryption for synced data** (v27, late 2024).
- **Quick Exit button** (assignable) — one tap closes everything, optionally wiping history and cookies.

### Ad/content blocking (third-party content blocker plugins)
- **Content-blocker extension API** — Samsung Internet pioneered (2016) an installable content-blocker plugin system on Android; blockers install as normal Android apps from Play Store/Galaxy Store, then activate under Menu > Settings/Ad blockers.
- **Multiple blockers simultaneously** — users can enable several at once.
- **Ecosystem** — AdGuard Content Blocker (20+ filter lists, custom rules, no root), Adblock Plus for Samsung Internet, Adblock for Samsung, Crystal, Unicorn, Disconnect, etc. The browser surfaces a curated recommendation list in-settings.
- **Limitation** — content blockers are declarative filter lists (Safari-style), not full extensions; no element zapping or scripting.

### Downloads & media (video assistant, save video)
- **Video Assistant** — floating control on web videos offering: pop-up (picture-in-picture) player, one-tap full screen, and "view on TV" casting; gesture controls inside player (swipe for brightness/volume, double-tap seek). Toggle at Settings > Useful features > Video Assistant.
- **Download manager** — pause/resume, download history, choose storage location.
- **Save page / save image / save link** via long-press context menu.
- **Video history** menu for revisiting watched videos.

### Reading & content (reader mode, translate, text scaling)
- **Reader mode** — declutters articles; adjustable font and font size (limited background-color choices — a noted weakness).
- **Translate** — full-webpage translation; engine improved 2026 with more accurate output across 20+ languages; deep integration with Galaxy AI on-device translation that even replaces text in menus and video overlays in-place.
- **Text scaling** — Settings > Accessibility: text size slider (can override site-forced sizing) plus webpage zoom control independent of system font size.
- **Read Highlight Aloud** — TTS reads selected/highlighted text.

### Customization & appearance (customizable toolbar/menu, dark mode for web)
- **Customizable bottom toolbar** — up to 7 slots, filled by drag-and-drop from the full function catalog (back/forward, home, tabs, bookmarks, downloads, share, dark mode, Quick Exit, QR scanner, desktop site, add-ons, Secret mode toggle, etc.). Introduced v10.2 (2019); still nearly unique among mainstream mobile browsers.
- **Customizable main menu grid** — the ≡ menu's icon grid is also fully re-arrangeable via the same drag-and-drop "Customize menu" screen (Settings > Layout and menu).
- **Address bar position** — top or bottom.
- **Dark mode for web content** — force-darkens all websites (algorithmic transformation), separate from UI dark theme; toggleable from the menu; known to occasionally mangle link colors/contrast.
- **High contrast mode** — accessibility-oriented white-on-dark rendering.
- **One UI 8.5/9 visual refresh** — blur/translucency, floating toolbar elements, new app icon and "Samsung Browser" branding.

### Sync & account (Samsung Cloud)
- **Samsung account sync** — bookmarks, saved pages, open tabs, tab groups, history, and (via Samsung Pass) passwords/passkeys across Galaxy phones, tablets, and now Windows PCs.
- **E2E-encrypted sync** (since v27).
- **Cross-device resume** — Windows/mobile "continue where you left off" prompts.
- **No Google-account sync** — sync universe is Samsung-account-only.

### AI features (Galaxy AI browsing assist)
- **Browsing Assist** (One UI 6.1+, Android 14+, Galaxy AI devices): **Summarize** — on-device AI page summaries with concise/detailed levels, plus translation of summaries; **Translate** — full-page on-device translation (20+ languages) that in-place replaces text including menus, ads, and text embedded in video.
- **Intelligent Summarization** improvements through 2025–2026 for long-form articles.
- **Ask AI** (March 2026, One UI 8.5, US/Korea first) — contextual Q&A about the page you're viewing. Powered by Perplexity APIs.
- **Ask Anything** (One UI 9, late 2026) — Ask AI renamed/expanded: answers questions using page context *and* browsing history.
- **Notes PDF** integration (One UI 9, in testing) — send page content into Samsung Notes as PDF.
- Requires Samsung Account sign-in; some features need network despite on-device models.

### Gestures & shortcuts
- **Swipe address bar / bottom bar left-right** — previous/next tab (phones; tablets use tab strip instead).
- **Pull-to-refresh.**
- **Video player gestures** — vertical swipes for brightness (left) / volume (right), double-tap seek.
- **Edge-swipe back/forward** — follows system gesture nav.
- **Long-press toolbar icons** for secondary actions (e.g., long-press tabs button for new tab/close tab options).

### Data saving & performance
- **No dedicated data-saver proxy** — content blockers are the de-facto data saver.
- **Auto-close unused tabs** reduces memory footprint.
- **AMOLED battery benefit** from forced dark mode is marketed as a power feature.
- **Chromium-current engine** (v30 tracks recent Chromium) though historically it lags Chrome's engine version by several releases — a recurring criticism.

### Extensions/add-ons
- **Two-tier model:** (1) open **content-blocker API** (Play Store apps, any Android device); (2) general **"Add-ons"** — translation tools, shopping assistants, security tools — distributed via **Galaxy Store** under "Samsung Internet Extensions," effectively Galaxy-device-only and a **closed beta** (only approved developers can publish).
- Legacy built-in add-ons: **Web contents provider / Amazon shopping assistant**, **Translator extension**.
- **Windows version supports full Chrome Web Store extensions** — mobile does not.

### Accessibility
- **Text size slider + webpage zoom** independent of system settings; "force zoom" on sites that block pinch-zoom.
- **High contrast mode** (Settings > Accessibility).
- **Reader mode** for clutter-free reading.
- **Read Highlight Aloud** TTS.
- Works with TalkBack; large-touch-target bottom UI is itself an accessibility win.

### Unique extras (Knox, Samsung Pass, etc.)
- **Samsung Pass integration** — biometric web login and autofill (IDs/passwords/OTP/addresses/cards); credentials stored in the **Knox** hardware-isolated vault; biometrics never leave the device; passkeys stored in Samsung Pass.
- **Knox-hardened browsing** — Knox platform integration; managed-configuration support for enterprise.
- **Biometric web authentication (WebAuthn/FIDO)** — fingerprint/face sign-in to websites.
- **DeX support** — desktop-class windowed browsing in Samsung DeX.
- **Quick Exit** panic button with data wipe.
- **Continue on PC** — cross-device handoff with Samsung Browser for Windows.

## 3. UI/UX structure & feature placement

- **Address bar + bottom toolbar layout & customizability:** Default layout is address bar (top or bottom, user's choice) plus a persistent bottom toolbar. The toolbar holds **up to 7 buttons** chosen by the user: Settings > **Layout and menu > Customize menu** opens a two-pane editor — the current toolbar/menu on top, the catalog of all available functions below — and users **drag and drop any function into any slot and reorder freely**. Items that don't fit on the toolbar live in the overflow menu grid, which is re-orderable by the same drag-and-drop. Tab bar and address bar can be merged into one row ("Together") or split ("Separate"). This is the deepest toolbar customization of any mainstream mobile browser and the core of its one-handed pitch.
- **Tab switcher design:** Full-screen switcher opened from the toolbar tabs button; three selectable presentations — **List, Stack (rolodex), Grid** — plus tab-group sections with named, colored group headers; bottom row offers new tab / Secret mode toggle; three-dot menu inside the switcher exposes Group tabs, Edit, Share group, Close all. Tablets/foldables get a desktop-style persistent tab strip instead.
- **Main menu (the grid sheet):** The ≡ (or ⋮) button opens a bottom-sheet **icon grid** (two+ rows) containing roughly: Bookmarks, Downloads, History, Saved pages, Share, Find on page, Desktop site, Add-ons/Ad blockers, Text size, Dark mode, Translate, Print, Zoom, Video history, Settings, and (recent) AI actions. The grid contents and order are **user-customizable**; anything can be promoted to the toolbar or demoted to the sheet.
- **Settings: top-level sections in order (approximate):** Samsung account/sync → **Layout and menu** → **Homepage** → Search engine/Address bar → **Tabs** → **Privacy and security** (incl. Secret mode settings, Smart Anti-Tracking, Privacy dashboard) → Ad blockers / **Add-ons** → Sites and downloads → **Useful features** (Video assistant, dark mode on websites, open links in other apps, etc.) → **Accessibility** → Labs/experimental → About. A settings **search bar** (v27+) sits on top.
- **Long-press / context menus:** Long-press link → open in new tab / open in Secret tab / open in group, copy, share, save link, download; long-press image → save, copy, share, search-by-image; long-press text selection → copy, share, web search, **Translate**, (AI actions on Galaxy AI devices); long-press toolbar buttons for secondary actions; long-press Quick access icons to edit/remove.
- **Gestures supported:** address-bar/toolbar horizontal swipe = tab switching; pull-to-refresh; system edge-swipe back; video-player brightness/volume swipes and double-tap seek; pinch zoom (with force-enable option).
- **One-handed-use design choices:** everything critical is reachable at the bottom — bottom address bar option, bottom toolbar, bottom-sheet main menu that opens above the thumb, bottom-anchored tab switcher controls, swipe-on-bar tab switching so no reach to top corners. This bottom-first architecture (shipped years before Chrome tried it) is why the browser is a one-hand UX benchmark.

## 4. Standout features (what Samsung Internet does best)

- **Toolbar + menu customization** — no mainstream mobile rival lets users drag-and-drop reorder up to 7 toolbar buttons *and* the whole overflow menu.
- **Secret mode with biometric lock** — password/fingerprint/face gate on private browsing; more robust than Chrome's Incognito lock, with its own anti-tracking level and persistence options.
- **Content-blocker plugin ecosystem** — the only major Android browser with a third-party ad-block extension API (AdGuard, Adblock Plus, Crystal, Unicorn...), running since 2016.
- **Video Assistant** — pop-up player, casting, and in-player gestures on arbitrary web video.
- **Smart Anti-Tracking on by default + Privacy Dashboard** — strongest default privacy posture among default OEM browsers.
- **Galaxy AI Browsing Assist** — on-device summarize + seamless in-place full-page translation, now expanding to Ask AI / Ask Anything.
- **Bottom-first, one-hand ergonomics** — the industry reference for reachable mobile browser UI.
- **Tab management depth** — groups (synced), lock, three switcher layouts, auto-close stale tabs, and (One UI 9) true multi-window.
- **Samsung Pass/Knox biometric autofill & passkeys** — hardware-vault credentials with biometric web login.

## 5. Weaknesses & common criticisms

- **Galaxy-ecosystem lock-in:** best features require a Samsung account and often Galaxy hardware; on non-Samsung Androids it's a diminished product.
- **Extension ecosystem is shallow and gated:** mobile add-ons are a closed beta on Galaxy Store; content blockers are filter-list-only — no uBlock-style full extensions on mobile.
- **Engine lag:** Chromium base historically trails Chrome by multiple versions, raising security-patch and web-compat concerns.
- **No Google sync / weak cross-ecosystem sync.**
- **Forced dark mode breaks pages:** algorithmic darkening can produce unreadable link colors and contrast failures.
- **Video Assistant bubbles obscure page UI:** floating buttons famously cover sites' own controls.
- **News feed / promotional clutter:** region-dependent feed and Samsung-services promotion feel like bloat.
- **Reader mode is basic:** limited theming/background options.
- **Update pacing & feature fragmentation:** flagship features debut on newest One UI first, fragmenting the experience.
- **Declining market share:** from 7.4% (2019) to ~2.3–2.8% (2026), squeezed by Chrome defaults.

## 6. Recent additions (2024–2026)

- **Early 2024 (v24–25, One UI 6.1):** Galaxy AI **Browsing Assist** — on-device Summarize and full-page Translate.
- **Mid 2024 (v26):** screenshots allowed in Secret mode; home-screen site shortcuts improvements.
- **Late 2024 (v27):** **end-to-end encryption for synced data**; **settings search**; sync scope expansion.
- **2025 (v28–29):** new tab-management features; **auto-close unused tabs**; history sorting; redesigned home screen.
- **Oct 2025:** **Samsung Internet for Windows PC beta** — Chromium, Chrome extensions, Samsung Pass + Windows Hello autofill, cross-device sync, Browsing Assist AI; GA and global expansion through 2026.
- **Mar 2026 (One UI 8.5 / Galaxy S26 launch):** **rebrand to "Samsung Browser"** with new icon/identity; **Ask AI** contextual page Q&A (US/Korea first); improved translation engine (20+ languages).
- **Mid 2026 (One UI 9 previews/beta):** **multiple simultaneous browser windows**; **Ask Anything**; **toolbar blur/translucent redesign**; **Notes PDF** export integration; "Cross Device Resume" flag spotted.
- **Ongoing 2026:** v30.x on Android; expansion of Samsung Browser beyond Galaxy devices.
