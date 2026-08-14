# ADR-0003: Isolated profiles ("Orbits") via orbitId columns and per-Orbit ProfileStore

**Status:** Accepted
**Date:** 2026-07-16
**Version:** v4.x (schema 13 → 14)

## Context

Users want separate browsing contexts — Work vs Personal — each with its own tabs, history,
bookmarks, logins and **cookies**. Incognito already existed ([ADR-0004](0004-incognito-in-memory.md))
but it is ephemeral; profiles must persist.

Cookie separation is the hard part: WebView's `CookieManager` is process-global, so early versions
could not isolate cookies at all (the v3 README listed this as a known limitation).

## Options considered

1. **`orbitId` on user-data tables + a per-Orbit `androidx.webkit` `ProfileStore` profile** *(chosen)*
   — Room rows are filtered by the active Orbit, and each Orbit gets its own WebView storage
   partition keyed by a stable `profileKey` on `OrbitEntity`.
2. **Separate Room databases per profile** — strong isolation by construction. Rejected: every DAO
   and the whole migration chain would need to become per-database, backup/restore would multiply,
   and cross-Orbit features (a global download list, "move tab to Orbit") become awkward.
3. **One database, filter in the repository layer only, no schema change** — cheapest. Rejected: the
   invariant would live in convention rather than data, so any forgotten `WHERE` leaks another
   profile's rows. Putting `orbitId` in the schema makes the filter reviewable.
4. **Accept no cookie isolation** — what v3 shipped. Rejected once `ProfileStore` made real
   partitioning possible: profiles that share a login session are not profiles.

## Decision

Add `orbitId` to the tables that hold profile-specific user data and filter every query on the active
Orbit. Give each Orbit a stable `ProfileStore` `profileKey` for WebView cookies/storage. Incognito
participates as `orbitId = null`.

## Consequences

**Good**
- Real isolation: separate cookies, so you can be logged into the same site as two different users.
- `orbitId = null` gives incognito a natural, invisible-to-every-filter identity.
- Deleting an Orbit deletes its rows *and* its WebView profile.

**Bad**
- Every new user-data table now needs an explicit scoping decision — and **six of sixteen tables are
  scoped**, so the invariant is not uniform. `closed_tabs` and `reading_list` are unscoped gaps
  ([DATA-MODEL.md](../DATA-MODEL.md#orbit-scoping-status)).
- `ProfileStore.deleteProfile` only succeeds once every WebView bound to that profile is destroyed,
  which forced a deferred-delete handshake between `BrowserViewModel`, `MainActivity` and
  `WebViewHolder`.
- Retrofitting `orbitId` required backfilling existing rows to the first Orbit and replacing unique
  indexes with composite ones (migration 16 → 17 for `bookmarks`).

## Revisit when

The unscoped-table gaps are closed, or a feature needs genuinely global user data — at which point
"global vs Orbit-scoped" should become an explicit, documented property of every table rather than an
implicit one.
