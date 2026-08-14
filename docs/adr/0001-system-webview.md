# ADR-0001: Render with the Android System WebView

**Status:** Accepted
**Date:** 2026-07-10
**Version:** v1.0

## Context

A browser needs a rendering engine. Andromeda is a solo learning project targeting a shippable APK,
built by someone learning Android — so engine choice governs both what is possible and whether the
project ever ships.

## Options considered

1. **Android System WebView** *(chosen)* — the engine already on every device. Zero APK cost, always
   security-patched by the OS/Play, full `WebViewClient` interception hooks, and the entire Android
   API surface documented and stable.
2. **GeckoView (Mozilla)** — a real independent engine with extension support, so uBlock Origin and
   proper WebExtensions would work. Rejected: adds ~50–70 MB to the APK, a much smaller body of
   Android integration documentation, and its own update/security burden. For a first browser it
   trades a shippable app for engine purity.
3. **Bundled Chromium / Crosswalk-style embed** — maximum control. Rejected outright: hundreds of MB,
   a build toolchain far beyond a learning project, and *we* would own patching every CVE.

## Decision

Use the Android System WebView, and treat its limits as documented product constraints rather than
bugs to work around indefinitely.

## Consequences

**Good**
- APK stays tiny for what it does; the engine patches itself via the OS.
- Every browser feature becomes an exercise in Android integration, which is the learning goal.
- Page state survives naturally by keeping live `WebView` instances (see [ADR-0002](0002-single-viewmodel-stateflow.md) and `WebViewHolder`).

**Bad — and permanent until this ADR is superseded**
- **No extension API.** Ad blocking must be built by hand ([ADR-0006](0006-own-adblock-engine.md)), and uBlock-class procedural cosmetic filters (`:has-text`) are unreachable.
- `shouldInterceptRequest` never sees WebSockets, so `$websocket` / `$ping` filter rules are unenforceable.
- Server-stitched video ads can be mitigated but never guaranteed to zero.
- We inherit the device's engine version: behaviour varies across OEMs and WebView channels.
- Page-content access is limited to JS round-trips ([ADR-0013](0013-no-persistent-js-interface.md)).

## Revisit when

Andromeda needs true extension support or engine-level features WebView cannot express, **and** a
~60 MB APK is acceptable. That would be a different product, so expect a new ADR, not an edit.
