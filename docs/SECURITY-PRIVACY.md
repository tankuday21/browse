# Security & privacy model

> **Last verified against:** v6.15, 2026-08-14.
> This file states what Andromeda *actually* guarantees. If you change a gate, a crypto choice, or a
> network call, update this file in the same commit — an out-of-date security doc is worse than none.

## Threat model

**Defended against**

| Threat | Mitigation |
|---|---|
| Someone picking up the unlocked phone and reading browsing history | Incognito leaves no trace; Orbits separate profiles; Black Hole panic wipe |
| Trackers and ads profiling the user | From-scratch Adblock-Plus engine + cosmetic filtering + Global Privacy Control |
| Phishing / malware pages | Safe Browsing with a full-screen interstitial |
| Credential theft from the app's database | AES-256-GCM, key in AndroidKeyStore (never in the DB or a file) |
| A lookalike domain harvesting a saved password | Fill requires **registrable-domain equality**, HTTPS, and a user tap |
| Passive network observers seeing our own service calls | Every first-party fetch is HTTPS-only |
| A third party learning what the user browses via our infrastructure | There is no infrastructure — all fetches go source-direct; no proxy, no API keys, no analytics, no telemetry |

**Explicitly NOT defended against**

- A compromised or rooted device, or a malicious keyboard/accessibility service
- OS-level or WebView-level vulnerabilities (we ship the system WebView; see [ADR-0001](adr/0001-system-webview.md))
- Server-stitched video ads (a hard limit for every WebView-based browser)
- Traffic analysis / fingerprinting beyond a neutral User-Agent
- Forensic recovery of deleted files from flash storage

## Privacy invariants

These are the promises. Each is enforced in code, not by convention.

### 1. Incognito never touches disk

