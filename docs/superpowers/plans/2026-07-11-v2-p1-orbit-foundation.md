# V2-P1: Orbit Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Andromeda's own visual identity replaces the wizard-default look: Orbit color/shape/motion tokens, the floating Command Bar (collapsed browse state + expanded edit state), full-bleed content, animated screen transitions. All v1 features remain reachable; the 74-test suite stays green.

**Branch:** `feature/orbit-foundation` → merge after owner device-acceptance → tag `v2-phase-1`.

## Decisions

1. **Dynamic color OFF.** v1 inherited wallpaper-based theming (the lavender look). A brand needs its own palette; Orbit tokens replace dynamic color. (Decision reversible via one flag if the owner misses it.)
2. **Command Bar composition:** back · address capsule (host + lock glyph, tap → edit state) · tab count · menu. Forward/Reload/Star move into the menu (Chrome-parity; keeps the pill airy). Star state still visible via menu item icon.
3. **Progress = gradient underline** on the pill's top edge (logo gradient), replacing the full-width bar.
4. Launchpad gets the greeting + restyle only; most-visited row lands in a later phase.

## Tasks

1. **Orbit tokens** — rewrite `ui/theme/Color.kt` (dark: `#0B0B1C`/`#14142E`/`#232349` surfaces, accent `#2A8BE8`, gradient `#1E4FD8→#35C3F3`; light equivalents), `Theme.kt` (custom schemes, no dynamic), `Shape.kt` (12/24/28dp), new `Orbit.kt` (gradient brush + motion easing constants). Build; commit.
2. **Command Bar** — new `ui/components/CommandBar.kt`: floating pill (nav-bar inset + 12dp), collapsed/editing states, focus + IME handling, gradient progress edge; `BrowserScreen` rework: full-bleed content in a `Box`, bar overlaid, menu absorbs Forward/Reload/Star, overlays restyled by tokens automatically. Build + unit tests; commit.
3. **Motion & Launchpad** — NavHost enter/exit transitions (arc-flavored slide+fade, emphasized-decelerate, 300ms), Launchpad greeting (time-of-day) under the wordmark. Build; commit.
4. **Device acceptance** — owner: new look on phone, all v1 features exercised through the new shell, keyboard behavior, dark + light themes. Merge, tag `v2-phase-1`.
