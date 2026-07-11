# Design Spec — Andromeda v2

**Date:** 2026-07-11
**Status:** Draft — pending owner approval
**Baseline:** Andromeda v1.0 (tagged `v1.0`), all 74 tests green

---

## 1. Purpose & Goals

v1 proved the engine: a working, private, ad-blocking browser. v2 makes it a **product** — a browser with its own visual identity that can genuinely replace Chrome as the owner's default, plus the power and privacy features expected of a modern browser.

**Success criteria:**
- Someone seeing the UI says "this is a different browser" — recognizably Andromeda, not a Chrome clone
- Andromeda is set as the owner's Android default browser and survives a full week of daily use
- Every v1 capability still works; the 74-test suite never regresses
- Each phase ships usable and accepted on-device, tagged like v1

**Non-goals (v2):** Play Store publication (v3, with trademark check) · sync/accounts/cloud · AI features · own download engine (v3) · iOS.

---

## 2. The "Orbit" Design Language

Derived from the brand mark (blue orbit swirl). This is the north star for every v2 screen; referenced from all modern browsers (Chrome's Material You, Safari's floating bar, Arc's gesture-first minimalism) but **copies none**.

| Token area | Decision |
|---|---|
| **Identity** | Dark-first. Surface family from the icon's deep space: `#0B0B1C` base, `#14142E` elevated, `#232349` highest. Light theme provided but dark is the brand default. |
| **Accent** | The logo gradient: royal blue `#1E4FD8` → cyan `#35C3F3`. Used as gradient (progress, focus glow, active tab ring) or solid mid-point `#2A8BE8` for small elements. Never more than one accent per view. |
| **Shape** | Pill and circle geometry: Command Bar is a floating pill; cards 24dp radius; speed dial circular; dialogs 28dp. |
| **Motion** | "Orbital": entrances travel subtle arcs, emphasized-decelerate easing, 250–350ms; tab cards fan like a deck; no bounce/overshoot. |
| **Typography** | System font; defined scale — wordmark/display uses tighter letter-spacing; body per Material defaults. |

### The Command Bar (signature component)
A floating pill docked ~12dp above the bottom edge containing: back, address field (tap to expand into full editing state with keyboard + suggestions), tab-count button, menu. Page content scrolls behind it.

**Signature gestures:**
- **Horizontal swipe on the bar → switch tabs** (with a slide-in preview of the neighbor tab)
- **Swipe up on the bar → tab switcher**
- Long-press address → copy/paste/share sheet

### Tab switcher
Grid of cards with **live page thumbnails** (captured on tab-hide, LRU disk cache), swipe-to-close with flick animation, incognito section visually separated (darker, veiled styling). FAB replaced by an in-grid "+" card.

### Home page ("Launchpad")
Greeting by time of day, search pill (focuses Command Bar), speed dial (bookmarks) + most-visited row (from history), incognito variant keeps current explainer. Optional subtle starfield texture from the icon background family.

---

## 3. Feature Scope by Phase

### V2-P1 — Orbit foundation
Design tokens (color/shape/motion in `ui/theme`), Command Bar replacing the current top-field + bottom-row layout, address editing state with keyboard, screen transition animations, error/SSL overlays restyled. **Done when:** v1 features all reachable through the new shell; owner accepts the feel on-device.

### V2-P2 — Tab experience
Thumbnail capture + cache, card-grid switcher redesign, swipe-on-bar tab switching, swipe-up switcher gesture, predictive back. **Done when:** gestures feel natural in daily use; thumbnails survive process death (regenerate on load).

### V2-P3 — Daily driver
- **Default-browser support:** `VIEW` intent filters for http/https (+ `DEFAULT`/`BROWSABLE` categories), handle incoming intents into new tabs, "Set as default" prompt in settings
- **Address autocomplete:** suggestion list while typing — matching history + bookmarks (local, instant) + search-engine suggestion API (network, debounced); tap to load, arrow to fill
- Find-in-page (WebView `findAllAsync` + match counter UI)
- Desktop-site toggle (per-tab UA switch)
- Pull-to-refresh
**Done when:** Andromeda set as device default; links from other apps open correctly.

### V2-P4 — Power
- **Reader mode:** extract article content (Readability-style DOM heuristics via injected JS), render in a clean native/HTML template with font-size control
- **Force-dark for websites:** algorithmic darkening (`WebSettingsCompat.setAlgorithmicDarkeningAllowed`, androidx.webkit), setting + per-site toggle
- **Ad-block v2:** cosmetic filtering — parse EasyList `##` element-hiding rules (generic + per-domain), inject CSS at page finish; path-prefix network rules
- QR scanner in address editing state (CameraX + ML Kit barcode)
**Done when:** reader mode works on major news sites; cosmetic filtering removes ad placeholders left by v1.

### V2-P5 — Privacy
- **True incognito isolation:** androidx.webkit `ProfileStore` (separate cookie/storage profile) when WebView supports it (`WebViewFeature` runtime check), current behavior as fallback — availability shown honestly in settings
- **Biometric lock for incognito** (BiometricPrompt when returning to incognito tabs)
- **HTTPS-only mode** (upgrade http→https, interstitial on failure)
- **Site permission prompts** (geolocation/camera/mic via `onPermissionRequest`/`onGeolocationPermissionsShowPrompt` + per-site remembered grants)
**Done when:** incognito cookies verifiably isolated on modern WebView; permission prompts work on test sites.

### V2-P6 — Housekeeping & v2.0 release
Bookmark folders (parentId migration) · bookmark import/export (Netscape HTML) · history search · local backup/restore (DB + settings to a file) · title-update fix (`onReceivedTitle`) · full regression + performance pass · `versionName 2.0`, tag `v2.0`.

---

## 4. Architecture Impact

- **UI layer:** heavy rewrite (Command Bar, switcher, Launchpad); ViewModel/core/data layers largely stable — the v1 layering pays off here
- **New components:** `SuggestionEngine` (history/bookmark/network suggestions), `ThumbnailStore` (capture + LRU disk cache), `ReaderExtractor`, `CosmeticFilterEngine` (extends ad-block), `PermissionStore` (per-site grants), `ProfileManager` (incognito profiles w/ feature detection)
- **DB migrations expected:** bookmarks folders (v4→v5), site permissions table (v5→v6)
- **New dependencies:** androidx.webkit (force-dark, ProfileStore), androidx.biometric, CameraX + ML Kit (QR; QR ships only if size cost acceptable — decision record at P4)
- **Testing:** same TDD rhythm; UI phases lean more on device acceptance, logic components (SuggestionEngine, CosmeticFilterEngine, ReaderExtractor heuristics) fully unit-tested

## 5. Decision Records

1. **Bottom Command Bar over top bar** — thumb reach on modern tall phones; differentiator vs v1; consistent with where browsers are heading, executed with our own gesture model.
2. **Own design language on Material 3 foundation** — M3 components under Orbit tokens; we restyle, not rebuild, the component library.
3. **Thumbnails via on-hide capture + LRU disk cache** — capturing on every frame is wasteful; capture when a tab leaves foreground, regenerate lazily after process death.
4. **ProfileStore gated by runtime feature check** — old WebViews fall back to v1 behavior with honest settings copy, rather than blocking the feature for everyone.
5. **Own download engine deferred to v3** — system DownloadManager remains; the UI polish lands regardless.

## 6. Working Agreement

Same as v1: spec → phased plans → TDD → feature branches → owner device-acceptance per phase → merge + tag. Owner-writes moments offered, never required. Regression rule: no phase merges with a failing v1 test.
