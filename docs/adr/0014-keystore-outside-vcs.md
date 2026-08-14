# ADR-0014: Signing keystore and credentials live outside version control

**Status:** Accepted
**Date:** 2026-07-11
**Version:** v1.x

## Context

Release APKs must be signed with a stable key. Android's signing identity is permanent in a practical
sense: an APK signed with a different key **will not install over** an existing installation
(signature mismatch), and on Play the key can never be changed without publishing a new app. Meanwhile
the repository is public on GitHub.

## Options considered

1. **Keystore file outside the repo; path and passwords in a gitignored `keystore.properties`; build
   reads it conditionally** *(chosen)* — the build works for anyone who clones (debug builds fine,
   release simply has no signing config) and secrets are never in git history.
2. **Commit the keystore and passwords** — rejected absolutely. Anyone could sign a malicious APK that
   the OS treats as an authentic update to Andromeda.
3. **Environment variables / CI secrets only** — good for CI, awkward for local builds; also there is
   no CI on this project yet.
4. **Play App Signing** (Google holds the final key; you hold an upload key) — genuinely reduces the
   consequence of losing your key. Rejected *for now*: enrolment is near-permanent and only relevant
   once distributing through Play ([ADR-0010](0010-arm64-only-apk.md)).

## Decision

`keystore.properties` (gitignored) holds `storeFile`, `storePassword`, `keyAlias`, `keyPassword`. The
`.jks` lives outside the working tree, currently `C:\Vault\keystores\andromeda.jks`. `app/build.gradle.kts`
creates the release signing config **only if** the properties file exists, so a clone without secrets
still builds.

`.gitignore` covers `keystore.properties*`, `*.jks`, `*.keystore` — patterns, deliberately, not exact
filenames.

## Consequences

**Good**
- Secrets have never entered git history; the repo can stay public.
- Contributors can build and test without any credentials.
- The signing identity is stable across all 45 releases to date.

**Bad — the operational burden is real, and has bitten twice**
- **The key is a single point of failure.** Losing `andromeda.jks` means no future version can update
  an installed Andromeda. It must be backed up somewhere durable and private.
- The build's conditional signing config fails in confusing ways if the properties file is malformed.
  A **UTF-8 BOM** on `keystore.properties` made `Properties.load()` read the first key as
  `\uFEFFstoreFile`, so `storeFile` resolved to `null`.
- Moving the vault stripped the `.jks` ACL to an **empty DACL with inheritance blocked**, making the
  file unreadable even by its owner, which Gradle reported only as `Error: Missing keystore`.
- Both incidents are written up in [SECURITY-PRIVACY.md](../SECURITY-PRIVACY.md) and
  [GOTCHAS.md](../GOTCHAS.md). **Lesson:** gitignore secrets by prefix; after any move, verify the key
  is byte-identical and that `:app:signingReport` prints real fingerprints.

## Revisit when

Distribution moves to Play (Play App Signing becomes worth its permanence), or CI starts producing
releases (secrets move to CI secret storage).
