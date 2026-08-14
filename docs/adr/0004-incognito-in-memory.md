# ADR-0004: Incognito as in-memory tabs with negative ids

**Status:** Accepted
**Date:** 2026-07-10
**Version:** v1.x (schema 2 → 3)

## Context

Incognito's value is entirely negative — it is defined by what it *doesn't* leave behind. Any feature
that writes data derived from page content is a potential leak, and features are added constantly. A
convention ("remember to check incognito") will fail; the guarantee has to be structural.

## Options considered

1. **Negative tab ids, `orbitId = null`, never written to any DAO** *(chosen)* — the id's *sign* is
   the marker, so "is this incognito?" is answerable anywhere from the id alone, with no lookup and
   no extra state to thread through call sites.
2. **A boolean `isIncognito` flag on the tab, rows still written but filtered out on read** —
   simplest to implement. Rejected: the data is on disk. A missed filter, a backup, or a forensic
   read exposes it. Incognito must not persist in the first place.
3. **A separate in-memory database for incognito** — clean conceptually. Rejected: doubles the DAO
   surface and still lets a careless write reach the wrong store; the negative-id guard is cheaper
   and harder to bypass.

## Decision

Incognito tabs receive negative ids and `orbitId = null`, live only in memory, and are never passed
to a DAO. `TabManager` centralises the guard (`isIncognitoId`), and every persistence path
(`persistRegisteredTab`, history insert, closed-tab insert, credential capture, favicons, player
progress) early-returns for them.

## Consequences

**Good**
- The guarantee is checkable by inspection: search for `isIncognitoId` / `orbitId = null` and every
  boundary is visible.
- `orbitId = null` composes with Orbits ([ADR-0003](0003-orbits-profile-isolation.md)) — incognito is
  invisible to every Orbit filter for free.
- Incognito tabs vanish on process death with no cleanup code.

**Bad**
- **Every new persisting feature must add its own gate.** This has caught real leaks: v5.9 shipped
  search-suggestion keystrokes from incognito tabs to Google, requiring a two-layer gate (captured at
  keystroke time *and* re-checked after the 200 ms debounce, because a tab switch could land inside
  that window).
- Incognito tabs cannot survive an intentional app restart, even though users sometimes want that.
- Negative ids are an implicit protocol; a new developer could generate one accidentally. It is
  documented here and in `TabManager`'s comments precisely because it is implicit.

## Revisit when

A third tab class appears (e.g. "temporary but restorable"), which would overload the sign convention
past its usefulness and argue for an explicit enum instead.
