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
- **No-lockout rule, FAIL-CLOSED (review-hardened):** the gate is skipped only when
  `KeyguardManager.isDeviceSecure` is false — the device provably has no screen lock, so auth
  can never succeed and users must not lose their own passwords (Chrome parity). Deliberately
  NOT `BiometricManager.canAuthenticate`: its non-SUCCESS codes (STATUS_UNKNOWN on API 28-29,
  HW_UNAVAILABLE, …) occur on devices that DO have a PIN, where skipping would silently
  disable the gate. The Keystore encryption is unchanged — app-level gate, not key-level auth.
- **Recents protection:** `FLAG_SECURE` is set while the passwords route is on top (cleared on
  leave) so a revealed password can't survive in the Recents thumbnail after re-lock.
- **Known limitation:** multi-window focus loss doesn't fire `onStop`, so the unlocked screen
  stays visible beside another app until the app truly backgrounds — same behavior as the
  incognito lock.
- **Fill stays ungated** (deliberate): filling never displays the password and is already
  user-tap-initiated per credential; Chrome's default is the same. Documented trade-off.

## Manual add / edit

- **Add:** an extended FAB ("Add login") → bottom sheet with Site (host), Username, Password
  (show/hide toggle) + Save. VM `onAddCredential(host, username, password)`:
  - host normalized via `UrlHosts` (same parser as capture/fill — a manual entry must fill on
    the matching page); a pasted URL reduces to its host, a bare host gets an https:// probe;
  - the sheet validates INLINE with `credentialHostPreview` — Save is disabled for a host the
    VM would refuse (never a silent "looks saved but wasn't"), with supporting text; the hint
    notes fills match exactly this host (no subdomain matching yet);
  - a (host, username) that collides with an existing login disables Save too ("already
    exists") — an accidental REPLACE must not silently destroy the other credential.
- **Edit:** pencil action per row → same sheet prefilled (username/host; password prefilled
  via `reveal` — the screen is already behind the gate). VM
  `onEditCredential(id, host, username, password)`:
  - if (host, username) unchanged → plain `save` (upsert replaces in place);
  - if the key changed → **save FIRST, delete the old row only after the save succeeded**
    (review-hardened: encryption can fail on a Keystore hiccup, and delete-then-save would
    destroy the only copy of the credential);
  - an edit landing on ANOTHER credential's key is refused in the VM as defense-in-depth
    (the sheet's duplicate check is the UX layer).
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
