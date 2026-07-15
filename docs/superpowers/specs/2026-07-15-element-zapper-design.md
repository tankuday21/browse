# Element Zapper — Design (v4.0, "Fortress" #1)

**Date:** 2026-07-15
**Status:** Approved direction, pending spec review

## Goal

Let the user tap any element on a web page and hide it — permanently, per-site — like uBlock
Origin's element picker. Zapped elements are re-hidden on every future visit **at document start**
so they never flash back.

## Architecture

Extends the existing injection path (`WebViewCompat.addDocumentStartJavaScript`, already used for
the GPC shim + YouTube scriptlets) and the cosmetic-filter idea. Three parts: an **in-page picker**
(JS injected when zap mode is entered) that highlights elements and computes a robust selector; a
**native confirm bar** (Compose, Orbit tokens) driven by the picked target over a JS bridge; and a
**per-site rule store** (Room) whose selectors are injected at document start on every load.

## Tech

Kotlin · Compose · Room (schema v11 + migration + exported schema) · WebView JS bridge
(`addJavascriptInterface`, scoped to the owning tab) · `addDocumentStartJavaScript`.

## Global constraints (inherit)

- **Incognito never persists.** Zapping works for the session in an incognito tab, but rules are
  NOT written to disk (saving a host would record the visit). No DAO writes for incognito.
- **Reuse, don't fork.** Use the existing document-start injection path; the JS bridge is scoped to
  the owning tabId (same discipline as the media bridge — no cross-tab spoofing).
- **Orbit tokens only** for all native UI (confirm bar, manage sheet).
- **Fail safe.** A malformed/oversized selector must never break page rendering; injection is
  wrapped so a bad rule is skipped, not fatal.

## Components

### 1. Storage — `data/ZappedElementDao` + `ZappedElementEntity`
- `zapped_elements(id, host, selector, label, createdAt)`, index on `host`.
- `observeForHost(host): Flow<List<..>>`, `countForHost`, `insert`, `deleteById`, `deleteForHost`.
- Room v10→v11 migration (CREATE TABLE + index), exported schema, migration test.

### 2. Selector generation (in-page JS)
On tap, compute two selectors for the target:
- **Exact:** `#id` if a stable id exists; else `tag` + up to N stable classes (skip classes that
  look auto-generated — long hashes, digits-heavy); else an `nth-of-type` path from the nearest
  ancestor bearing an id or stable class. Cap path depth.
- **Similar:** `tag` + shared stable classes only (matches siblings/cards of the same kind).
Return `{exact, similar, label}` (label = human tag.class summary) to Kotlin.

### 3. Picker overlay (JS, injected on entering zap mode)
- `touchmove`/`pointermove` → outline the element under the finger (high-contrast box, no layout
  shift — an overlay div, not a border on the target).
- Tap → freeze target, post `{exact, similar, label}` via the bridge; stop default navigation.
- Esc/second-tap-elsewhere or the native Cancel tears the overlay down.

### 4. Native confirm bar (Compose, Orbit)
- Appears (bottom, above OmniBar) when a target is posted: shows `label`, buttons **Hide** /
  **Hide similar** / **Cancel**.
- Hide → persist `exact`; Hide similar → persist `similar`. Both immediately inject the hide CSS
  into the live page (`display:none !important`) so it vanishes at once, and exit zap mode.

### 5. Re-apply at load
- On page start for a host with saved rules, inject a document-start script that sets
  `display:none !important` for each saved selector (wrapped per-selector in try/catch). No flash.
- Also applied to the current tab immediately on save.

### 6. Entry + management (UX)
- ⋮ menu → **⚡ Zap element** (enabled only on a real page, not home) → enters zap mode.
- Site controls → **Hidden elements (n)** → a bottom sheet listing this host's rules (label +
  remove); removing un-hides on next load (and live). A "clear all for this site" action.

## ViewModel / wiring
- `ZapRepository(dao)` — observe/add/remove, gated non-incognito for writes; provides the
  per-host selector list for injection.
- VM: `zapMode: StateFlow<Boolean>`, `pendingZapTarget: StateFlow<ZapTarget?>`, `enterZapMode()`,
  `onZapTargetPicked(...)`, `hide(exact|similar)`, `cancelZap()`, `hiddenForHost(host)`.
- `WebViewHolder`: inject picker JS on zap mode; register a per-tab bridge; inject saved selectors
  at document start; live-inject on save/remove.

## Testing
- **Pure/unit (run):** selector **sanitizer/validator** (reject empty, cap length, escape) and
  per-host dedup; `ZapRepository` incognito gating (no write); Room v10→v11 migration test.
- The in-page JS selector generation is validated by hand on-device (not unit-testable on JVM);
  keep that logic small and defensive.
- Build-verify the Compose UI.

## Out of scope / limitations
- uBlock-style **parent-expansion slider** (backlog v4.x) — v4.0 is tap-to-hide + "hide similar".
- Procedural cosmetics (`:has-text`, etc.) — CSS selectors only (WebView has no extension APIs).
- Cross-site/global rules — v4.0 is per-host only.

## Rollout
- Room v11 + migration + exported schema + migration test.
- Version **v4.0**, versionCode 8. Ships after review + on-device verification, signed release.
- New feature branch `feature/v4.0-element-zapper` **off main, created after v3.2 is merged**.
