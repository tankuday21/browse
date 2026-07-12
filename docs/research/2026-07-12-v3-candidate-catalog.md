# Andromeda v3 Candidate Catalog — everything we can build, enhanced

**Date:** 2026-07-12 · **Input:** [browser landscape research](2026-07-12-browser-landscape.md) · **Status:** discovery material, pre-spec

Every feature below is **verified buildable on WebView** (engine-impossible items are excluded — see report §5). Each entry has an ID (for picking scope: "I want B1, G1, H3"), the browser that does it best today, **our enhancement — how Andromeda goes beyond them**, and effort (S = ~a day · M = days · L = phase-sized · XL = multi-phase).

> **The one honest warning from the research:** the browsers criticized hardest in 2026 are the ones that added everything (Edge "bloat/clutter", Opera "kitchen-sink", Brave "feature sprawl" — their own users say it). The winners are *curated*: every feature discoverable, nothing shouting. So we build MANY of these — but in coherent releases, each feature polished before the next lands, and always removable/toggleable. Combining every browser's features is the roadmap; curating them is the product.

---

## A. Omnibox & navigation

| ID | Feature | Best today | Our enhancement | Effort |
|---|---|---|---|---|
| A1 | Address bar top/bottom choice | Firefox (onboarding ask) | Orbit Command Bar stays a floating pill in both positions — nobody else's bottom bar is this distinct | M |
| A2 | Voice search | Chrome | Straight to results in current engine, no Google UI detour | S |
| A3 | QR/barcode scanner in bar | Edge/Samsung | Scan → smart action (URL opens, text searches, Wi-Fi joins) | M |
| A4 | **Omnibox power mode**: `@tabs` / `@history` / `@bookmarks` scoping, custom search keywords (`w kotlin` → Wikipedia), inline calculator & unit converter, quick actions ("clear data", "new incognito") | Desktop-only (Chrome/Firefox); **no mobile browser does this** | First-on-mobile full @-scoping — a genuine "wow" for power users | L |
| A5 | Bar long-press: copy URL / paste-and-go / share | Chrome | Plus "send to another Orbit profile" once D7 lands | S |
| A6 | Clipboard URL suggestion chip | Firefox | Auto-offer with privacy: read clipboard only on bar focus | S |

## B. Tabs — the biggest table-stakes gap

| ID | Feature | Best today | Our enhancement | Effort |
|---|---|---|---|---|
| B1 | **Tab groups** — named, colored, collapse in switcher | Chrome/Opera | Opera-style auto-Islands (links opened from a page auto-group) + manual drag-to-group; group = one card stack in our thumbnail switcher | L |
| B2 | Tab search | Chrome/Opera | Searches title, URL, **and page content of loaded tabs** | M |
| B3 | Recently closed / undo close | Opera (100 deep) | 100-deep, restores whole groups, one swipe from switcher | M |
| B4 | Inactive-tab auto-archive | Chrome (21 days) | Archive keeps the thumbnail — a visual "time capsule" shelf, not a text list | M |
| B5 | Pinned tabs + tab lock (no accidental close) | Samsung | Pin = tiny orbit dot on the Command Bar edge | M |
| B6 | Switcher layouts: grid / list / stack | Samsung (3 views) | Our thumbnails already beat most; add list for 50-tab users | M |
| B7 | Multi-select bulk actions (close/group/share) | Chrome | Share N tabs = one formatted list | M |
| B8 | **Workspaces** — save/restore named tab sets ("Trip", "Project") | Firefox Collections / Edge desktop-only | **No mobile browser has true workspaces** — ours persist, restore lazily (thumbnails first, load on tap), and export/import as files | L |
| B9 | Tab badge easter egg at 100+ tabs | Chrome ":D" | Ours shows "∞" — on-brand | S |

## C. Home / new-tab

