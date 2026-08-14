# ADR-0013: No persistent @JavascriptInterface — evaluateJavascript round-trips only

**Status:** Accepted
**Date:** 2026-07-22
**Version:** v6.1 (established earlier, formalised here)

## Context

Several features need to read or modify page content: reader-mode extraction, element zapping,
cosmetic filtering, cookie-banner dismissal, and full-page translation (which must collect every text
node and write translations back).

Android offers two mechanisms: `addJavascriptInterface`, which injects a Java object into **every**
page's JS context, or `evaluateJavascript`, a one-shot call returning a JSON-encoded result.

## Options considered

1. **`evaluateJavascript` round-trips only** *(chosen)* — the app calls into the page when it needs
   something; the page has no handle on the app at any other time.
2. **A persistent `@JavascriptInterface` bridge** — far more convenient: the page can push data,
   callbacks are natural, no serialisation dance. **Rejected on security grounds:** the injected object
   is reachable by *all* JavaScript on the page, including third-party ad and tracker scripts. Every
   exposed method becomes attack surface on every site, forever. Historically this class of bridge has
   produced remote-code-execution bugs in Android apps.
3. **A bridge injected only on trusted pages** — rejected: "trusted" is not decidable for arbitrary
   web content, and a single mistake in the gate re-exposes the interface globally.

## Decision

No persistent JS bridge. All page interaction is `evaluateJavascript`, with results returned as JSON
strings and parsed on the Kotlin side. For translation this means three explicit scripts — COLLECT,
APPLY, RESTORE — with intermediate state stashed on a page-scoped variable
(`window.__andromedaTr`).

Defence in depth on the apply path: translated text is written with `node.nodeValue = ...` **by index**,
never `innerHTML`, so even a gap in escaping cannot become HTML/script injection.

## Consequences

**Good**
- Zero standing attack surface: there is no object for page scripts to find.
- Each interaction is explicit, bounded, and reviewable.
- Works identically in incognito with no special casing.

**Bad**
- The page cannot *push* to the app; features that would want a callback must poll or be triggered by a
  WebView lifecycle callback. This is why translation is a snapshot at trigger time and SPA content
  added later stays untranslated ([ROADMAP.md](../ROADMAP.md)).
- Everything crosses the boundary as strings, so escaping is our responsibility — including
  platform-specific traps (Android's bundled `org.json` differs from the desktop `org.json` used in
  tests on U+2028/U+2029, so those must be escaped explicitly).
- Large payloads (up to a 2,000-node cap) are serialised per call.
- A callback that never fires can wedge the UI, which is why the collect step is wrapped in
  `withTimeoutOrNull`.

## Revisit when

Never for untrusted web content. If a genuinely app-owned page (a local `file://` UI) needs rich
two-way messaging, that is a different, narrowly-scoped decision.
