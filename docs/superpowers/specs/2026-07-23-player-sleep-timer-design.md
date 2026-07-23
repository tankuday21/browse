# Andromeda Player Sleep Timer (v6.3)

**Goal:** "Stop playing after N minutes" for the Andromeda Player — the classic fall-asleep-to-a-
video/album control, deferred from v6.0.

## Scope

- Applies to the Andromeda Player (the Media3 player in `AndromedaPlayerService`). Read Aloud is a
  separate service and out of scope for v6.1... v6.3 (its own follow-up if wanted).
- Presets: Off, 15, 30, 45, 60 minutes, and "End of track" (stop when the current item finishes).
- When the timer elapses: the player **pauses** (not tears down) — the user can resume; the
  session/notification stay so it's not jarring.

## Pure core (JVM-tested): `media/SleepTimer.kt`

No Android types, so it unit-tests on the JVM.

```kotlin
enum class SleepPreset(val minutes: Int?) {   // null = End of track
    OFF(0), M15(15), M30(30), M45(45), M60(60), END_OF_TRACK(null);
}

object SleepTimer {
    /** Absolute deadline (elapsedRealtime ms) for a minute-preset, or null for OFF/END_OF_TRACK. */
    fun deadline(preset: SleepPreset, nowMs: Long): Long?
    /** Remaining whole seconds to [deadline] from [nowMs], floored at 0. */
    fun remainingSeconds(deadlineMs: Long, nowMs: Long): Int
    /** "12:05" / "0:09" style mm:ss for a remaining-seconds count. */
    fun formatRemaining(seconds: Int): String
}
```

## Service (`AndromedaPlayerService`)

- A `sleepDeadlineMs: Long?` + `endOfTrackArmed: Boolean` on the service.
- `ACTION_SET_SLEEP` (extra: preset name). Minute presets set `sleepDeadlineMs` and start a 1s
  ticker coroutine that pauses the player when `now >= deadline`, then clears the timer.
  END_OF_TRACK sets `endOfTrackArmed`; the existing `STATE_ENDED` handler, when armed, pauses
  instead of advancing and disarms. OFF clears both and cancels the ticker.
- Timer is cleared on `onDestroy` (ticker cancelled with the scope).
- Exposes the current preset + remaining seconds so the UI can show a countdown — via a
  `MutableStateFlow<SleepState>` on a companion (the same in-process-only pattern; the player and
  UI share a process), where `SleepState(preset, remainingSeconds)`.

## UI (`PlayerScreen`)

- A bedtime/timer icon in the control row. Tapping opens a small menu of the presets. When a timer
  is active the icon is accented and shows the mm:ss countdown beside it (collapses to the icon
  when Off).
- Selecting a preset sends `ACTION_SET_SLEEP`; selecting Off cancels.

## Testing

- Unit (JVM): `SleepTimer.deadline` (minute presets → now + minutes*60_000; OFF/END_OF_TRACK →
  null), `remainingSeconds` (floors at 0 after the deadline; correct mid-count), `formatRemaining`
  ("0:09", "1:00", "60:00"). Preset enum minute mapping.
- On-device: set 15 min on a video, confirm it pauses ~15 min later (or short-circuit test with a
  1-min build); "End of track" pauses at track end instead of advancing; Off cancels a running
  timer; the countdown ticks in the control row.
