# ADR-0002: One ViewModel exposing StateFlows as the single source of truth

**Status:** Accepted
**Date:** 2026-07-10
**Version:** v1.0

## Context

Compose screens need state, and a browser's state is unusually entangled: the active tab affects the
toolbar, the find bar, the reader overlay, the translate bar, site settings, and the Orbit filter on
every query. Several screens need the same slices simultaneously.

## Options considered

1. **A single `BrowserViewModel` holding all state as `StateFlow`s, with `onX(...)` intent functions**
   *(chosen)* — one place where state transitions happen, so cross-cutting interactions (tab switch
   resets translate state, Orbit switch re-filters everything) are expressible without coordination
   between objects.
2. **One ViewModel per screen** — conventional and keeps files small. Rejected: the shared state is
   the hard part of a browser. Splitting it means duplicating tab/Orbit logic or inventing a shared
   layer underneath, which is the single ViewModel again with extra indirection.
3. **A full unidirectional-data-flow store (MVI/Redux-style) with reducers and a sealed action type**
   — excellent traceability. Rejected as premature for a solo learner: it front-loads a large amount
   of ceremony before any browser feature exists, and Compose + `StateFlow` already gives
   unidirectional flow in practice.

## Decision

A single `BrowserViewModel` owns all app state. Screens are stateless, receive state via
`collectAsStateWithLifecycle`, and mutate nothing directly — they call intent functions. **No screen
ever touches a DAO.**

## Consequences

**Good**
- Cross-feature invariants are enforceable in one file (e.g. "switching tabs cancels the pending
  suggestion fetch and clears translate state").
- Testable without Compose: the ViewModel is driven by fake DAOs in plain JVM tests
  (`BrowserViewModelTest`, 1,687 lines).
- Adding a feature is usually "add a StateFlow + an intent function + a stateless composable".

**Bad**
- `BrowserViewModel.kt` is **2,568 lines** and grows with every feature. It is the project's largest
  file and its main refactor candidate.
- Field declaration order matters: `init` collectors can call functions that reference
  later-declared state, which produced a real NPE (see [GOTCHAS.md](../GOTCHAS.md)).
- `StateFlow`s read synchronously as `.value` from non-suspend functions must use
  `SharingStarted.Eagerly`, or a cold-start window returns the initial value.

## Revisit when

The ViewModel obstructs work rather than enabling it — e.g. unrelated features start colliding in
it. The natural next step is extracting cohesive slices (tabs, downloads, reader) into delegates that
the ViewModel composes, **without** giving screens direct data access.
