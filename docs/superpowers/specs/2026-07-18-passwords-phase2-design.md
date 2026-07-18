# Passwords Phase 2 — biometric lock + manual add/edit (v5.1)

**Goal:** Close v4.7's two biggest gaps. (1) The Passwords screen reveals any saved password on
a plain tap — anyone holding the unlocked phone can read them all; gate the screen behind the
device's biometric/credential auth. (2) There is no way to add or edit a login by hand —
capture-only. Add both.

## Biometric gate

- **Pref:** `lockPasswords` (default **true**), mirroring the `lockIncognito` 4-layer pattern
  (SettingsRepository interface + DataStore impl + VM StateFlow/toggle + SettingsScreen
  `PrefSwitchRow` "Require screen lock for passwords"). Wired into settings backup/restore
  like the other booleans.
- **Gate UX:** full-screen opaque gate over the Passwords screen, same visual language as the
  incognito lock. Extract the shared layout into `ui/components/LockGate.kt` (icon + title +
  subtitle + Unlock pill + auto-prompt on appearance); `IncognitoLockScreen` becomes a thin
  wrapper so both gates stay identical by construction.
- **State:** VM `passwordsLocked: StateFlow<Boolean>` starts true; `onPasswordsUnlocked()`
  clears it; re-locks in `MainActivity.onStop` (same lifecycle hook that re-locks incognito).
  So one auth unlocks the screen for the foreground session — leave the app and it re-locks.
- **Prompt:** extract `promptBiometricUnlock` into a reusable
  `MainActivity.promptUnlock(title, subtitle, onSuccess)`; incognito keeps its copy semantics.
  Failure/cancel = gate stays (no state change) — and add the missing
  `onAuthenticationError` override as an explicit no-op with a comment (v4.7 gap).
- **No-lockout rule:** if `BiometricManager.canAuthenticate(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)`
  is not SUCCESS (no screen lock enrolled at all), the gate is skipped — a user without a
  screen lock must not lose access to their own passwords. (Chrome behaves the same.) The
  Keystore encryption is unchanged — this is an app-level gate, not key-level auth.
- **Fill stays ungated** (deliberate): filling never displays the password and is already
  user-tap-initiated per credential; Chrome's default is the same. Documented trade-off.

## Manual add / edit

- **Add:** "+" action in the Passwords screen top bar → bottom sheet with Site (host),
  Username, Password (with show/hide toggle per the app's form conventions) + Save. VM
  `onAddCredential(host, username, password)`:
  - host normalized: trimmed, lowercased; a pasted URL goes through `UrlHosts.of` first;
  - rejected when host or password is blank (repo already fail-closes);
  - saves into the ACTIVE Orbit via `credentialRepository.save(...)` (upsert semantics:
    same (orbit, host, username) replaces — password change updates in place).
- **Edit:** pencil action per row → same sheet prefilled (username/host; password prefilled
  via `reveal` — the screen is already behind the gate). VM
  `onEditCredential(id, host, username, password)`:
  - if (host, username) unchanged → plain `save` (upsert replaces);
  - if either changed → `delete(id)` then `save` (the unique index includes username, so
    in-place mutation isn't possible; delete-then-save keeps one row).
- `updatedAt` = now on every write (existing repo behavior).

## Testing

- Unit (JVM, FakeCredentialDao + FakeCredentialCipher): add saves normalized host into active
  Orbit; blank host/password rejected; add with existing (host, username) replaces the
  password; edit password-only updates in place (same id count, new cipher); edit changing
  username removes the old row (no duplicate); `passwordsLocked` starts true, unlock clears,
  re-lock sets. FakeSettingsRepository gains `lockPasswords`.
- On-device: open Passwords → biometric prompt appears; cancel keeps the gate; unlock shows
  the list; background + return re-locks; toggle off in Settings removes the gate; add a
  login manually and see it fill on the matching site; edit its password and username.
