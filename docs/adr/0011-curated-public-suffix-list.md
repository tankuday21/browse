# ADR-0011: Curated public-suffix list, compared by equality

**Status:** Accepted
**Date:** 2026-07-23
**Version:** v6.5

## Context

A password saved on `example.com` should fill on `login.example.com`. Deciding "same site?" requires
knowing where the *registrable domain* (eTLD+1) boundary is — which depends on the public suffix list.
Get this wrong and it becomes a phishing vector.

The classic mistake is **suffix containment**: testing whether one host ends with the other. Under
that rule `example.com.evil.net` "contains" `example.com` and a saved password fills on the attacker's
page.

## Options considered

1. **A curated in-repo suffix set, compared by registrable-domain equality** *(chosen)* — a hand-picked
   `PUBLIC_SUFFIXES` set covering common multi-label ccTLDs plus mainstream multi-tenant SaaS suffixes
   (`myshopify.com`, `zendesk.com`, `atlassian.net`, `sharepoint.com`, `notion.site`, `github.io`, …)
   so tenants stay isolated from each other.
2. **Bundle the full Mozilla Public Suffix List** — correct by construction. Rejected *for now*:
   ~230 KB of data plus parsing and an update mechanism, for a feature whose failure mode is already
   bounded by other gates. Genuinely the right long-term answer ([ROADMAP.md](../ROADMAP.md)).
3. **Suffix containment / naive "ends with"** — rejected as an outright security bug, described above.
4. **Exact host match only** (pre-v6.5 behaviour) — perfectly safe but the feature doesn't exist: a
   login saved on the bare domain never fills on `login.` or `www.`.

## Decision

`CredentialHostMatch.registrableDomain(host)` computes eTLD+1 using the curated set;
`sameSite(a, b)` compares those results for **equality**, never containment. `matches(a, b)` is
`exactHost(a, b) || sameSite(a, b)`.

The `exactHost` arm is load-bearing: without it, hosts with no registrable domain — IP literals,
`localhost`, or a bare public suffix like `wordpress.com` — would lose fill that worked before v6.5.
That regression was introduced and caught in review.

## Consequences

**Good**
- Phishing case closed: `example.com` vs `example.com.evil.net` → `example.com` vs `evil.net` → no match.
- Multi-tenant SaaS tenants don't share credentials with each other.
- Never weaker than the pre-v6.5 exact-host behaviour.

**Bad — residual risk, accepted and documented**
- A multi-tenant suffix *absent* from the curated set will over-match between that provider's tenants.
- Mitigating gates make the worst case tolerable rather than dangerous: fill is **HTTPS-only**,
  **requires an explicit user tap** (never auto-injected), and the fill bar displays `username @host`
  when the candidate host differs from the page host — so a surprising match is visible before it is
  accepted. Worst case is an extra labelled suggestion the user ignores; never a silent leak.
- The set needs occasional maintenance as SaaS providers appear.

## Revisit when

A real over-match is observed, or credential fill becomes automatic rather than tap-gated — either
would make the full Mozilla PSL mandatory rather than merely better.
