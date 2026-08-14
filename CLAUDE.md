# Working agreement — Andromeda

Instructions for any AI assistant working in this repository. Read this before touching code.

## Project

Andromeda is an Android browser: Kotlin, Jetpack Compose, System WebView, Room. Currently **v6.15**,
26,800 LOC, 45 releases. Built by one person as a learning project, at high velocity, to professional
standards. It is distributed as a **sideloaded arm64 APK** — there is no Play listing and no CI.

Orientation: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) · [docs/DATA-MODEL.md](docs/DATA-MODEL.md) ·
[docs/adr/](docs/adr/) · [docs/ROADMAP.md](docs/ROADMAP.md)

## Non-negotiable constraints

**Secrets**
- The signing keystore lives outside the repo. **Never** commit `keystore.properties*`, `*.jks`, or `*.keystore`.
- Never print a password in output. Paths are fine; values are not.
- **Never regenerate the signing key.** A different key cannot install over an existing Andromeda. See [ADR-0014](docs/adr/0014-keystore-outside-vcs.md).

**Privacy invariants** — breaking one is a release-blocking bug even if tests pass
- **Incognito never touches disk.** Any feature that persists something derived from page content needs an incognito early-return *and a test proving it*. Real leaks have shipped here ([ADR-0004](docs/adr/0004-incognito-in-memory.md)).
- **Credentials:** HTTPS only, never captured or filled in incognito, fill is always user-tap-initiated — never automatic.
- **Network:** every first-party fetch is HTTPS, source-direct, no proxy, no API keys, no analytics, neutral User-Agent.
- **No persistent `@JavascriptInterface`** — `evaluateJavascript` round-trips only ([ADR-0013](docs/adr/0013-no-persistent-js-interface.md)).

**Destructive actions** — Black Hole and clear-data are irreversible. They stay opt-in, confirmed, and default to the safest option.

## How work is done here

Follow the nine-step ritual in [docs/WORKFLOW.md](docs/WORKFLOW.md). The parts most often skipped, and
most costly to skip:

**1. Dup-check before designing.** Grep for the *behaviour*, not the symbol you expect. Five roadmap
items turned out to be already shipped; find-in-page counting was nearly rebuilt because it lives in a
`setFindListener` lambda. **The roadmap is a hypothesis, the code is the truth.**

**2. Put decisions in pure cores.** Anything with a decision — matching, parsing, ranking, cutoffs,
policies — is a plain Kotlin object/enum in `browser/` with no Android imports and a JUnit test. UI and
Services stay thin. This is why the project has real coverage without a device ([ADR-0005](docs/adr/0005-pure-testable-cores.md)).

**3. Adversarial review before merge.** Review has caught genuine defects in nearly every feature,
including ones the author was confident about. Never pre-instruct a reviewer to ignore a finding
([ADR-0015](docs/adr/0015-adversarial-review-before-merge.md)).

**4. Verify the artifact, not just the build.**
```bash
./gradlew --stop && ./gradlew clean assembleRelease   # log OUTSIDE app/build/
unzip -p app/build/outputs/apk/release/app-release.apk classes.dex | grep -a -c -F "<unique literal>"
```
**R8 renames methods** — dex-verify with string literals only. Then md5-match the staged APK.

**5. Update the docs in the same commit.** The mapping table is in
[docs/README.md](docs/README.md#maintenance-rules). Shipping something means: CHANGELOG updated,
ROADMAP item removed, and any doc the change contradicts fixed.

## Reporting standards

- **Never claim a build, test, or install succeeded without showing the evidence.** If instrumented tests were compiled but not run, say exactly that.
- Report measured numbers, not estimates, once a measurement exists.
- Flag gaps you find even when they are outside the task. The `closed_tabs` cross-Orbit leak was found while writing docs, not while fixing bugs.

## Author context

The author is **learning Android** — a beginner in this stack, not in judgement. So:

- Explain the *why* behind implementation choices as you go, briefly and specifically to this codebase.
- Give the full picture before asking him to choose, including tradeoffs and what breaks.
- Prefer a clear recommendation over a survey of options.
- Don't over-explain general programming; do explain Android/Compose/Room specifics and the reasoning behind a design.

## Environment

| | |
|---|---|
| Repo | `c:\Projects\active\andromeda` (moved from `F:\Dev\Browse`; that copy is stale) |
| JDK | `export JAVA_HOME="C:/Program Files/Android/Android Studio/jbr"` |
| Keystore | `C:\Vault\keystores\andromeda.jks`, alias `andromeda` |
| Build logs | Write **outside** `app/build/` — `clean` deletes them mid-run |
| Staging | `Andromeda-vX.Y-release.apk` to Desktop **and** Downloads, md5-matched |

Known environment traps — read before debugging a build: [docs/GOTCHAS.md](docs/GOTCHAS.md).
