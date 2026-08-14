# ADR-0012: Resolve download state races with an atomic DB claim

**Status:** Accepted
**Date:** 2026-07-23
**Version:** v6.8

## Context

A download's state is written from three independent directions: the **user** (cancel), the
**engine** (progress / complete / fail), and the **service start path** (`handleStart` marking it
RUNNING). A cancel arriving while a download was queued or starting could be overwritten by
`setState(RUNNING)`, producing two bad outcomes at once: an orphaned file on disk, and an "undead"
download that shows RUNNING or DONE after the user cancelled it.

The reason cancel loses is subtle: `engine.cancel(id)` is a **no-op until `engine.start` has
registered that id**, and only the engine's own cancel path deletes the partial file.

## Options considered

1. **An atomic conditional `UPDATE` in the DAO — a "claim"** *(chosen)*. SQLite serialises writes, so
   a claim cannot interleave with the cancel's write; exactly one wins and the loser learns it lost
   from the affected-row count.
   ```sql
   UPDATE downloads SET state='RUNNING', error=NULL
   WHERE id=:id AND state != 'CANCELLED' AND state != 'DONE'
   ```
2. **An in-memory mutex/lock per download id in the Service** — rejected: the Service can be
   recreated, and the DB is the real source of truth. A lock that doesn't span process death guards
   the wrong thing.
3. **Read-then-write with a state check in Kotlin** (`if (state != CANCELLED) setState(RUNNING)`) —
   rejected: that is the bug. The check and the write are separate statements, so a cancel lands
   between them.
4. **A single-threaded actor/queue serialising all download mutations** — would work, but it is a
   larger architectural change to fix a specific ordering problem the database can already arbitrate.

## Decision

`DownloadDao.markRunningIfLive(id): Int` performs the conditional update and returns the affected-row
count. It is applied at **both** points that could clobber a cancellation:

1. **`handleStart`** — early-abort if already CANCELLED before creating any file; then claim. On a lost
   claim (0 rows), delete the file **only if this run created it** (`entry.filePath == null`, so a real
   partial from a resume is never destroyed) and abort.
2. **The engine-emitted RUNNING in `onStateChanged`** — this closed a *follow-on* window found in
   review: a cancel landing after `handleStart`'s claim but before `engine.start` registered the id
   meant `engine.cancel` no-op'd, and the engine's own RUNNING emit then overwrote CANCELLED. Routing
   that emit through the same claim means a lost claim calls `engine.cancel(id)` — by which point the
   generation *is* registered, so its cancel path deletes the file.

Excluding `DONE` prevents a stray start from resurrecting a completed download; clearing `error`
preserves the previous `setState` semantics.

## Consequences

**Good**
- The race is closed at the layer that can actually arbitrate it, and survives process death.
- `activeCount` remains exactly-once on every abort path.
- The claim is JVM-testable through the DAO even though `handleStart` lives in an untestable Service
  ([ADR-0005](0005-pure-testable-cores.md)).

**Bad**
- Correctness now depends on *both* call sites using the claim; a third state-writing path added later
  must remember to.
- A negligible documented residual: if the engine generation was already removed, RUNNING is never
  written over CANCELLED (the safe direction).
- **Verification gotcha:** R8 renames methods, so dex-verifying this feature requires the Room-generated
  **SQL string literal**, not the method name ([GOTCHAS.md](../GOTCHAS.md)).

## Revisit when

Any new writer of download state appears, or the engine gains its own durable state — either would
justify consolidating all transitions behind one claim-aware repository.
