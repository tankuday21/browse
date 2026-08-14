# ADR-0007: Own download engine instead of DownloadManager

**Status:** Accepted
**Date:** 2026-07-13
**Version:** v3.0

## Context

A power-user browser is judged on its download manager. Users expect pause/resume, parallel segments,
visible speed, retry after a network drop, and survival across app death.

## Options considered

1. **A custom engine in a foreground `DownloadService`, with state in Room** *(chosen)* — full control
   over segmentation, resume semantics, scheduling and UI.
2. **Android's system `DownloadManager`** — free, OS-managed, survives app death. Rejected: no
   parallel segments, no real pause/resume control, no per-download speed telemetry to draw a graph
   from, notifications and UI we cannot style, and downloads that outlive the app in ways that make
   per-Orbit ownership ([ADR-0003](0003-orbits-profile-isolation.md)) and the Black Hole wipe
   impossible to honour.
3. **WorkManager per download** — good process-death survival and constraint handling. Rejected as the
   primary engine: WorkManager's execution model fights long-lived, user-cancellable, progress-heavy
   work with mid-flight state. (WorkManager is still used elsewhere, e.g. weekly filter-list updates.)

## Decision

Own the engine. `DownloadService` is a foreground service (`dataSync`); every download's full state
lives in the `downloads` table (`totalBytes`, `downloadedBytes`, `state`, `filePath`, `mimeType`,
`etag`, `segments`, `segmentState`, `error`, `attempts`) so progress is durable and resumable across
process death. Range requests drive parallel segments; `etag` validates a resume.

## Consequences

**Good**
- Pause/resume/cancel/retry, parallel segments, live speed graph, Wi-Fi/later scheduling.
- Downloads belong to an Orbit and are wiped by the Black Hole, because we own the record.
- Resume survives process death, since the DB — not memory — is the source of truth.

**Bad**
- **We own concurrency correctness.** State transitions arrive from the user (cancel), the engine
  (progress/complete/fail) and the service start path simultaneously, which produced a real
  cancel-vs-start race requiring an atomic DB claim ([ADR-0012](0012-atomic-db-claim-for-races.md)).
- Service lifecycle, notification obligations and OEM battery managers are ours to handle.
- The `downloads` table carries the most columns and the most migrations of any table.
- Logic living in a Service is not JVM-unit-testable, so correctness must be pushed down to the DAO
  and pure planners ([ADR-0005](0005-pure-testable-cores.md)).

## Revisit when

Never, realistically — the feature only exists because we own it. But any *new* background work that
is fire-and-forget with constraints should use WorkManager rather than this engine.
