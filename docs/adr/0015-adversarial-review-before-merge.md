# ADR-0015: Every feature passes an adversarial review before merge

**Status:** Accepted
**Date:** 2026-07-10
**Version:** v1.0 onward

## Context

Andromeda is built by one person learning Android, at high velocity — 45 releases in 15 days. That
combination normally produces exactly one outcome: plausible-looking code with subtle bugs, shipped
because the author is also the only reviewer and is not a neutral party about their own work.

## Options considered

1. **A dedicated adversarial reviewer pass on every feature branch before merge, whose default stance
   is to find problems** *(chosen)* — plus a security-focused pass for anything touching a trust
   boundary.
2. **Self-review only** — rejected: the author has just convinced themselves the code is right, so
   they are the worst available reviewer for their own assumptions.
3. **Tests only, no review** — rejected: tests verify what you thought to test. Every real bug listed
   below was in code with passing tests.
4. **Review only "risky" features** — rejected: risk assessment is exactly what the author is bad at.
   The v6.10 bookmark-folders crash came from a feature that looked like pure UI.

## Decision

Each feature branch is reviewed before merge, with findings triaged Critical / Important / Minor. All
Critical and Important findings are fixed and the reviewer re-reviews until it reaches APPROVE.
Security-sensitive features get a second, security-specific pass. Reviewers are never told to ignore a
finding in advance.

## Consequences

**Good — this is the highest-value process in the project.** Real defects caught *before* merge:

| Version | Found in review |
|---|---|
| v6.5 | Routing fill through `credentialsForSite` silently **dropped exact-host fill** for hosts with no registrable domain (IP, `localhost`, `wordpress.com`) — a regression the author introduced ([ADR-0011](0011-curated-public-suffix-list.md)) |
| v6.8 | The **follow-on RUNNING-overwrite window** the author's own fix had missed ([ADR-0012](0012-atomic-db-claim-for-races.md)) |
| v6.10 | A **duplicate-key crash** when a folder was literally named `__ungrouped__` alongside top-level bookmarks |
| v6.14 | The clear-data range **persisted across dialog reopen**, so a stale "All time" could cause an accidental full wipe |
| v6.2 | Shake-to-wipe could fire **past the incognito biometric lock**; the recogniser also counted one sustained impact as three jolts |
| v5.9 | Search suggestions **leaked incognito keystrokes**; the first fix still lost a race inside the debounce window |

**Bad**
- Roughly doubles the work per feature, and a review loop adds iterations before merge.
- Reviews cost tokens/time on a solo project.
- It is tempting to skip for "trivial" changes — and mostly acceptable to (the v6.15 build-config
  change was verified empirically instead), but that judgement is itself a risk.

## Revisit when

Never for behaviour-changing code. Pure build-config or documentation changes may substitute empirical
verification (a clean build plus artifact inspection) for a reviewer — state clearly which was done.
