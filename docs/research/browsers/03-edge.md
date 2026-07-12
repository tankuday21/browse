# Edge (Android)

## 1. Overview & positioning
- **Package:** `com.microsoft.emmx` (Google Play), Chromium/Blink-based (not WebView), synced release cadence with desktop Edge (~4-week major versions; v150 as of July 2026).
- **Positioning:** Microsoft's "AI browser" and full-service companion to Windows/desktop Edge — pitched as "the browser with Copilot built in." Differentiators vs. Chrome on Android: built-in Adblock Plus, mobile extension support, Microsoft Rewards earning, deep Copilot/Bing/MSN integration, and cross-device continuity with Windows.
- **Strategic direction (2025–2026):** aggressive consolidation around Copilot. In May 2026 Microsoft **retired the standalone "Copilot Mode"** and folded its AI features into the default browser UI on desktop and mobile. Simultaneously it has been **pruning legacy features** (Collections and Sidebar deprecated around Edge 149; **Drop retirement announced June 2026**) to "make room for Copilot."
- Target user: Microsoft-account households, Windows users, Rewards point earners, and enterprise (extensive Intune MAM policy surface on Android — release notes are dominated by enterprise policies like `ExtensionInstallForcelist`, `DownloadBlockedForFileTypes`, `CopilotNewTabPageEnabled`).
- Market share on Android remains small single-digits; the app is heavily promoted via Windows, Bing, and Rewards funnels.

## 2. Feature inventory by category