| Guarantee | Enforcement |
|---|---|
| Incognito tabs are never persisted | Negative tab ids; `TabManager` skips the DAO for them, `persistRegisteredTab` early-returns |
| Incognito has no Orbit identity | `effectiveOrbitId = if (incognito) null else orbitId` ([TabManager.kt:125](../app/src/main/java/com/udaytank/browse/browser/TabManager.kt#L125)) |
| No history rows | History insert is skipped for incognito |
| No recently-closed entries | `closeTab` gates the insert on `!isIncognitoId(id)` ([TabManager.kt:163](../app/src/main/java/com/udaytank/browse/browser/TabManager.kt#L163)) |
| No password capture or fill | The credential paths return early for incognito |
| No search-suggestion keystrokes leave the device | Two-layer gate: incognito is captured at keystroke time **and** re-checked after the 200 ms debounce (v5.9 fixed a real leak here) |
| Separate WebView storage | A fixed incognito `ProfileStore` profile, wiped on exit |

**Rule for new features:** if a feature writes anything derived from page content, it must have an
incognito early-return, and a test that proves it. Assume nothing is safe by default.

### 2. Orbits isolate profiles — with two known exceptions

Per-Orbit isolation covers `tabs`, `history`, `bookmarks`, `home_shortcuts`, `credentials`,
`downloads`, plus WebView cookies/storage via a per-Orbit `ProfileStore` profile key.

**Known gap:** `closed_tabs` and `reading_list` are **not** Orbit-scoped, so recently-closed entries
and saved articles are visible across Orbits (normal browsing only — incognito is unaffected).
Details in [DATA-MODEL.md](DATA-MODEL.md#orbit-scoping-status); fix tracked in [ROADMAP.md](ROADMAP.md).

### 3. Credential vault

| Property | Implementation |
|---|---|
| Encryption | **AES-256-GCM**, `CredentialCipher` |
| Key storage | **AndroidKeyStore**, hardware-backed where available; the key never enters the DB or a file |
| Nonce | Per-row GCM nonce stored alongside the ciphertext |
| Scope | Per-Orbit (`credentials.orbitId`), unique `(orbitId, host, username)` |
| Capture gate | HTTPS only; never in incognito; never for hosts in `neverSaveSites` |
| Fill gate | HTTPS only, **user-tap initiated — never auto-injected**, never in incognito |
| Cross-subdomain fill | `CredentialHostMatch.matches` = exact host **OR** equal registrable domain. Compares computed eTLD+1 for **equality**, never suffix containment — so `example.com` vs `example.com.evil.net` resolves to `example.com` vs `evil.net` and does not match ([ADR-0011](adr/0011-curated-public-suffix-list.md)) |
| Affordance | When the candidate host differs from the page host, the fill bar shows `username @host` so a surprising match is visible before the tap |

**Residual risk (accepted, documented):** the public-suffix list is curated, not the full Mozilla
PSL. A missing multi-tenant SaaS suffix would over-match between tenants. Because fill also requires
HTTPS **and** an explicit tap **and** displays the source host, the worst case is an extra labelled
suggestion the user can ignore — never a silent leak.

### 4. Network policy

Every network call Andromeda makes on its own behalf (not page traffic):

- **HTTPS only.** Non-HTTPS sources are skipped, e.g. `FeedRepository` `continue`s on a non-HTTPS feed URL.
- **Source-direct.** No proxy, no relay, no first-party server. There is no backend.
- **No API keys, no accounts, no analytics, no crash reporting.**
- **Neutral User-Agent** — `Mozilla/5.0 (Android) Andromeda/<ver>` rather than the platform default, which leaks device model and OS build on every request.
- **On-device inference.** Translation and language ID run locally; page text is never transmitted. Only the ML Kit model itself downloads once, from Google, as an SDK operation.

⚠️ **Known drift:** the User-Agent version is hardcoded in three places and out of sync —
`Andromeda/5.9` in `SuggestionEngine`, `Andromeda/3.2` in `FeedRepository` and `WeatherRepository`.
Harmless but sloppy; consolidation is tracked in [ROADMAP.md](ROADMAP.md).

### 5. Black Hole (panic wipe)

An irreversible, one-tap erase of all browsing data. Because it is destructive, it is gated
deliberately:

- **Opt-in at every layer** — the shake gesture defaults to off in the pref, the ViewModel and the test fake
- **The gesture only *arms* the confirmation dialog** — `onBlackHole()` is called from exactly two places, both inside `BlackHoleConfirmDialog.onConfirm`
- **Foreground-only** sensor registration, unregistered on pause
- **Suppressed while incognito is biometric-locked**, so a shake can't wipe past the lock
- Wipe order matters: `ProfileStore` keys are captured **before** the `orbits` table is cleared, in-flight downloads are stopped before their files are deleted, and `articleStore` is cleared

## Build & release security

| Rule | Detail |
|---|---|
| The keystore never enters version control | `.gitignore` covers `keystore.properties*`, `*.jks`, `*.keystore` |
| Credentials live outside the repo | `keystore.properties` holds the path + passwords and is gitignored; the `.jks` lives in `C:\Vault\keystores\` |
| The signing identity is fixed | All releases are signed with the same `andromeda.jks` (alias `andromeda`). Signing with a different key breaks upgrade installs with a signature mismatch — **never regenerate it** |
| Verify before claiming a build shipped | Clean build → dex-verify with unique **string literals** → md5-match the staged APK. See [WORKFLOW.md](WORKFLOW.md) |

### Incidents worth remembering

Both were found on 2026-08-14 after the project moved to `C:\Projects\active\andromeda`:

1. **`keystore.properties.bak` was not gitignored.** The pattern was the exact filename, so a backup
   copy holding live signing credentials was committable. Fixed by broadening to
   `keystore.properties*`. **Lesson:** gitignore secrets by *prefix*, never by exact filename.
2. **The vault `.jks` had an empty DACL with inheritance blocked** — zero access rules, so even the
   owner could not read it, and Gradle reported a misleading `Error: Missing keystore`. Fixed with
   `icacls <file> /inheritance:e`. **Lesson:** an empty DACL means deny-everyone, not
   no-restrictions; verify the key is byte-identical (`Get-FileHash`) after any move.

## Related documents

- [DATA-MODEL.md](DATA-MODEL.md) — what is stored and how it is scoped
- [ARCHITECTURE.md](ARCHITECTURE.md) — where the gates live
- [ROADMAP.md](ROADMAP.md) — open security/privacy items
- [GOTCHAS.md](GOTCHAS.md) — build and environment traps
