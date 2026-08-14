# ADR-0009: On-device ML Kit translation, accepting the APK cost

**Status:** Accepted
**Date:** 2026-07-22
**Version:** v6.1

## Context

Full-page translation was explicitly user-requested. Chrome's implementation sends page text to
Google's servers — which is precisely the behaviour a privacy-first browser should not copy.

## Options considered

1. **ML Kit on-device translation + language ID** *(chosen)* — inference runs locally; page text never
   leaves the device. Works offline once the language model is cached, and works in incognito without
   a special case.
2. **A cloud translation API** (Google/DeepL/Azure) — best quality, ~50+ languages, near-zero APK
   cost. **Rejected on privacy grounds:** it would ship the contents of every translated page to a
   third party, requiring an API key and effectively a backend. This contradicts the project's central
   promise ([SECURITY-PRIVACY.md](../SECURITY-PRIVACY.md)).
3. **No translation feature** — rejected; it was a direct user request and a genuine daily-use gap.
4. **Download the model lazily and ship none of the engine** — not possible: the *inference engine*
   (`libtranslate_jni.so`) is a native library that must be in the APK. Only the per-language *models*
   download on demand.

## Decision

Use ML Kit `translate` + `language-id` on-device. Page text is collected and re-applied through
`evaluateJavascript` round-trips ([ADR-0013](0013-no-persistent-js-interface.md)), never via a
persistent bridge. Text nodes are set by index using `node.nodeValue` — **not** `innerHTML` — so even
an escaping gap cannot become HTML injection.

## Consequences

**Good**
- Page content is never transmitted. Translation works offline and in incognito.
- No API key, no account, no backend, no per-request cost.

**Bad — and this is the dominant cost**
- **The APK grew from ~6 MB to ~37 MB.** `libtranslate_jni.so` ships per-ABI, so a universal APK
  carried it four times (~64 MB) before ABI filtering. This single decision is the reason
  [ADR-0010](0010-arm64-only-apk.md) exists, and it remains ~15.6 MB of the current 23.6 MB APK.
- A ~30 MB model download happens on first use per language pair (disclosed in the UI).
- Translation quality is below cloud services.
- Snapshot-in-time only: content added later by SPA/infinite-scroll pages is untranslated until
  re-run, and there is a 2,000-node cap ([ROADMAP.md](../ROADMAP.md)).

## Revisit when

The size cost becomes unacceptable, or ML Kit offers a downloadable engine rather than a bundled one.
Note that removing translation would roughly halve the APK — that is the honest trade being made every
release.
