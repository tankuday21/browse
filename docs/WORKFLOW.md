# Development workflow

> **Last verified against:** v6.15, 2026-08-14.
> This is the process that produced 45 releases in 15 days without shipping a known regression.

## Per-feature ritual

Every behaviour-changing feature follows all nine steps. Build-config and documentation changes may
skip review (step 4) in favour of empirical verification — but must say which was done.

### 1. Dup-check first

**Grep the codebase for the behaviour before designing anything.** Search for the *behaviour*, not the
symbol name you expect: find-in-page match counting was nearly rebuilt because it lives in a
`setFindListener` lambda rather than a method called `onFindResultReceived`. See the warning at the top
of [ROADMAP.md](ROADMAP.md).

### 2. Write a spec

`docs/superpowers/specs/YYYY-MM-DD-vX.Y-<topic>-design.md`, containing: Goal, Why, Design (with the
actual code for pure cores), Testing, **Non-goals**. The non-goals section is what keeps scope honest.

### 3. Branch and implement

```bash
git checkout -b feature/vX.Y-<short-name>
```

Put every decision in a pure core with a unit test ([ADR-0005](adr/0005-pure-testable-cores.md)); keep
the Compose/Service layer thin. Run the suite: `./gradlew testDebugUnitTest`.

### 4. Adversarial review

Dispatch a reviewer over the branch diff; add a security-focused pass for anything touching a trust
boundary (credentials, incognito, network, destructive actions). Fix **all** Critical and Important
findings, then re-review until APPROVE. Never pre-instruct a reviewer to ignore something.
Rationale and the list of bugs this has caught: [ADR-0015](adr/0015-adversarial-review-before-merge.md).

### 5. Bump the version

In `app/build.gradle.kts`: `versionCode` +1, `versionName` to the new `X.Y`.

### 6. Clean release build

```bash
export JAVA_HOME="C:/Program Files/Android/Android Studio/jbr"
./gradlew --stop
./gradlew clean assembleRelease > "$SCRATCH/build.log" 2>&1
```

⚠️ **Never write the build log under `app/build/`** — `clean` deletes it mid-run, so a trailing `tail`
fails and a *successful* build looks failed. Use a scratch directory.

### 7. dex-verify the artifact

Prove the shipped APK contains this feature's code — a clean build is necessary but not sufficient
evidence, and incremental builds can ship stale classes.

```bash
unzip -p app/build/outputs/apk/release/app-release.apk classes.dex \
  | grep -a -c -F "<a unique string literal from this feature>"
```

**Use string literals, never method or class names** — R8 renames those. UI labels and Room-generated
SQL work well.

### 8. Merge, tag, push

```bash
git checkout main
git merge --no-ff feature/vX.Y-<short-name> -m "Merge vX.Y: <summary>"
git tag vX.Y
git push origin main --tags
```

`--no-ff` keeps each feature a visible unit in history.

### 9. Stage the APK and record it

Copy to Desktop **and** Downloads as `Andromeda-vX.Y-release.apk`, then confirm all three copies match:

```bash
md5sum app/build/outputs/apk/release/app-release.apk \
       ~/Desktop/Andromeda-vX.Y-release.apk \
       ~/Downloads/Andromeda-vX.Y-release.apk
```

Then update [../CHANGELOG.md](../CHANGELOG.md), remove the item from [ROADMAP.md](ROADMAP.md), and add
any new deferred items to it.

## Release checklist

```
[ ] Dup-checked — the feature does not already exist
[ ] Spec written with explicit non-goals
[ ] Feature branch, not main
[ ] Pure cores extracted and unit-tested
[ ] ./gradlew testDebugUnitTest green
[ ] Review APPROVE (or: documented why review was skipped)
[ ] versionCode + versionName bumped
[ ] Clean release build (log written outside app/build/)
[ ] dex-verified with a unique string literal
[ ] Merged --no-ff, tagged, pushed with tags
[ ] APK staged to Desktop + Downloads, md5 matched
[ ] CHANGELOG.md updated, ROADMAP.md item removed
[ ] Docs touched by this change updated (see below)
```

## Which document to update when

| If you change… | Update |
|---|---|
| a layer boundary, package, or invariant | [ARCHITECTURE.md](ARCHITECTURE.md) |
| an entity, column, or migration | [DATA-MODEL.md](DATA-MODEL.md) **and** export the schema JSON |
| a security gate, crypto choice, or network call | [SECURITY-PRIVACY.md](SECURITY-PRIVACY.md) |
| an approach where you rejected a real alternative | a new [ADR](adr/) — never edit an old one |
| what's shipped | [../CHANGELOG.md](../CHANGELOG.md) + remove from [ROADMAP.md](ROADMAP.md) |
| the build, or you hit an environment trap | [GOTCHAS.md](GOTCHAS.md) |
| the test approach or a fake | [TESTING.md](TESTING.md) |

## Parallelisation tip

Dispatch the review and start the clean release build **at the same time** — they're independent. If
review forces a code change, stop the build and rebuild. If it only changes docs, tests, or comments,
the in-flight APK is still valid (identical bytecode).