### Navigation & address bar
- Combined search/URL omnibox with Bing default; search suggestions, site suggestions, Copilot suggestions.
- **Top or bottom address bar** — user-selectable position (bottom option shipped early 2025, long-requested).
- Voice search microphone in the address-bar keyboard panel.
- Camera icon: **visual search (Bing Lens-style) + QR/barcode scanning** from the address bar; QR scanner also in main menu (v150 moved scanning to Edge's built-in camera UI).
- Copilot **Summary notification chip in the address bar** (page-summary entry point, 2026).
- Swipe horizontally on the address bar to switch tabs (Chrome-style; extended to bottom omnibox).

### Tabs & tab management
- Grid tab switcher with normal / InPrivate panes; "+" for new tab, close-all option.
- **Tab groups on Android** (rolled out from Canary flag `Edge Tab auto-grouping` to stable): auto-grouping, manual add/remove, rename group, ungroup, hide/collapse groups — near desktop parity.
- Recently closed tabs / reopen; switch-tabs via address-bar swipe.
- Copilot can **reason across all open tabs** (compare, summarize, answer questions) — 2026.
- "Continue on PC"-style cross-device tab handoff via Microsoft account sync (send tabs between phone and desktop).

### Home / New-tab page (feed)
- NTP: search box, wallpaper, quick-link shortcut tiles (top sites + user pins; enterprise `ManagedTopSites`/`ManagedNtpShortcuts`), weather card, and an **MSN (Microsoft Start) news feed** below the fold.
- Three layout presets via NTP gear icon: **Focused / Inspirational (Bing daily image) / Informational (full news dashboard)**, plus custom; feed can be turned off.
- **Copilot New Tab experience** (2026): Copilot prompt card / Copilot-centric NTP rolled from desktop to mobile; a "Copilot New Tab Card" surfaces prompts and continuation of prior tasks (policy-controllable via `CopilotNewTabPageEnabled`).
- Rewards and shopping promos appear on the NTP in some markets.

### Search & discovery
- Bing default (switchable to Google, Yahoo, DuckDuckGo, etc. in Settings → General → default search engine).
- Visual search from camera icon; voice search; QR scan.
- Text-selection "search in sidebar / web search" context action.
- Bing Rewards-earning searches count on mobile Edge.
- Journeys (see AI) doubles as history-based re-discovery.

### Privacy & security (tracking prevention tiers)
- **Tracking Prevention with three tiers — Basic / Balanced (default) / Strict** — same Trust Protection List engine as desktop, under Settings → Privacy and security.
- InPrivate browsing mode (with option to lock/require authentication on some builds).
- Microsoft Defender **SmartScreen** phishing/malware protection.
- Password Manager with autofill, password monitor/leak alerts tied to Microsoft account sync.
- Clear browsing data on exit options; site permissions per-site; cookie controls; "Do Not Track" toggle.
- Enterprise: Intune MAM containerization, conditional access, `IncognitoModeAvailability`, allowed/blocked URL policies — the deepest MDM surface of any Android browser.
- Criticism noted in privacy audits: heavy telemetry defaults; personalization + ads tied to Microsoft account (see §5).

### Ad/content blocking (built-in AdBlock Plus)
- **Built-in Adblock Plus content blocker** — a bundled ABP engine, toggleable ("Block ads"), with Acceptable Ads implications; a genuine differentiator vs. Chrome.
- The "Block Ads" toggle was **hidden/relocated during the 2025 settings redesign** (Settings → Privacy and security → Content blockers on current builds); Microsoft says the engine wasn't removed, just re-homed — caused user confusion.
- Ad blocking can now also be supplemented with **real ad-block extensions** (uBlock Origin Lite, AdGuard, Ghostery) via mobile extension support — AdGuard was among the first partners.
- Standard pop-up blocker.

### Downloads & media
- Download manager with in-app list, notifications, "Ask where to save," background downloads; PDF download-to-OneDrive integration.
- Built-in **PDF reader** (with Copilot PDF summarization); PDF print.
- **Picture-in-Picture overlay** for supported video pages (policy `PictureInPictureOverlayEnabled`, 2026).
- Copilot **Video Summary** for videos incl. YouTube (2026).
- Files can be shared to OneDrive; media autoplay controls per site.

### Reading & content (immersive reader, read aloud, translate)
- **Immersive Reader** reading view (icon in address bar on compatible pages): decluttered text, text size/spacing, themes, grammar tools.
- **Read Aloud** with natural neural voices, works on pages and PDFs; controllable by `ReadAloudEnabled` policy (2026) — works with screen off in some builds.
- **Microsoft Translator** built in: full-page translate prompts and on-demand translation of selection or page.
- Copilot page **Summarize** (address-bar chip / menu action).

### Customization & appearance
- Light/dark/system theme; NTP wallpaper/background images; NTP layout presets.
- **Address bar position top/bottom.**
- **Customizable main menu**: drag-and-drop reorder of actions ("Change menu"/edit button); grid vs. list presentation (layout varies by rollout and system font size).
- Text scaling/page zoom; homepage button configuration.
- Redesigned toolbar (2025–2026): re-arranged bottom bar with Share button replacing "Continue on PC," centered ellipsis menu.

### Sync & account (Drop, Collections, cross-device)
- Microsoft-account sign-in (MSA + Entra ID work profiles, multi-profile with easy identity switching).
- **Sync**: favorites, passwords, addresses/payment, open tabs, history, Collections (toggleable per data type); "Reset sync server data" option added 2025.
- **Collections on mobile**: save pages/notes to synced Collections via menu → "Add to Collections" — **but Collections is being deprecated** (removed from desktop around Edge 149; mobile long-term future doubtful).
- **Drop**: send-to-self files/notes thread synced via OneDrive, available in mobile menu — **officially being retired (announced June 2026)**; text notes will be deleted, files remain in OneDrive.
- Cross-device: send tab to devices, shared password autofill, continue browsing on PC.

### AI features (Copilot)
- **Copilot chat built in** (dedicated button/menu entry; formerly Bing Chat): Q&A, composition, image understanding, GPT-class models; free tier plus Copilot Pro benefits.
- **Copilot Mode retired May 2026** — AI features now integrated throughout the default UI.
- **Multi-tab reasoning**: Copilot analyzes/compares/summarizes across open tabs; can continue tasks started on desktop.
- **Journeys on mobile** (2026): organizes browsing history into topic-based cards with summaries and suggested next steps.
- **Copilot Vision & Voice on mobile** (2026): share your screen and talk through what you're looking at, hands-free.
- **Copilot long-term memory** across sessions (2026).
- Page/PDF/video **summarization**; Copilot Summary address-bar notifications; Copilot prompt card on NTP.
- New 2026 experiments: turn open tabs into **podcasts**, generate **quizzes** from webpages.
- Enterprise-grade "AAD Copilot" (work-account Copilot with commercial data protection) on Android.

### Gestures & shortcuts
- Swipe address bar left/right to switch tabs (works with bottom omnibox).
- Swipe down from address bar for tab management/switcher (build-dependent).
- Pull-to-refresh on pages.
- Long-press links/images for context menus; long-press tab-switcher and toolbar buttons for quick actions (e.g., new InPrivate tab).
- Known annoyance: tab-switch swipe order ignores tab-group visual order.

### Data saving & performance
- No classic proxy "data saver" mode (the old Turbo/data-compression died with legacy Edge); efficiency comes from ad/tracker blocking reducing payloads.
- Startup/parallel tab-load tuning (`AntiParallelOnStartUpEnabled`), background tab throttling inherited from Chromium.
- Criticized as heavier (APK size, RAM, battery) than Chrome/Firefox due to bundled services (Rewards, shopping, Copilot, MSN feed).

### Extensions/add-ons (mobile extension support!)
- **Official extension support on Android** — flagship differentiator. Piloted late 2024 (~20 curated add-ons: uBlock, Dark Reader, Tampermonkey, AdGuard, Bitwarden-class), **official global rollout March 2025**, on by default for Android 11+ by 2026.
- Dedicated mobile **Edge Add-ons store**; curated catalog (~87 approved of 2,400+ submitted as of March 2026) — approval requires mobile-safe UI, no battery-draining background scripts, no Android system-API access.
- Late-2025 beta expanded compatibility toward "virtually any" desktop-class Edge/Chromium extension (thousands installable), though not full parity — some render poorly on mobile.
- Enterprise `ExtensionInstallForcelist` policy for silent extension deployment on Android (v150, July 2026).

### Accessibility
- Read Aloud + Immersive Reader (core accessibility pair).
- Text scaling/zoom overriding site restrictions; respects Android font-size/bold accessibility settings (which even flips menu grid↔list layout).
- TalkBack screen-reader support; high-contrast/dark theme; Copilot Voice/Vision as hands-free assistive interaction.

### Shopping/Rewards/extras
- **Microsoft Rewards integrated**: join/track points in Settings → My profile → Microsoft Rewards; earn on Bing searches in mobile Edge; redeem for gift cards etc. (select markets).
- Shopping: price comparison, **price history and price-drop alerts**, cashback in supported regions; native Bing-powered coupon experience was ported to Android — **the standalone Coupons feature was retired (2025)** in favor of cashback/price tools.
- Extras: built-in games (Surf game — now policy-gated `AllowSurfGame`), Bing daily wallpapers, MSN content ecosystem, Web Capture/screenshot tool, share sheet with QR-code page sharing, "Send to devices."

## 3. UI/UX structure & feature placement

- **Address bar position and contents:** Top by default, **user-movable to bottom** (Settings → appearance/address bar). Contains: site security/lock (page info incl. SSL cert info), URL/search field, refresh; focused state shows voice mic + camera (visual search/QR) above keyboard; Copilot Summary chip can appear in the bar. Swiping the bar switches tabs.
- **Bottom toolbar layout:** Persistent bottom bar (Edge's signature vs. Chrome) — typically **5 slots: Back · Forward (or Home) · Tab switcher (count badge) · Copilot (center-ish, replaced older hub/"Continue on PC"; a Share button appeared in the 2025 redesign) · "…" ellipsis menu**. The 2025–2026 redesign moved the ellipsis toward center and swapped Continue-on-PC for Share. The toolbar itself is only lightly customizable; deep customization lives in the menu sheet.
- **Tab switcher design:** Full-screen grid of tab thumbnails; toggle between normal and **InPrivate** spaces; tab-group cards with rename/ungroup/hide via group ellipsis menu; + new tab, close-all, and overflow menu.
- **Main menu (customizable bottom-sheet grid):** Opens from "…" as a sheet — historically a **paged icon grid**, being A/B-migrated to a **reorderable list** ("Change menu" enables drag-and-drop; grid vs. list also depends on system font size and rollout). Typical items: New tab · New InPrivate tab · Add to favorites · Favorites · History · Downloads · Collections · Copilot · Share · Find on page · Translate · Read aloud · Desktop site toggle · Add to phone (home screen) · Drop (being retired) · QR scanner/scan code · Web capture · Night/dark mode · Rewards · Settings · Exit. Top row of the sheet shows account/profile switcher.
- **Settings screen — top-level sections (post-2025 redesigned settings, approximate order):** Profile/account card (sign-in, sync, **Microsoft Rewards** under My profile) → **General** (default search engine, homepage, address bar position, default browser) → **Accounts/Sync** → **Appearance** (theme, menu/toolbar) → **New tab page** → **Privacy and security** (tracking prevention tiers, content blockers/Block ads, clear data, InPrivate, Do Not Track) → **Passwords/Autofill** (payments, addresses) → **Site permissions** → **Downloads** → **Languages/Translate** → **Accessibility** → **Notifications** → **Copilot/AI settings** → **About**. (Exact order shifts across the ongoing settings-UI migration — a documented source of user complaints.)
- **Long-press / context menus:** Links: open in new tab / new InPrivate tab / open in group, copy link, share, download link, preview. Images: open, save, share, **visual search with the image**, copy. Text selection: copy, share, web search, translate, Copilot/ask. Address bar long-press: paste/copy URL. Home-screen app icon long-press: shortcuts (new tab, InPrivate tab, search).
- **Gestures supported:** pull-to-refresh; address-bar horizontal swipe = tab switch; swipe down from bar = tab UI (build-dependent); pinch zoom; back-gesture integration with Android navigation; swipe tabs closed in switcher.
- **First-run/onboarding flow:** Splash → Microsoft account sign-in prompt (auto-suggests device MSA; skippable) → sync opt-in → data/telemetry + personalization consent (region-dependent) → **set-as-default-browser prompt** (Android role manager sheet) → NTP feed/layout intro; 2026 builds add a **Copilot FRE (first-run experience) screen for MSA users** introducing AI features. Recurrent nudges thereafter: sign-in banners, default-browser prompts, Rewards/Copilot promos — a common annoyance theme.

## 4. Standout features (what Edge does best)
- **Mobile extension support** — the only mainstream Chromium browser on Android with an official, growing extension store (uBlock Origin Lite, Dark Reader, AdGuard, Tampermonkey…), heading toward desktop-catalog compatibility.
- **Built-in Adblock Plus** ad blocking out of the box — no setup, unlike Chrome.
- **Deepest mobile AI integration of any major browser**: Copilot multi-tab reasoning, page/PDF/video summarization, Journeys, Vision + Voice screen-sharing assistant, long-term memory, tabs-to-podcast/quiz experiments.
- **Microsoft Rewards earning while browsing/searching** — effectively pays users; unique among browsers.
- **Cross-device continuity with Windows**: tab/password/favorites/Collections sync, send-to-PC, shared Copilot context.
- **Bottom address bar option + persistent bottom toolbar** — strong one-handed ergonomics.
- **Reading stack**: Immersive Reader + neural Read Aloud + full-page Translate is best-in-class on mobile.
- **Enterprise manageability**: unmatched Intune/MAM policy coverage on Android (forced extensions, download blocking, managed NTP, Copilot controls).
- Shopping tools (price history, price alerts, cashback) built in.

## 5. Weaknesses & common criticisms
- **Bloat/clutter**: Rewards, shopping, MSN feed, Copilot everywhere — reviewers call it overwhelming vs. minimalist rivals; "Microsoft services detract from focused browsing."
- **Aggressive promotion**: persistent sign-in, default-browser, Bing/Copilot and Rewards nudges; "Stay in Microsoft Edge" interstitials hijacking external-app redirects irritate users and break some OAuth flows.
- **Feature churn/removals**: Collections, Sidebar, Coupons, and now **Drop** all deprecated in ~18 months to clear room for Copilot — erodes trust in investing in Edge features; Drop text notes get deleted.
- **Settings redesign confusion**: options (notably the Block Ads toggle) moved or hidden mid-2025; grid-vs-list menu inconsistency across identical devices.
- **Privacy reputation**: heavy telemetry defaults, Bing/MSN data coupling, ABP "Acceptable Ads" model; privacy guides recommend extensive toggling.
- **Performance/resource footprint**: heavier RAM/battery/APK than Chrome; users report the mobile engine trailing desktop Chromium in features/security cadence.
- **Extension catalog still curated/small** in the official store (~87 approved as of March 2026), with mobile-rendering issues for desktop-class extensions in the expanded beta.
- **Tab UX rough edges**: swipe-to-switch ignores tab-group ordering; tab opening direction inconsistencies.
- No true data-saver mode; AI features gated by Microsoft account (and best experience by Copilot Pro).

## 6. Recent additions (2024–2026)
- **Late 2024:** extension support pilot on Android (curated ~20 add-ons); Copilot (Bing Chat successor) embedded in bottom bar; tab auto-grouping in Canary.
- **Feb–Mar 2025:** **bottom address bar option** ships (Feb 2025); **official global extension rollout** (Mar 2025); "Reset sync server data"; toolbar redesign begins (Share replaces Continue-on-PC, centered ellipsis); customizable/reorderable actions menu experiments; native Bing coupon experience replaces Honey-style coupons (then Coupons retired outright).
- **Mid–late 2025:** settings UI overhaul (Block Ads toggle relocated); tab groups mature toward stable (rename/ungroup/hide); Copilot PDF summarization and YouTube/video integration; October 2025 beta expands extension compatibility toward the full desktop catalog.
- **Early 2026:** Copilot Mode FRE for MSA users; Copilot prompt card on NTP; Copilot Summary notification in address bar (v145, Mar 2026); multi-tab summary + video summary polish (v148, May 2026); `CopilotNewTabPageEnabled`, `ReadAloudEnabled`, `EdgeEDropEnabled`, PiP overlay policies; extensions default-on for Android 11+.
- **May 2026:** **Copilot Mode retired; AI integrated into default UI** — multi-tab reasoning, **Journeys on mobile**, **Vision + Voice on mobile**, long-term memory, tabs-to-podcast, quiz generation; desktop-style new-tab page to mobile (Edge blog, May 13, 2026).
- **June–July 2026:** **Drop retirement announced** (following Collections/Sidebar deprecation ~Edge 149); v150 (July 2026): `ExtensionInstallForcelist` on Android, QR scanning via built-in Edge camera, Copilot Summary/New Tab Card refinements.
