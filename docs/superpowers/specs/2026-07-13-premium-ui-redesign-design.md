# Andromeda v3.1 — Premium UI Redesign

**Status:** Design (brainstormed with the owner via the visual companion, 2026-07-13).
**Goal:** Make Andromeda feel like a distinctive, premium, big-company-grade browser — "simple yet premium, authentically ours." Fix the four things the owner flagged: the toolbar that "moves differently," an unbalanced home screen, cluttered menus/options, and a lack of consistency across screens. This is a **design-system-led refactor of the existing UI**, not new features and not a re-architecture.

## 1. Design principles (the north star)

- **Deep-space dark base, one bright accent.** Layered neutral surfaces; the blue accent (`#1E4FD8 → #35C3F3`) used sparingly for emphasis, never as filler.
- **One system, every screen.** Tabs, bookmarks, history, downloads, reading list, reader, settings, onboarding all pull from the same tokens — same spacing, radii, type, surfaces, motion.
- **Predictable over clever.** Nothing the user touches should jump unexpectedly. The primary chrome stays where the thumb expects it.
- **Opinionated defaults, tasteful customization.** Ship one beautiful default; offer a few curated switches — never a free-form editor that can produce a messy result.
- **Calm motion.** A single gentle spring curve everywhere; motion clarifies, it doesn't decorate.

## 2. Design tokens (foundation — build first)

A single source of truth in `ui/theme/`, exposed so every composable reads the same values (extend the existing `Orbit`/`Theme` rather than scattering literals).

- **Surfaces (dark-first):** `base #08081C` (page), `surface #12122E` (bars, cards, rows), `elevated #181840` (sheets, dialogs, menus). Never place a flat surface directly on the same-value surface — always step a level.
- **Text:** `primary #E6E8F5`, `secondary #C6C8E0`, `muted #8A8CB5`.
- **Accent:** gradient `#1E4FD8 → #35C3F3`; solid `#2E7BE0` for single-color needs (icon tint, active state). Used for the active/selected state and one hero moment per screen at most.
- **Spacing scale:** `4, 8, 12, 16, 24, 32` — all padding/margins/gaps snap to these. No arbitrary values.
- **Corner radii:** `chip = 10`, `card = 16`, `bar/sheet = 22`, `pill/icon-button = 50%`.
- **Type scale:** `display 24/800`, `title 17/700`, `body 13/400`, `caption 11/400` (dp / weight), with comfortable line height (≈1.5 for body). Applied via named text styles, not inline sizes.
- **Elevation:** soft shadows only on `elevated` (sheets/dialogs) and the floating bar; cards use borders + surface step, not heavy shadows.
- **Motion:** one spring spec for structural movement (sheets, bar shrink/expand) ≈ `220ms`, medium-soft; a fast tween ≈ `120ms` for taps/toggles/ripples. Centralize as named specs so every animation references them.
- **Light theme:** derive a light variant from the same token names (inverted neutrals, same accent) so the system, not per-screen code, handles theme. Dark is the signature; light must still be first-class.

## 3. Toolbar model (the core fix)

**Decision: a fixed bottom bar that shrinks — never hides — and never covers the page.**

- The web content is laid out in the viewport **above** the bar (screen minus bar height); the page is never drawn behind the bar, so nothing is ever hidden. This is the key correction to the owner's "content behind it" concern.
- **At rest — full bar** (`height ≈ 56dp`, radius 22, `surface`, floating with 12dp side/bottom insets): back, address/omnibox, tab-count, menu. Address + actions always one thumb-tap away.
- **On scroll down — slim handle:** the bar animates to a compact pill (`height ≈ 30dp`, a small grab indicator) using the spring motion, giving the page more height. It **never fully disappears**.
- **On scroll up, tap the handle, or reaching the top — full bar** springs back.
- Drives off the existing `browser/BarScrollPolicy` hysteresis, repurposed from hide/show to **full/slim** with the same anti-jitter thresholds; state exposed as an enum (`Full` / `Slim`) the bar composable animates between.
- **The same bar component is used on the home screen and on web pages** — no separate centered search that jumps to the bottom. This removes the transition the owner disliked.
- Fullscreen video / PiP / reader continue to hide the bar entirely as today.
- Edit (typing) state: the bar expands to full, rises above the keyboard, and shows the suggestions panel — unchanged behavior, restyled to tokens.

