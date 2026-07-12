# Brave (Android)

## 1. Overview & positioning
- **Positioning:** Privacy-first Chromium browser whose core pitch is "block everything by default" — ads, trackers, fingerprinting — with an opt-in attention economy (BAT rewards) and a growing privacy-preserving AI stack (Leo, Brave Search). Marketed as "3x faster than Chrome" because blocked ads/trackers mean fewer network requests and less CPU/battery/data.
- **Engine:** Chromium (Blink). Tracks Chromium closely — as of mid-2026 stable Android is ~1.91–1.92 on Chromium 149–150.
- **Scale:** ~65M+ MAU across platforms per 2026 comparisons; consistently ranked the top privacy pick among Android browsers in 2026 roundups.
- **Business model:** Free browser; monetized via opt-in Brave Ads/Rewards (BAT), Brave Search ads, and paid subscriptions: Leo Premium ($14.99/mo), Brave Firewall+VPN ($9.99/mo), Search Premium, and — new in 2026 — **Brave Origin**, a $59.99 one-time-purchase "de-bloated" browser variant with the crypto/AI/news extras stripped out.

## 2. Feature inventory by category

### Navigation & address bar
- Combined omnibox (URL + search + suggestions) with "Ask Leo" entry — typed queries can be routed to Leo AI directly from the address bar.
- Address bar position: **top by default, bottom optional** — toggle in Appearance settings or long-press the bar.
- Shields (lion) icon lives inside the address bar; tap for the per-site Shields panel.
- **Custom toolbar shortcut** (v1.85+): a configurable toolbar button giving one-tap access to a chosen feature.
- Swipe left/right on the toolbar (top or bottom) to switch tabs.
- De-AMP (bypass Google AMP pages to publisher's real URL), debouncing (skip known tracking redirect hops), tracking-parameter stripping on navigation.

### Tabs & tab management
- Chromium-style grid tab switcher with separate **Private tabs** pane; tab groups supported.
- Recent tabs / synced tabs from other chain devices.
- Fixes in 2026 for foldables and dynamic-theme tab-switcher colors.

### Home / New-tab page
- NTP shows **Privacy Stats**: lifetime ads/trackers blocked, bandwidth saved, time saved (headline Brave differentiator).
- **Background images** and **Sponsored Images** (full-page NTP ad backgrounds; earn BAT if Rewards is on). Toggles: Settings → Display → New Tab Page.
- Top Sites row beneath stats; **pinned website shortcuts** addable to NTP since v1.86.
- **Brave News**: private, on-device-personalized news feed built into the NTP scroll; can be disabled.

### Search & discovery (Brave Search)
- Brave Search is the default engine (independent index; no Google/Bing dependency); default can be changed; custom search engines supported.
- Separate default engine settable for **standard vs private tabs**.
- **Ask Brave** (Sept 2025→2026): unified search+AI chat — chat follow-ups, and a **Deep Research mode** running multi-round searches over Brave's index; free.
- **Rerank**: user-side boosting/demoting/removal of domains in search rankings.
- Web Discovery Project: opt-in, anonymous contribution of browsing data to improve the Brave Search index.

### Privacy & security (Shields)
- **Shields master toggle per site** (lion icon): per-site overrides of global defaults set at Settings → Brave Shields & privacy.
- **Trackers & ads blocking: Disabled / Standard / Aggressive.** Standard = network filtering + cosmetic filtering tuned for compatibility; **Aggressive blocks everything incl. first-party ads/trackers** and hardens against filter-circumvention scripts.
- **Fingerprinting protection:** blocks/modifies fingerprintable APIs so Brave instances look alike, plus **randomizes API outputs ("farbling")** per-site-per-session so a fingerprint can't link you across sites/sessions.
- **HTTPS by default / Upgrade connections to HTTPS**, with a strict mode that blocks plain-HTTP fallbacks.
- **Script blocking:** global "Block scripts" toggle with per-site allow; blocked-script count viewable/re-enable-able from the Shields panel.
- **Cookie controls:** block third-party cookies (default) or all cookies, per-site; ephemeral/partitioned third-party storage.
- **Forgetful Browsing:** per-site (or global) "forget me when I close this site" — auto-clears cookies/storage and logs you out when last tab of a site closes.
- **Bounce-tracking defenses:** query-parameter stripping, **debouncing**, **unlinkable bouncing**.
- **Block cookie-consent banners** and notification-permission annoyances via dedicated filter lists.
- Social media blocking: Google/Facebook login-button and embed blocking toggles.
- Safe Browsing; private tabs can be **locked behind fingerprint/biometrics**.
- **No Tor on Android** — Private Window with Tor is desktop-only.

### Ad/content blocking (industry benchmark — how it works)
- Native **Rust-based adblock engine** (adblock-rust) running in the network stack — not an extension — so it's faster and can't be killed by Manifest V3; the key structural advantage vs Chrome on Android.
- **Filter lists:** ships with EasyList, EasyPrivacy, uBlock Origin lists, and Brave-maintained lists; **Filter lists page** lets users enable dozens of regional/annoyance lists, **subscribe to custom filter-list URLs, and write their own custom filter rules** — full uBlock-style syntax incl. cosmetic rules.
- **Cosmetic filtering:** hides page elements matching CSS selectors from filter lists, so pages reflow cleanly.
- **Block Elements on Android (v1.78, 2025):** tap-to-hide arbitrary page elements — Brave was the **first major mobile browser** with interactive element zapping; selections saved as per-site custom cosmetic rules.
- **Scriptlet injection** (uBO-style "resource replacement") to neutralize anti-adblock and tracking scripts; procedural cosmetic filters supported.
- YouTube-specific hardening continued through v1.87+ (better ad blocking, PiP, background play).

### Downloads & media
- Standard Chromium download manager, download-later/parallel downloading flags.
- **Background video/audio playback** — free, built-in (Settings → Media → Background video playback): keep YouTube etc. playing with screen off — a flagship Android feature.
- **Brave Playlist:** save video/audio into an in-browser playlist for later/**offline** playback (Android arrival lagged iOS; some bugs).
- Picture-in-picture supported.

### Reading & content (Speedreader)
- **Speedreader is effectively desktop-only.** On Android it exists only behind flags and is widely reported broken; no shipped reader mode on Android as of 2026 — a recurring user complaint.
- Page translate (Brave-hosted translation) available on Android; Leo can summarize pages as a workaround.

### Customization & appearance
- Theme: light/dark/system; **dynamic (Material You) theming** via flag (2026).
- **Night Mode (experimental)**: forces dark rendering of web content, with occasional rendering glitches.
- Bottom vs top address bar; **customizable main menu** (v1.84 — show/hide/reorder menu items); custom toolbar shortcut (v1.85).
- NTP background/sponsored image toggles, Brave News on/off, stats widgets on/off.
- Improved large-screen UI for tablets/foldables (2026).

### Sync & account (Brave Sync chain)
- **No account, ever.** Sync uses a "Sync chain": a device generates a 25-word seed phrase (shown as a **QR code for phones**); all devices holding the seed form the chain.
- **Client-side (end-to-end) encryption** — Brave's servers store only ciphertext; sync categories selectable: bookmarks (default), history, passwords, autofill, open tabs, settings.
- Known criticism: history sync is partial, and community reports of sync flakiness persist.

### AI features (Leo)
- **Leo on Android since v1.63**: page/video summarization, Q&A about the current page, translation, content generation — from the address bar ("Ask Leo") or menu → Leo.
- **Privacy architecture:** no account needed, chats not stored server-side or used for training, requests routed through an **anonymization proxy**; zero-retention model hosting.
- **Free tier** (2026): Llama 3.1 8B, Claude Haiku, Qwen 3 14B, and other open models. **Premium $14.99/mo:** Claude Sonnet/Opus, DeepSeek, and more; higher rate limits.
- **Automatic Mode** (2026): Leo auto-picks the best model per query.
- Android-specific Leo (2025–26): quick-actions/voice widget, personalization + memories, @-mention open tabs, attach files/PDFs to chats.

### Gestures & shortcuts
- Swipe horizontally on the toolbar/address bar to switch tabs; swipe up on bottom address bar opens the tab grid.
- Pull-to-refresh (not user-disableable — a complaint thread exists).
- Long-press address bar to reposition; long-press tab-switcher button for new tab / new private tab / close tab.
- Android app-shortcuts (long-press launcher icon): new tab, new private tab; Leo in quick settings (v1.82).

### Data saving & performance
- No proxy-based "lite mode" — data savings come from blocking: NTP stats quantify **bandwidth saved**.
- Benchmarks: ad-heavy pages 2–3x faster than Chrome, ~44% lower RAM in 2026 head-to-heads; native Rust blocking engine keeps overhead minimal.

### Rewards/Web3 (BAT, wallet)
- **Brave Rewards (opt-in):** view privacy-preserving Brave Ads → earn BAT; on-device ad matching, no profile leaves the phone.
- **Rewards 3.0 (Q1 2025):** redesigned UX; **self-custody payouts** via Brave Wallet Solana address (v1.81).
- **BAT Roadmap 3.0 "On-Chain Era" (2026):** BAT ecosystem moving on-chain; BAT usable for Brave premium products.
- **Brave Wallet:** built-in multi-chain crypto wallet — Ethereum/EVM, Solana, Bitcoin; buy/send/swap, NFTs, dApp connections.
- Creator tipping to verified sites/channels.

### Accessibility
- Inherits Chromium accessibility: TalkBack support, page zoom with force-enable zoom, text scaling.
- Dedicated **Accessibility** section in settings; night/dark modes assist low-vision users; known gap: no reader mode on Android hurts accessibility use-cases.

### Unique extras
- **Tor Private Windows: desktop-only.**
- **Secure DNS (DNS-over-HTTPS)** configurable on Android.
- **Brave Firewall + VPN** ($9.99/mo, 10 devices): whole-device VPN + tracker-blocking firewall; **Smart Proxy Routing** (2025); kill switch not on Android.
- **Brave Talk** (private video calls); Wayback Machine 404 fallback (desktop-centric).
- **Brave Origin** (2026): the paid, stripped-down sibling — no Rewards/Wallet/Leo/News/Talk/sponsored images/telemetry.

## 3. UI/UX structure & feature placement
- **Address bar:** top by default; bottom option via Appearance settings / long-press. Shields lion icon sits in the address bar (left of the URL).
- **Shields panel (tap lion):** site name + big Shields up/down toggle; counters for trackers & ads blocked (tappable list) and blocked scripts; "Advanced controls" expander → per-site: trackers & ads level, HTTPS upgrade, block scripts, cookie blocking level, fingerprinting blocking, **Forgetful Browsing** toggle, **Block Elements** entry; link to global Shields defaults and filter management.
- **Tab switcher:** grid of thumbnails, Chromium-style; tab-groups support; toggle between normal and Private panes.
- **Main menu (⋮, customizable/reorderable since v1.84)** — representative default order: New tab · New private tab · History · Delete browsing data · Downloads · Bookmarks · Recent tabs · Share · Find in page · Translate · Add to Home screen · Desktop site toggle · Night Mode · Brave Leo · Brave Wallet · Brave Rewards · Brave VPN · Playlist · Print · **Customize menu** · Settings · Exit. (Feature rows like Wallet/Rewards/VPN/Leo are the ones users most often hide.)
- **Settings top-level structure (approximate order):** a **Features** block — Brave Shields & privacy · Brave Rewards · Brave News · Brave Wallet · Brave Leo · Brave VPN · Brave Playlist — followed by **General**: Search engines · Brave Sync · Passwords · Payment methods · Addresses · Privacy and security · Notifications · Site settings · Media · Downloads · Home page · Tabs · **Display/Appearance**: Appearance (incl. Night Mode, bottom bar) · New Tab Page · Theme · Accessibility · Languages · **Advanced**: About Brave.
- **Long-press / context menus:** links (open in new/private/group tab, copy, download, share, "summarize with Leo" entries in 2025+ builds); images (download, search with Brave, share); text selection (copy/share/translate/Ask Leo).
- **Gestures:** toolbar swipe = tab switch; swipe-up on bottom bar = tab grid; pull-to-refresh; swipe-to-dismiss tabs in switcher.

## 4. Standout features (what Brave does best)
- **Best-in-class native ad/content blocking on Android** — Rust engine + EasyList/uBO lists + scriptlets + cosmetic filtering + custom lists/rules, immune to MV3; the benchmark all other mobile browsers get measured against.
- **Block Elements tap-to-zap** (first major mobile browser with interactive element hiding).
- **Fingerprint randomization (farbling)** — genuinely differentiated anti-fingerprinting.
- **Free background video playback + built-in adblock on YouTube** — replicates YouTube Premium's two headline conveniences at $0; the single biggest Android-specific draw.
- **Leo AI with a real privacy architecture** (proxied, no logs, no account, model choice).
- **Accountless, E2E-encrypted Sync chain.**
- **Independent search index + Ask Brave/Deep Research** — the only major browser vendor with its own index.
- **Layered navigation-tracking defenses** (De-AMP, debouncing, unlinkable bouncing, param stripping, Forgetful Browsing) that no other Android browser matches as a package.

## 5. Weaknesses & common criticisms
- **Crypto/Web3 clutter:** Rewards, Wallet, BAT and sponsored NTP images feel like bloat to many; so persistent that Brave productized the complaint as paid **Brave Origin** — itself criticized ("paying $60 to remove what a group policy can disable").
- **Feature sprawl / disruptive updates:** menu and UI churn; each release adds toggles; reviews cite "inconsistency and clutter."
- **No reader mode / Speedreader on Android** (flag-only and broken) — notable gap vs Firefox/Samsung Internet.
- **No Tor tabs on Android**, despite years of requests.
- **Sync reliability:** partial history sync, occasional lost settings/passwords reports; no cloud account fallback if the chain breaks.
- **Playlist lagged years behind iOS** on Android; offline playback bugs.
- **Site breakage** under Aggressive Shields/fingerprint randomization; occasional YouTube cat-and-mouse breakage windows.
- **Past trust dings still cited:** 2020 affiliate-link autocomplete incident, opt-out sponsored images.
- **No extension support on Android** (unlike Firefox); built-ins must cover everything.

## 6. Recent additions (2024–2026)
- **2024:** Leo on Android (v1.63, Jan 2024); Leo model expansion; bottom-address-bar improvements.
- **Early–mid 2025:** **Rewards 3.0** redesign; **Block Elements on Android** (v1.78); custom filter lists via Android settings.
- **Mid–late 2025:** **Self-custody BAT payouts on Solana** (v1.81); **Leo quick actions/voice widget + personalization/memories/@-tab-mentions** (v1.82); VPN Smart Proxy; **Ask Brave** (Sept 2025); **customizable main menu + CSV password import + Leo PDF attachments** (v1.84).
- **Late 2025–2026:** **custom toolbar shortcuts** (v1.85); **pin website shortcuts on NTP + new Leo models** (v1.86); **YouTube improvements — better ad blocking, PiP, background play** (v1.87); dynamic Material-You theming flag; Leo **Automatic Mode**; **Brave Search Rerank** and Ask Brave **Deep Research**; **BAT Roadmap 3.0**; **Brave Origin** launched June 2026; Chromium 145→150 track.
