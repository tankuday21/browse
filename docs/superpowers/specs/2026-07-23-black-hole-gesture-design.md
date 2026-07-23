# Black Hole Shake Gesture (v6.2)

**Goal:** Trigger the existing Black Hole panic-wipe with a hard device shake, so it's reachable
without digging into Settings — while making an accidental catastrophic wipe impossible.

## Safety model (non-negotiable — the wipe is irreversible)

- **Opt-in, default OFF.** A destructive full-wipe gesture is never on by default. Enabled via a
  Settings → Danger Zone toggle with explicit copy.
- **Shake only ARMS the confirmation.** The gesture shows the exact same "Enter the Black Hole?"
  dialog the Settings button shows; the wipe runs only on the explicit "Erase everything" tap.
  The shake never calls `onBlackHole()` directly.
- **Foreground-only.** The accelerometer listener is registered in `onResume` and unregistered in
  `onPause` — no background battery cost, no possibility of a pocket-shake wipe while backgrounded.
- **Cooldown.** After a shake is recognized, further recognition is suppressed for a short window
  so one vigorous shake shows one dialog, not a stack.

## Pure core (JVM-tested): `browser/ShakeDetector.kt`

A dependency-free recognizer fed raw accelerometer samples; no Android types in its API.

```kotlin
class ShakeDetector(
    private val gThreshold: Float = 2.7f,   // g-force over gravity that counts as a "jolt"
    private val requiredJolts: Int = 3,     // distinct jolts...
    private val windowMs: Long = 1000L,      // ...within this window = a shake
    private val cooldownMs: Long = 2000L,    // suppression after firing
) {
    /** Returns true exactly once when [x,y,z] (m/s^2) at [timeMs] completes a shake. */
    fun onSample(x: Float, y: Float, z: Float, timeMs: Long): Boolean
}
```

Algorithm: `gForce = sqrt(x²+y²+z²) / 9.81`. A sample with `gForce > gThreshold` is a jolt whose
timestamp is recorded; prune timestamps older than `windowMs`; when `>= requiredJolts` remain and
we're past the cooldown, fire (return true once), stamp the cooldown, and clear the jolt list.
Defaults tuned so a deliberate hard shake fires but normal walking/handling does not.

## Android wiring (`MainActivity`)

- New setting `blackHoleGesture: Flow<Boolean>` (default false) in `SettingsRepository`
  (4-layer pattern), collected in `MainActivity`.
- A `SensorEventListener` on `TYPE_ACCELEROMETER` (SENSOR_DELAY_UI). Registered in `onResume`
  **only when the pref is enabled**; unregistered in `onPause` and whenever the pref flips off.
  Each event feeds `ShakeDetector.onSample`; a `true` result performs a short haptic buzz
  (`VibratorManager`, best-effort) and sets a `showBlackHoleConfirm` Compose state.
- `showBlackHoleConfirm` renders the same `AlertDialog` copy as Settings; confirm →
  `viewModel.onBlackHole()`, dismiss → clears the state. (Extract the dialog body so Settings and
  the gesture share one composable, avoiding copy drift.)

## Settings UI

Danger Zone gains a toggle row above the Black Hole button: **"Shake to erase"** with caption
"When on, a hard shake opens the Black Hole confirmation. You'll always be asked before anything
is erased." (Off by default.)

## Known limitations (documented)

- No per-device sensitivity calibration in v6.2 (fixed tuned defaults); revisit if the owner finds
  it too eager or too stubborn on their hardware.
- Devices without an accelerometer simply never fire (the toggle still persists harmlessly).

## Testing

- Unit (JVM): `ShakeDetector` — below-threshold samples never fire; `requiredJolts` strong samples
  within `windowMs` fire exactly once; jolts spread beyond the window don't fire; cooldown
  suppresses an immediate second fire, then a later shake fires again; a single huge spike alone
  doesn't fire (needs `requiredJolts`).
- On-device: enable the toggle, shake → confirmation appears (buzz), Cancel leaves data intact;
  confirm wipes; toggle off → shaking does nothing; background the app and shake → nothing.
