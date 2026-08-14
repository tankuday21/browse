# ADR-0005: Push decisions into pure, JVM-testable cores

**Status:** Accepted
**Date:** 2026-07-10
**Version:** v1.0

## Context

Android's hard-to-test surfaces are exactly where browser logic wants to live: `WebViewClient`
callbacks, Services, Compose composables. Instrumented tests need a device, and no device has been
reliably available during this project — so anything only testable on-device is effectively untested.

## Options considered

1. **Extract every decision into a pure Kotlin object/enum with no Android imports, unit-test it on
   the JVM, and keep the Android layer as thin plumbing** *(chosen)*.
2. **Robolectric** — test Android classes on the JVM. Rejected: it makes the *framework* testable but
   leaves the logic tangled with it, so tests stay slow and the design does not improve.
3. **Instrumented tests as the primary strategy** — highest fidelity. Rejected: requires a
   device/emulator, so in practice tests would be written and never run. Reserved for the things only
   a device can verify (Room migrations, real DAO behaviour).

## Decision

Any code containing a *decision* — matching, parsing, ranking, cutoffs, policies, formatting — is a
pure object or enum in `browser/`, `translate/`, `media/`, `download/`, with a JUnit test. The Android
layer (Compose, Services, `WebViewHolder`) only wires and renders.

Examples: `AbpParser`, `BookmarkSearch`, `CredentialHostMatch`, `ClearDataRange`, `ReadingTime`,
`ShakeDetector`, `SleepTimer`, `TabClosePolicy`, `PlayerQueuePolicy`, `TranslatePayload`,
`ShareBundle`, `SiteSettingsResolver`.

## Consequences

**Good**
- **82 JVM test files** run in seconds with no device — the practical reason this project has real
  test coverage at all.
- The ViewModel is testable via fake DAOs, so state-machine behaviour is covered too.
- Security-critical logic (`CredentialHostMatch`'s registrable-domain equality) is exhaustively
  testable, including the phishing cases.
- Pure functions are reviewable in isolation, which makes adversarial review
  ([ADR-0015](0015-adversarial-review-before-merge.md)) far more effective.

**Bad**
- Genuine two-layer coordination can't be unit-tested and needs care: the `DownloadService` start-path
  race ([ADR-0012](0012-atomic-db-claim-for-races.md)) lives in a Service, so only the DAO claim it
  delegates to is covered.
- Instrumented tests (Room migrations) are usually **compiled but not run** for lack of a device.
  Reports must say so rather than implying they passed.
- Some indirection: a one-line decision becomes a file plus a test.

## Revisit when

Never, as a default. But when a pure core exists only to satisfy the rule and has a single trivial
caller, inline it — the rule serves testability, not itself.