| ID | Feature | Best today | Our enhancement | Effort |
|---|---|---|---|---|
| C1 | Editable shortcut grid (pin/reorder/remove, not just bookmarks) | Samsung Quick access | Long-press to edit, folders as "constellations" | M |
| C2 | Wallpapers / Orbit theme packs | Firefox/Brave | Generated space gradients from our palette — zero licensing | M |
| C3 | **Privacy stats block** — ads/trackers blocked, data saved | Brave (their #1 brand surface) | We already count blocks; add estimated MB + time saved. Free brand value | S |
| C4 | "Continue browsing" resume card | Chrome | Shows last reading-list article too (with H1) | M |

## D. Privacy & security

| ID | Feature | Best today | Our enhancement | Effort |
|---|---|---|---|---|
| D1 | Safe Browsing (phishing/malware warnings) | Chrome | One line of code (`setSafeBrowsingEnabled`) + our own warning page styling | S |
| D2 | Cookie-banner auto-dismiss | Opera/Firefox | Annoyance filter lists through our existing engine — banners just never appear | M |
| D3 | Forgetful browsing — per-site "forget me when I leave" | Brave | Toggle lives in our site panel (D6); auto-clears cookies+storage on last tab close | M |
| D4 | **Quick Exit / panic wipe** | DDG Fire Button / Samsung | **"Black Hole" button** — one tap collapses everything into an animated black hole: tabs, history, cookies gone. The most on-brand feature we could possibly ship | M |
| D5 | Global Privacy Control header | Firefox/DDG | Header injection — trivial, marketable | S |
| D6 | **Site panel** (tap the lock) — per-site: blocked counts, ad-block level, JS, cookies, dark mode, desktop mode, permissions | Brave Shields (the anatomy to copy) | One panel unifies what we currently scatter across the menu — plus per-site *everything* (H6) | L |
| D7 | **Profiles / containers ("Orbits")** — separate cookies/storage/logins per identity: Personal, Work, Shopping | **Nobody on mobile.** Firefox containers = desktop-only | THE signature differentiator. We already ship ProfileStore for incognito — extend to N named profiles, colored ring on the Command Bar shows which Orbit you're in, per-tab assignment, "open link in other Orbit" | L |
| D8 | Quieter permission chip (non-modal, bar-anchored) | Chrome | Replace our AlertDialog with a Command Bar chip — research says 85% of prompts get ignored; ours won't block the page | M |

## E. Ad-block v2

| ID | Feature | Best today | Our enhancement | Effort |
|---|---|---|---|---|
| E1 | Path/regex rules + `$options` subset (third-party, domain=, image/script types) | Brave | Upgrades our parser from domain-only to real EasyList coverage | L |
| E2 | Per-site blocking levels: off / standard / aggressive | Brave | Set from the D6 site panel | M |
| E3 | **Element zapper** — tap any page element to hide it forever | Brave (first & only on mobile) | Second-ever mobile browser with this; saved as per-site cosmetic rules the user can review/undo | L |
| E4 | Custom filter lists (subscribe by URL) + user rules | Brave | Paste a URL, we fetch + parse + merge | M |
| E5 | Auto-updating filter lists (ours is a frozen snapshot) | everyone | Background WorkManager refresh + delta stats ("+1,204 new rules") | M |
| E6 | Scriptlet injection (anti-adblock neutralizers) | Brave | Curated subset of uBO scriptlets via JS injection | L |

## F. Passwords & autofill — our single biggest hole (0%)

| ID | Feature | Best today | Our enhancement | Effort |
|---|---|---|---|---|
| F1 | **Android Autofill integration** — Google/Samsung/Bitwarden password managers fill inside our WebView | all Chromium browsers | The pragmatic first step: users keep their existing vault, we gain fill support | M |
| F2 | Own password manager (save/generate/biometric-gate) | Chrome GPM | Only after F1 proves demand — security-sensitive, deserves its own phase + threat model | XL |
| F3 | Passkeys via Credential Manager | Chrome | Rides on F1/F2 groundwork | L |
| F4 | Breach alerts for saved logins (HIBP-style API) | Chrome/Edge | Requires F2 | M |

## G. Downloads & media

| ID | Feature | Best today | Our enhancement | Effort |
|---|---|---|---|---|
| G1 | **Own download engine** — parallel segmented connections, pause/resume, retry, rich notifications | desktop managers; weak everywhere on mobile | Live speed graph, auto-resume on network return, smart segment count. Deferred twice — now due | L |
| G2 | In-app previews (image/video/audio/APK info) | Samsung | Tap a finished download → preview sheet, no app-picker roulette | M |
| G3 | **Background video/audio playback** | Brave (their #1 Android draw) | Media-notification controls; per-site opt-in so it never surprises | M |
| G4 | Picture-in-Picture for fullscreen video | all majors | `onShowCustomView` + Android PiP | M |
| G5 | Video assistant (floating pop-out/fullscreen/casting controls) | Samsung | Minimal, auto-hiding — Samsung's is famous for covering page buttons; ours won't | L |
| G6 | Save page for offline (web archive) | Chrome | Pairs with H1 reading list | M |
| G7 | Scheduled downloads ("tonight on Wi-Fi") | Chrome (regional) | Rides on G1 | S |

## H. Reading & content stack

| ID | Feature | Best today | Our enhancement | Effort |
|---|---|---|---|---|
| H1 | **Reading list** — save for later, offline, from menu or bar | Pocket (dead — vacuum!) | Saves the *cleaned reader version* offline automatically; home-screen resume card | L |
| H2 | **Full-page translate** (ML Kit, on-device = private) | Firefox (on-device) | On-device like Firefox — privacy story Chrome can't tell; auto-offer bar on foreign pages | L |
| H3 | **Read-aloud TTS** on reader pages | Edge (best voices) | "Podcast mode": queue your whole reading list and listen with screen off | M |
| H4 | In-app PDF viewer | Chrome (2025) | Most Android browsers still punt to external apps — we render inline (`PdfRenderer`) | L |
| H5 | Print / save page as PDF | all majors | WebView print adapter — quick win | S |
| H6 | **Per-site memory** — zoom, text size, dark mode, desktop mode remembered per site | nobody does this well on mobile | Set once in the D6 panel, remembered forever | M |
| H7 | Reader polish — fix title/nav leakage (known v2 limit) | Firefox Reader | Better extraction heuristics + font/theme/width controls | M |

## I. Customization & accessibility

| ID | Feature | Best today | Our enhancement | Effort |
|---|---|---|---|---|
| I1 | One configurable Command Bar slot | Chrome/Vivaldi | The consensus compromise; pick from ~10 actions | M |
| I2 | Full drag-and-drop toolbar + menu customization | Samsung (gold standard) | Later evolution of I1 | L |
| I3 | Text scaling + force-zoom on stubborn sites | Samsung/Firefox | Accessibility table stakes we're missing | M |
| I4 | High contrast mode | Samsung | | S |
| I5 | Orbit accent colors / theme variants | Opera GX (fun) | Accent from our gradient family; never the GX kitsch | M |
| I6 | Settings search | Samsung (v27) | Needed once v3 doubles our settings | M |

## J. Data, backup & onboarding

| ID | Feature | Best today | Our enhancement | Effort |
|---|---|---|---|---|
| J1 | **Full backup/restore to file** — bookmarks, settings, tabs, reading list, profiles | rare on mobile | One encrypted file, no account, no cloud — deferred from v2 | M |
| J2 | **Onboarding** (≤3 skippable screens) + import from Chrome/other browser | Firefox (the reference) | Value pitch → import bookmarks (we have the parser!) → default-browser ask | M |
| J3 | Sync across devices (accountless QR-chain, E2E) | Brave/DDG model | XL — needs our own backend. Recommend v4, after J1 proves the data model | XL |
| J4 | App shortcuts (launcher long-press: new tab / incognito) | all majors | Trivial manifest win | S |

## K. Delight & polish

| ID | Feature | Best today | Our enhancement | Effort |
|---|---|---|---|---|
| K1 | **Offline mini-game** on the error page | Chrome dino (270M players/mo) | Starship dodging asteroids through the Andromeda galaxy — our dino moment | M |
| K2 | Full-page screenshot + annotate | Edge/Vivaldi | Scrolling capture + Orbit-styled markup pen | L |
| K3 | Share sheet upgrade: QR for current page, copy clean URL (strip tracking params) | Chrome / Brave | Param-stripping on share = privacy delight | M |
| K4 | Smooth micro-animations pass (switcher, menu, transitions) | Arc (the bar) | Orbit easing everywhere, 120Hz-friendly | M |

## L. AI (owner chose Power direction — parked, not forgotten)

Assistant-tier only, whenever we want it: L1 summarize page · L2 ask-the-page · L3 AI omnibox answers — all feasible on-device (Gemini Nano flagships) or cloud (~$0.001/page). If ever added: ship with a Firefox-style **master AI off-switch** from day one. Agentic browsing: **no** (fragile + prompt-injection unsolved industry-wide). Recommend: v4 decision.

---

## Suggested carving (my recommendation, owner decides)

**v3 — "The Power Release"** (~6 phases, marathon-friendly):
1. **P1 Tabs**: B1 groups, B2 search, B3 undo, B5 pin, B6 list view, B7 bulk, B9
2. **P2 Downloads & media**: G1 engine, G2 previews, G3 background play, G4 PiP, G7
3. **P3 Reading stack**: H1 reading list, H3 TTS, H5 print, H6 per-site memory, H7 reader polish
4. **P4 Site panel & privacy**: D6 panel, D1 Safe Browsing, D2 banner-dismiss, D3 forgetful, D4 Black Hole, D5 GPC, D8 chip, E2 levels, E5 auto-update
5. **P5 Signature**: D7 **Orbits profiles** + E3 element zapper
6. **P6 Foundation & delight**: F1 autofill, J1 backup, J2 onboarding+import, J4, C1, C3, I3, K1 game, A2, A5, A6

**v4 candidates**: H2 translate, H4 PDF, A4 omnibox power, B8 workspaces, E1/E6 ad-block deep, F2/F3 own passwords/passkeys, I2 full customization, K2 screenshots, J3 sync, L AI + Play Store.

Everything in this file is on the table — this carving is one proposal, not a decision.
