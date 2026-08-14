# Gotchas

> Hard-won traps, each of which cost real debugging time. Add to this file whenever something takes
> more than a few minutes to figure out — that is the cheapest documentation in the project.

## Build & environment

**Never write the release build log under `app/build/`.**
`clean` deletes the directory mid-run, so the trailing `tail` on the log fails and a **successful**
build looks failed. Write logs to a scratch directory.

**`2>&1 | tail -n` hides compile errors.**
Piping to `tail` truncates Kotlin `e:` error lines out of the captured output. Redirect to a log file,
then `grep` it.

**Java `Properties.load()` breaks on a UTF-8 BOM.**
`load(InputStream)` decodes as Latin-1, so a BOM makes the first key `\uFEFFstoreFile` and
`getProperty("storeFile")` returns `null`. In `keystore.properties` this fails the release build at
configuration time with a confusing message. Check with `head -c 3 file | od -An -tx1` — `ef bb bf`
means BOM. Strip it: `tail -c +4 file > tmp && mv tmp file`.

**Backslashes in `.properties` files are escape characters.**
`C:\Vault\keystore.jks` is read as `C:Vaultkeystore.jks`. Use forward slashes (`C:/Vault/...`) or
double the backslashes.

**An empty Windows DACL means deny-everyone, not no-restrictions.**
Moving files can strip ACLs and set the "inheritance blocked" flag, leaving zero access rules — the
owner cannot read their own file. Gradle reports only `Error: Missing keystore`. Diagnose with
`Get-Acl` (look for `AreAccessRulesProtected: True` and an empty `Access` list); fix with
`icacls <file> /inheritance:e`. After any keystore move, verify the file is byte-identical with
`Get-FileHash` and that `:app:signingReport` prints real fingerprints.

**`strings` isn't available in this Git Bash.**
Use `unzip -p apk classes.dex | grep -a -c -F "<literal>"` instead.

## R8 / release builds

**R8 renames classes and methods, so dex-verify must use string literals.**
Grepping `classes.dex` for `markRunningIfLive` returns 0 even though the method is present. Reliable
markers: UI label strings and Room-generated SQL (`"state != 'CANCELLED' AND state != 'DONE'"`).

**A clean build is not proof the APK contains your change.**
Incremental builds have shipped stale classes. Always dex-verify, then md5-match the staged copies.

## Kotlin & Compose

**Declare state fields before the `init` block.**
`init`'s collectors can call functions that reference `_someState` declared further down the class,
producing an NPE at construction. This actually happened when moving a `_translateState.value`
assignment out of a `launch`.

**`SharingStarted.Eagerly` when a `StateFlow` is read as `.value` from non-suspend code.**
Otherwise a cold-start window returns the initial value. Make that initial value **fail open** in the
harmless direction (e.g. `neverSaveSites` empty ⇒ show a dismissable prompt rather than wrongly
suppressing one).

**`LazyColumn` key namespaces must be disjoint.**
Mixing `String` header keys with `Long` row keys is fine, but a folder literally named `__ungrouped__`
next to top-level bookmarks produced duplicate keys and a crash. Namespace them (`"hdr:$folder"`,
`"hdr-ungrouped"`).

**Prune `mutableStateMapOf` UI state when its backing list changes.**
Collapse state for a deleted-then-recreated folder came back stale. `LaunchedEffect(keys) { map.retainAll(...) }`.

**`sections.forEach { item { } }` works inside `LazyColumn`** because `forEach` is inline, so
`item`/`items` bind the enclosing `LazyListScope`. Not obvious, worth knowing.

**Don't shadow `items`.** Naming a lambda parameter `items` inside a `LazyColumn` shadows the DSL
function. Name it `rows`.

**A `var foo` property plus an explicit `fun setFoo()` is a JVM signature clash.** Rename the function.

## KDoc

**A KDoc comment cannot contain `*/` or `/*`.**
`*/` closes the comment early; `/*` opens a nested one. This bites when documenting MIME wildcards —
a bare `"*/*"` or `"image/*"` inside a doc comment breaks the build. Describe them in words.

## WebView & JS

**Strip tags by replacing with a space, not the empty string.**
`<p>one</p><p>two</p>` becomes `onetwo` — one word instead of two — which silently corrupts word counts.

**Android's bundled `org.json` differs from the test-classpath `org.json`.**
They disagree on U+2028 / U+2029 escaping, so escape those explicitly rather than trusting the encoder.

**`node.nodeValue`, never `innerHTML`, when writing text back into a page.**
Keeps an escaping gap from becoming HTML injection ([ADR-0013](adr/0013-no-persistent-js-interface.md)).

**A callback that never fires wedges the UI.** Wrap page round-trips in `withTimeoutOrNull` and allow
dismissal from progress states.

## Testing

**`runCatching` in production code swallows `AssertionError` from a test callback**, so `fail()` inside
one passes silently. Use recording flags.

**Negative tests need an in-test positive control**, or they pass vacuously.

**Race tests must interleave** — use `runCurrent()`, then mutation-verify by deleting the guard and
confirming the test fails.

Details and examples in [TESTING.md](TESTING.md).

## Android specifics

**ABI and `minSdk` are independent.** Dropping `armeabi-v7a` does not change the Android-version floor
([ADR-0010](adr/0010-arm64-only-apk.md)).

**Native `.so` libraries dominate APK size, not your Kotlin.** Ten features (v6.5–v6.14) moved the APK
by ~0 MB; one ML Kit dependency moved it by 30 MB.

**`ProfileStore.deleteProfile` silently fails while any WebView is still bound to that profile.**
Hence the deferred-delete handshake between `BrowserViewModel`, `MainActivity` and `WebViewHolder`.

**`ACTION_SEND_MULTIPLE` needs every URI on `ClipData`,** not just `EXTRA_STREAM` — some OEMs won't
grant read permission otherwise.

**`startService` on an already-foreground service is fine**, but be sure it really is foreground or you
inherit a `startForeground` obligation.
