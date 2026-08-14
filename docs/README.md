# Andromeda documentation

Start here. Each document has one job, and each says at the top when it was last verified against the
code — **if that line is stale, trust the code over the doc and then fix the doc.**

## Read in this order

**New to the project?**
[../README.md](../README.md) → [ARCHITECTURE.md](ARCHITECTURE.md) → [DATA-MODEL.md](DATA-MODEL.md) → [adr/](adr/)

**About to build something?**
[ROADMAP.md](ROADMAP.md) (verify the item is still open!) → [WORKFLOW.md](WORKFLOW.md) → [TESTING.md](TESTING.md)

**Something is broken?**
[GOTCHAS.md](GOTCHAS.md) → [TESTING.md](TESTING.md)

**Touching credentials, incognito, or the network?**
[SECURITY-PRIVACY.md](SECURITY-PRIVACY.md) first, without exception.

## The documents

| Document | Purpose | Changes |
|---|---|---|
| [../README.md](../README.md) | Front door: what Andromeda is, features, build | Every release |
| [../CHANGELOG.md](../CHANGELOG.md) | What shipped and when | Every release |
| [ARCHITECTURE.md](ARCHITECTURE.md) | Layers, packages, data flow, the six core invariants | Rarely — only real structural change |
| [DATA-MODEL.md](DATA-MODEL.md) | 16 entities, Orbit scoping, migrations v1→v21 | Every schema change |
| [SECURITY-PRIVACY.md](SECURITY-PRIVACY.md) | Threat model, privacy invariants, crypto, keystore ops | Any gate/crypto/network change |
| [adr/](adr/) | **Decision log** — what was chosen, what was rejected, what it cost | Append-only; never edited |
| [ROADMAP.md](ROADMAP.md) | Everything not done, tiered by importance | Every release |
| [WORKFLOW.md](WORKFLOW.md) | The nine-step feature ritual + release checklist | When the process changes |
| [TESTING.md](TESTING.md) | Test strategy, fakes, and the traps that make tests lie | When the approach changes |
| [GOTCHAS.md](GOTCHAS.md) | Environment/build/Compose traps | Whenever something costs you time |
| [superpowers/specs/](superpowers/specs/) | Per-feature design docs (40) | One per feature, at design time |
| [superpowers/plans/](superpowers/plans/) | Phased implementation plans (18) | One per phase |
| [research/](research/) | Competitive analysis of 10 browsers + adblock research | Rarely |

## Maintenance rules

The reason documentation rots is that updating it is a separate task from doing the work. So it isn't
one — **the doc change ships in the same commit as the code change.**

### 1. Which doc to update when

| If you change… | Update |
|---|---|
| a layer boundary, package, or invariant | [ARCHITECTURE.md](ARCHITECTURE.md) |
| an entity, column, or migration | [DATA-MODEL.md](DATA-MODEL.md) + export the schema JSON |
| a security gate, crypto choice, or network call | [SECURITY-PRIVACY.md](SECURITY-PRIVACY.md) |
| an approach, having rejected a real alternative | a **new** [ADR](adr/) |
| anything shipped | [../CHANGELOG.md](../CHANGELOG.md) + remove the item from [ROADMAP.md](ROADMAP.md) |
| the build, or you hit an environment trap | [GOTCHAS.md](GOTCHAS.md) |
| the test approach or a fake | [TESTING.md](TESTING.md) |

### 2. ADRs are append-only

Never edit a decision to match what you did later. Write a new ADR and mark the old one
`Superseded by ADR-XXXX`. The value of the log is that it records what you believed *at the time*.

### 3. Every doc carries a "Last verified against" line

Update it when you confirm the doc matches reality. A stale line is a useful warning; a missing one is
a lie.

### 4. Write the "why", not the "what"

The code already says what it does. Documentation exists for the things code cannot express: rejected
alternatives, accepted risks, and the constraints that made a decision correct.

### 5. Record gaps honestly

`closed_tabs` crossing Orbits is written down as a gap rather than quietly omitted. A document that
only lists successes cannot be trusted about anything.

## Provenance

This suite was written on **2026-08-14**, after v6.15, and reconstructed from the specs, commit
history, and project memory. The ADRs record decisions from 2026-07-10 → 2026-07-24; records from 0016
onward are written at decision time.

Writing it surfaced four things the code disagreed with:

1. The README claimed **v4.0** and "ProfileStore isolation backlogged" — both long superseded.
2. Only **6 of 16** tables carry `orbitId`, not all of them as assumed.
3. `closed_tabs` **crosses Orbits** — a real, previously unrecorded privacy gap.
4. Five backlog items had **already shipped** (biometric passwords lock, manual credential add/edit, QR
   generation, the upload `capture` attribute, per-Orbit downloads) while still being listed as pending.

That is the argument for these documents, and for keeping them current.
