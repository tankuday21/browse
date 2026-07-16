# Passwords & Autofill — Phase 1 (v4.7)

**Goal:** A private, per-Orbit password manager: save login credentials (encrypted at rest with
an Android Keystore key), offer to fill them on return visits, and manage them in a dedicated
screen. Fits Andromeda's privacy stance — everything stays on-device, encrypted, and isolated
per Orbit like the rest of v4.2–v4.4.

## Security model (the part that must be right)

1. **Encryption at rest.** Passwords are encrypted with **AES-256-GCM** using a key held in the
   **AndroidKeyStore** (hardware-backed where available), never exported. Each credential row
   stores the ciphertext + its GCM IV. Username + host are stored plaintext (needed for lookup
   and display); only the password is encrypted. If decryption fails (key invalidated), the row
   is treated as unusable, not crashed.
2. **Never in incognito.** No save prompt, no fill, no bridge activity on incognito tabs — same
   gate the rest of the app uses. Incognito credential rows are never written.
3. **Per-Orbit isolation.** Credentials carry `orbitId`; every read/fill is scoped to the active
   Orbit; deleting an Orbit purges its credentials (Black Hole purges all).
4. **Bridge safety.** The JS↔native bridge follows the app's existing bridge pattern (namespaced
   `@JavascriptInterface`, per-tab, incognito-gated). Save/fill only act on the **main frame's**
   host (compare the submitting document origin to the tab's committed URL host); ignore
   cross-origin iframes. Filling injects values only into the current page's fields and never
   logs the password.
5. **Fill is user-initiated.** On a page with saved creds, show a small prompt ("Fill password
   for <host>?"); we do NOT silently autofill. Password is passed to the page only on the user's
   tap.

## Data model + migration (v17 → v18, DB version 18)

New table `credentials`:
`id` (PK auto), `orbitId INTEGER`, `host TEXT`, `username TEXT`, `passwordCipher BLOB`,
`iv BLOB`, `updatedAt INTEGER`. Unique index on `(orbitId, host, username)` (updating an existing
login replaces it). No backfill (new table).

`CredentialDao`: `observeForOrbit(orbitId)`, `getForOrbitAndHost(orbitId, host)`,
`upsert(entity)` (REPLACE on the unique index), `deleteById(id)`, `deleteForOrbit(orbitId)`,
`clearAll()` (Black Hole).

## Encryption — `CredentialCipher`

A small wrapper over `AndroidKeyStore`:
- `getOrCreateKey()` — AES/GCM/NoPadding, 256-bit, in `AndroidKeyStore` under a fixed alias
  (`andromeda_credentials`); `setUserAuthenticationRequired(false)` (Phase 1: no per-use biometric
  — the incognito biometric lock is separate; Phase 2 can add per-reveal auth).
- `encrypt(plain: String): Pair<ByteArray ct, ByteArray iv>`
- `decrypt(ct: ByteArray, iv: ByteArray): String?` (null on failure)
Pure-ish; guarded with runCatching so a Keystore hiccup degrades gracefully.
`CredentialRepository` composes cipher + DAO: `save(orbitId, host, username, password, now)`,
`credentialsForHost(orbitId, host): List<DecryptedCredential>`, `observeForOrbit`, `delete`,
`deleteForOrbit`, `clearAll`.

## WebView capture + fill (mirror existing bridge/injection pattern — see scout report)

- **Capture:** inject JS on page finished (non-incognito) that hooks form `submit` and detects a
  password field + a best-guess username field (nearest preceding text/email input); on submit,
  call the bridge `onLoginSubmitted(host, username, password)`. VM (non-incognito, host matches
  tab) emits a "Save password?" prompt (a snackbar/sheet with Save / Never / Not now). On Save →
  `CredentialRepository.save(...)`.
- **Fill:** on page finished (non-incognito), VM checks `credentialsForHost(activeOrbit, host)`;
  if any, surface a "Fill password" affordance. On tap, `evaluateJavascript` fills the detected
  username/password fields for the chosen credential.
- Both JS constants live next to the existing injected-JS constants in WebViewHolder; the bridge
  is registered the same way as the existing per-tab bridges and gated off for incognito.

## Management UI (ui-ux-pro-max)

A **Passwords** screen (reachable from Settings and/or the menu): the Orbit scope header
(reused `OrbitScopeHeader`, scope "passwords"), a list of saved logins (favicon + host +
username, password masked with a reveal toggle + copy + delete), and an Orbit-aware empty state.
Add manually is Phase 2; Phase 1 focuses on captured logins + management.

## Black Hole + Orbit delete

`onDeleteOrbit` → `credentialRepository.deleteForOrbit(id)`. `onBlackHole` →
`credentialRepository.clearAll()`. (The Keystore key itself can remain; with all rows gone it
decrypts nothing.)

## Out of scope (Phase 2+)

Per-reveal biometric auth; manual add/edit; password generator; breach check; cross-device sync;
smarter username-field heuristics; Autofill Framework (system-wide) integration.

## Testing

- `CredentialCipher` round-trip (encrypt→decrypt) on device (androidTest — Keystore needs a real
  device/emulator); decrypt returns null on tampered ciphertext.
- `CredentialDao` Orbit-scoping + unique-index replace + `deleteForOrbit`/`clearAll` (androidTest).
- Migration v17→v18 creates the table (androidTest).
- VM (unit, with a fake cipher/repo): save from active Orbit only; incognito never saves; fill
  lookup scoped to active Orbit; Orbit delete + Black Hole purge credentials.
- Host-match guard: a submit whose origin host ≠ the tab's committed host is ignored.
