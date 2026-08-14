# Testing

> **Last verified against:** v6.15, 2026-08-14. 82 JVM test files, 4 instrumented.

## Strategy

The constraint that shapes everything: **no device has been reliably available**, so anything only
testable on-device is effectively untested. Therefore logic is pushed into pure Kotlin cores and
tested on the JVM ([ADR-0005](adr/0005-pure-testable-cores.md)).

| Layer | How it's tested |
|---|---|
| Pure cores (`browser/`, `translate/`, `media/`, `download/`) | Plain JUnit. Fast, no Android. **This is where the coverage is.** |
| `BrowserViewModel` | JUnit + fake DAOs + `kotlinx-coroutines-test` (`runTest`, `advanceUntilIdle`) |
| Network parsing | JUnit + `MockWebServer` |
| Room migrations, real DAO SQL | `MigrationTestHelper` in `androidTest` — **needs a device; usually compiled, not run** |
| Compose UI | Not automated. Verified from per-version on-device checklists. |

## Commands

```bash
export JAVA_HOME="C:/Program Files/Android/Android Studio/jbr"

./gradlew testDebugUnitTest            # the suite that actually runs (82 files)
./gradlew testDebugUnitTest --tests "*BookmarkSearchTest*"
./gradlew connectedDebugAndroidTest    # migrations/DAO — requires a device or emulator
./gradlew :app:signingReport           # verify release signing resolves
```

## Fakes

Hand-written, in `app/src/test/java/com/udaytank/browse/`:

- **`FakeDaos.kt`** — in-memory `MutableStateFlow`-backed DAO implementations. They must **mirror the
  real SQL's semantics**, including composite unique indexes: `FakeBookmarkDao.insert` reproduces the
  `(url, orbitId)` uniqueness so the same URL can exist in two Orbits. A fake that is more permissive
  than the real DAO makes tests pass on behaviour the database would reject.
- **`FakeSettingsRepository`** — DataStore stand-in. Every new setting must be added here, defaulting
  the same way the real repository does.

When you add a DAO method, add it to the fake in the same commit — the interface won't compile otherwise,
which is the point.

## Test-quality rules learned the hard way

These came from reviews finding tests that passed while proving nothing.

**1. `runCatching` swallows `AssertionError`.**
Production code wrapping a callback in `runCatching` will swallow a `fail()` inside that callback, so
the test passes. Use a **recording flag** and assert on it afterwards:

```kotlin
var fetched = false
val fetcher = { _: String -> fetched = true; "" }
// ... act ...
assertFalse("suggestion fetch must not run in incognito", fetched)
```

**2. Every negative test needs an in-test positive control.**
"Assert X did not happen" passes vacuously if X could never happen. Prove the mechanism works first,
reset, *then* assert suppression:

```kotlin
onQueryChanged("test"); advanceUntilIdle(); assertTrue(fetched)  // control: it CAN fire
fetched = false
switchToIncognito(); onQueryChanged("test"); advanceUntilIdle()
assertFalse(fetched)                                            // now the real assertion
```

**3. Race tests must actually interleave.**
Under `StandardTestDispatcher` nothing runs until you pump the scheduler, so a "race" test can be
sequential and vacuous. Use `runCurrent()` to land the second event inside the first one's window, and
**mutation-verify**: delete the guard and confirm the test fails. If it still passes, it wasn't testing
the race.

**4. Fix time; never call the real clock.**
Pass `now` in (`ClearDataRange.cutoff(now)`, `SleepTimer` deadlines) so tests are deterministic.

**5. `SharingStarted.Eagerly` for `StateFlow`s read as `.value`.**
A lazily-started flow returns its initial value during a cold-start window. Where a non-suspend
function reads `.value` to make a decision (`neverSaveSites`, `activeOrbitId`), it must be eager — and
the initial value must **fail open** in the harmless direction.

## Release verification ritual

Unit tests do not prove the *shipped APK* contains your code. Incremental builds can ship stale
classes, so:

1. **Clean release build** — `./gradlew --stop; ./gradlew clean assembleRelease`
2. **dex-verify with unique string literals:**
   ```bash
   unzip -p app/build/outputs/apk/release/app-release.apk classes.dex \
     | grep -a -c -F "Deletes history for the chosen range"
   ```
   **R8 renames classes and methods**, so a method name like `markRunningIfLive` returns 0 even when
   present. Use string literals — UI labels and Room-generated SQL are reliable markers.
3. **md5-match** the staged APK against the build output.

Full procedure in [WORKFLOW.md](WORKFLOW.md); traps in [GOTCHAS.md](GOTCHAS.md).

## Gaps

- No Compose UI tests; screen behaviour rests on manual checklists.
- Instrumented migration tests exist but are rarely executed — **say "compiled, not run"** rather than
  implying they passed.
- Service internals (`DownloadService.handleStart`) aren't unit-testable; correctness is pushed to the
  DAO claim instead ([ADR-0012](adr/0012-atomic-db-claim-for-races.md)).
- Schema JSONs only go back to v5, so migrations from v1–v4 cannot be tested.
