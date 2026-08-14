# Architecture Decision Records

An ADR records **one decision**: what we chose, what we rejected, and what it cost us. It is written
once and then left alone — if a decision changes, add a *new* ADR that supersedes the old one rather
than editing history. That append-only property is what makes this log trustworthy months later.

> **Provenance:** ADRs 0001–0015 were **reconstructed on 2026-08-14** from the per-feature specs in
> [../superpowers/specs/](../superpowers/specs/), commit messages, and project memory. They record
> decisions made between 2026-07-10 and 2026-07-24, so the "Date" field is the date of the
> *decision*, not of the writing. Records from 0016 onward are written at decision time.

## How to add one

1. Copy the format below, next number in sequence, filename `NNNN-kebab-case-title.md`.
2. Write it **when the decision is made**, not afterwards — the rejected options are the valuable
   part, and they're the first thing you forget.
3. Add a row to the index.
4. If it replaces an earlier decision, set `Supersedes:` and mark the old one `Superseded by:`.

```markdown
# ADR-NNNN: Title

**Status:** Accepted | Superseded by ADR-XXXX
**Date:** YYYY-MM-DD
**Version:** vX.Y

## Context
The forces at play. What made a decision necessary.

## Options considered
1. **Chosen option** — why it wins.
2. **Rejected option** — why not. Be specific; "worse" is not a reason.

## Decision
One paragraph, unambiguous.

## Consequences
What this buys, what it costs, and what we now have to live with.

## Revisit when
The concrete trigger that should reopen this.
```

## Index

| # | Decision | Date | Status |
|---|---|---|---|
| [0001](0001-system-webview.md) | Render with the Android **System WebView**, not GeckoView or a bundled Chromium | 2026-07-10 | Accepted |
| [0002](0002-single-viewmodel-stateflow.md) | One `BrowserViewModel` exposing `StateFlow`s as the single source of truth | 2026-07-10 | Accepted |
| [0003](0003-orbits-profile-isolation.md) | Isolated profiles ("Orbits") via `orbitId` columns + per-Orbit `ProfileStore` | 2026-07-16 | Accepted |
| [0004](0004-incognito-in-memory.md) | Incognito as in-memory tabs with negative ids and `orbitId = null` | 2026-07-10 | Accepted |
| [0005](0005-pure-testable-cores.md) | Push decisions into pure, JVM-testable cores; keep UI and Services thin | 2026-07-10 | Accepted |
| [0006](0006-own-adblock-engine.md) | Write the Adblock-Plus engine from scratch instead of using a library | 2026-07-12 | Accepted |
| [0007](0007-own-download-engine.md) | Own download engine instead of `DownloadManager` | 2026-07-13 | Accepted |
| [0008](0008-media3-hybrid-player.md) | Media3/ExoPlayer as engine + fully custom UI | 2026-07-21 | Accepted |
| [0009](0009-mlkit-on-device-translate.md) | On-device ML Kit translation, accepting the APK cost | 2026-07-22 | Accepted |
| [0010](0010-arm64-only-apk.md) | Ship one arm64-only APK; no App Bundle, no ABI splits | 2026-07-24 | Accepted |
| [0011](0011-curated-public-suffix-list.md) | Curated public-suffix list, compared by **equality**, not the full Mozilla PSL | 2026-07-23 | Accepted |
| [0012](0012-atomic-db-claim-for-races.md) | Resolve download state races with an atomic DB claim, not in-memory locks | 2026-07-23 | Accepted |
| [0013](0013-no-persistent-js-interface.md) | No persistent `@JavascriptInterface`; `evaluateJavascript` round-trips only | 2026-07-22 | Accepted |
| [0014](0014-keystore-outside-vcs.md) | Signing keystore and credentials live outside version control | 2026-07-11 | Accepted |
| [0015](0015-adversarial-review-before-merge.md) | Every feature passes an adversarial subagent review before merge | 2026-07-10 | Accepted |
