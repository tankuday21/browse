# ADR-0006: Write the Adblock-Plus engine from scratch

**Status:** Accepted
**Date:** 2026-07-12
**Version:** v3 "Adblock Max"

## Context

Ad and tracker blocking is the headline privacy feature. [ADR-0001](0001-system-webview.md) rules out
extensions, so uBlock Origin is unavailable. The only hook is `WebViewClient.shouldInterceptRequest`,
called on **every** subresource request — so whatever we build sits on the hot path of page loading.

## Options considered

1. **Implement the Adblock-Plus filter syntax ourselves: parser + token index + cosmetic filtering**
   *(chosen)* — full control over the matching semantics and the performance profile, and it consumes
   the standard filter lists everyone else uses.
2. **A hosts-file / domain-blocklist approach** — trivial to implement (a `Set<String>` of hostnames).
   Rejected: it cannot express `$domain`, `$third-party`, resource types, or exception rules (`@@`),
   so it both over-blocks (breaking sites with no way to except them) and under-blocks (first-party
   trackers). It also can't do cosmetic filtering, so ad *placeholders* remain.
3. **An existing Android ad-block library** — rejected on inspection: the mature implementations are
   either extension-bound, tied to a proxy/VPN model, or unmaintained. Depending on an abandoned
   matcher for the core feature of the app is worse than owning it.
4. **A local VPN / proxy service** — what several blockers do; also enables DNS-level blocking.
   Rejected: it claims the device's VPN slot (users can only have one), triggers a scary system
   permission dialog, and cannot do cosmetic filtering — while still being slower to iterate on.

## Decision

Implement the ABP syntax directly: a parser for network rules (`$domain`, `$third-party`, resource
types, exceptions, anchors, wildcards, plus hosts-format lists), a **token index** so matching is
O(tokens-in-URL) rather than O(rules), cosmetic filtering (generic + per-domain element hiding)
injected at document start, and a small scriptlet pack for YouTube ad-data pruning.

Ship five bundled lists (~5.1 MB of text): EasyList, EasyPrivacy, AdGuard Mobile, Peter Lowe's, and a
cookie-annoyances list — auto-updated weekly, individually toggleable.

## Consequences

**Good**
- Real blocking quality: exception rules and per-domain cosmetics mean sites can be unbroken
  precisely rather than by disabling blocking wholesale.
- The token index keeps ~100k rules viable on the request hot path.
- YouTube in-stream ads are *mitigated* via data pruning + auto-skip, which extension-less WebView
  browsers generally cannot do at all.
- No VPN permission, no proxy, no third party sees the user's traffic.

**Bad**
- ~5.1 MB of the APK is filter text — now the **largest remaining size lever** after the ML Kit
  libraries ([ROADMAP.md](../ROADMAP.md)).
- We own the matcher's correctness and performance forever; a filter-syntax extension upstream is our
  problem to implement.
- Hard limits inherited from WebView: WebSockets are invisible to `shouldInterceptRequest`
  (`$websocket`/`$ping` unenforceable), and procedural cosmetics (`:has-text`) are unreachable.
- Server-stitched video ads can never be guaranteed to zero.

## Revisit when

Filter-list format changes outpace our parser, or measurements show the token index is a real
bottleneck on low-end devices.
