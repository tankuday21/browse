# Phase 4: Ad Blocker Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Block ad/tracker network requests using a bundled EasyList snapshot, with a global on/off setting, per-site allow toggle, and a per-page blocked counter.

**Architecture:** `FilterListParser` (pure Kotlin) parses the **domain-anchor subset** of EasyList syntax (`||domain^` block rules, `@@||domain^` exceptions) into immutable domain sets. `AdBlockEngine` (app-scoped singleton in `BrowseApplication`, loaded on a background dispatcher at startup) answers `shouldBlock(requestHost, pageHost)` via a parent-domain walk — `a.b.doubleclick.net` matches a rule for `doubleclick.net`. `WebViewHolder` hooks `shouldInterceptRequest` (runs on WebView's background threads — engine is immutable-after-load + `@Volatile`, page hosts tracked in a `ConcurrentHashMap`) and returns an empty response for blocked requests. Blocked events flow to the ViewModel for a per-page counter.

**Scope decisions (documented):**
- v1 parses only pure domain rules (~most of EasyList's blocking power: ad servers are blocked by domain). Path rules (`||x.com/ads/`), rules with options (`$third-party`), and cosmetic rules (`##`) are skipped — full ABP matching is a backlog item.
- Counter is per-page-load (Chrome-style), not persisted.
- List updates: bundled snapshot only; over-the-air refresh is backlog.

## Tasks

1. **FilterListParser** (TDD, pure): `parse(text): BlockList(blockedDomains, allowedDomains)`; tests cover block rules, exceptions, comments/headers, cosmetic rules skipped, path/option rules skipped, case-insensitivity. Commit.
2. **AdBlockEngine** (TDD, pure): `shouldBlock(requestHost: String?, pageHost: String?)` with parent-domain walk; `enabled` flag; per-site allowlist (matches pageHost chain); EasyList exception domains honored. Commit.
3. **Settings + VM:** `adBlockEnabled` (default true), `adAllowedSites: Flow<Set<String>>` (+ `toggleAdAllowedSite(host)`); VM StateFlows, `onAdBlockToggled`, `onToggleAllowAdsOnCurrentSite()`, per-tab blocked counts (`MutableStateFlow<Map<Long, Int>>`, reset on page start, incremented from background threads — safe via atomic `update`). Commit.
4. **Wiring:** `BrowseApplication` owns `adBlockEngine` + loads the asset on `Dispatchers.Default`; `WebViewHolder(context, listener, adBlock)` implements `shouldInterceptRequest` + `onRequestBlocked(tabId)` listener event + page-host tracking; MainActivity `LaunchedEffect` pushes `enabled`/allowlist into the engine. Commit.
5. **UI:** Settings switch "Block ads"; ⋮ menu shows "N ads blocked on this page" and "Allow/Block ads on this site". Build + full suite. Commit.
6. **Acceptance:** visible ad slots collapse on ad-heavy sites; counter increments; per-site allow works and persists; toggle off restores ads. Merge `--no-ff`, tag `phase-4`, push.