**One reusable component:** `ui/components/OmniBar.kt` (or refactor the current `CommandBar`) owns full/slim/edit states, so home and web share exactly one implementation.

## 4. Home screen

**Decision: "Focused" default + a small curated Home settings section. No free-form editor.**

- **Default (Focused):** centered logo + wordmark, a tidy shortcut row (editable grid — add/remove/reorder, already built), generous calm spacing, the shared bottom bar as the single search entry. Search happens only in the bottom bar — no second search box.
- **Home settings** (new section in Settings, DataStore-backed — no DB migration):
  - `showGreeting` (default **off** for calm; "Good morning/evening")
  - `showHomeStats` (default **off**; the lifetime trackers-blocked / data-saved line)
  - `shortcutDensity` (`Few` = one row (default) / `More` = full grid) — this is effectively the A↔C toggle
  - `wallpaper` (default **none/subtle**; a couple of built-in backdrops, custom image deferred)
- Incognito home keeps its disclaimer and the same layout/tokens; stats never shown in incognito.
- Every combination still looks like Andromeda — the switches reshape, they can't make it messy.

## 5. Menu

**Decision: a bottom sheet (replaces the corner dropdown).**

- `ModalBottomSheet` (M3) sliding up from the bar: grab-handle, then a **quick icon action row** (back / forward / refresh / share / add-to-home or bookmark), then the grouped list (New tab · New incognito | Bookmarks · History · Downloads · Reading list | Save for later · Reader · Find in page | Desktop site · Site settings · Print | Settings) — same items as today, preserved exactly, plus the ad-block footer.
- Grouped with dividers and the spacing scale; icons tinted from tokens; active/unread badges (Downloads, Reading list) carried over. Swipe-down or scrim-tap to dismiss.
- Reuses the existing menu action wiring; only the container changes (dropdown → sheet).

## 6. Consistency sweep (every other screen)

Apply the tokens and shared components so nothing looks bespoke. No behavior changes, only look/spacing/motion:

- **Tab switcher:** cards use `card` radius, `surface` fill, consistent spacing; grid/list rows on the spacing scale; selection + group headers use accent/tokens; sheet-based context menu matches §5.
- **Bookmarks / History / Downloads / Reading list:** unified list-row spec (height, padding, icon size, title/caption styles), unified top-app-bar treatment, unified empty states, swipe/undo motion on the shared curve.
- **Reader:** controls sheet and the "Aa"/Listen pills adopt tokens; reader themes unaffected (content palettes stay).
- **Settings:** section headers, row heights, switches, sliders all on tokens; the new Home section slots in.
- **Onboarding / error page / dialogs / toasts:** same surfaces, radii, motion.

## 7. Architecture notes

- No new dependencies; no DB migration (Home prefs are DataStore booleans/enum/string).
- Central token module in `ui/theme/`; a `MaterialTheme` extension (or `Orbit` object) exposes `spacing`, `radii`, `motion`, `surface levels`, `accent`, named text styles.
- One shared `OmniBar` composable; one shared list-row composable; one shared bottom-sheet menu.
- The WebView content inset (page above the bar) is a layout change in `BrowserScreen`/`TabWebView`, not a WebView setting.
- Pure logic (BarScrollPolicy full/slim thresholds) stays unit-tested; visual changes verified on device.

## 8. Phasing (each phase shippable + verifiable)

1. **Tokens & theme foundation** — the token module + light/dark derivation. No visible change alone; everything else builds on it.
2. **OmniBar** — fixed bottom bar, content-inset-above, full/slim shrink via BarScrollPolicy; home + web share it.
3. **Home** — Focused default + Home settings toggles.
4. **Menu** — bottom-sheet menu.
5. **Consistency sweep** — roll tokens/shared components across all remaining screens + motion pass.

## 9. Non-goals

- No new browser features (this is polish/structure only).
- No free-form/drag-drop home editor.
- No engine or navigation-architecture changes.
- No change to what the menu can do — only how it's presented.

## 10. Acceptance (owner, on phone)

Bottom bar shrinks smoothly on scroll and never covers the page; home and web pages share the same bar with no jump; menu opens as a bottom sheet; tabs/bookmarks/downloads/history/reader/settings visibly share one spacing/type/motion system; light and dark both look intentional; nothing feels randomly positioned.
