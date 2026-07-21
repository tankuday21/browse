# Multi-Share Downloads + Andromeda Player (v6.0)

**Goal:** (A) select multiple downloads and share/delete them in one action; (B) an in-app
"Andromeda Player" that plays downloaded audio/video with a custom Orbit-themed UI, background
audio, Picture-in-Picture, resume-where-you-left-off, and a one-tap handoff to an external player.

**Engine decision (recorded):** The player uses **Media3 / ExoPlayer** as the decode engine and
builds a **100% custom feature/UI layer** on top — the professional structure (VLC/MX Player are
shaped the same way). We deliberately do NOT hand-roll demuxers/codecs (raw `MediaPlayer` is
strictly weaker; bundling FFmpeg means a 20–40 MB APK and GPL/LGPL obligations). Media3 is a
swappable engine under the custom UI if maximal format support is ever wanted.

---

## Global Constraints

- Kotlin/Compose Material3, MVVM single-StateFlow `BrowserViewModel`, Room, single Activity.
- New deps: `androidx.media3:media3-exoplayer`, `media3-ui`, `media3-session` (add to
  `libs.versions.toml`; ~+1.5–2 MB APK). No other new deps.
- Incognito is not involved: Downloads are already per-Orbit and no incognito media is persisted.
- File sharing/opening go through the existing `${applicationId}.files` FileProvider authority
  (derive from `context.packageName`, never a hardcoded id — v5.3 rule).
- `MainActivity` already declares `supportsPictureInPicture="true"` and the app holds
  `FOREGROUND_SERVICE_MEDIA_PLAYBACK`; reuse, don't re-add.

---

## Part A — Multi-Select Share + Delete in Downloads

### UX
- Long-press any download row → **selection mode**. A leading checkbox appears on every row;
  the top bar becomes a contextual bar: `‹selected count› selected`, a **Share** action, a
  **Delete** action, and a close (X) that exits selection mode.
- In selection mode, a tap toggles that row's selection (it does NOT open the preview).
  Outside selection mode, behavior is unchanged (tap opens preview, long-press enters mode).
- Selection is by download id; state lives in the composable (`SnapshotStateList<Long>` /
  `mutableStateOf<Boolean>` for the mode), cleared on exit and when the list changes such that a
  selected id vanishes.

### Multi-share (the optimal, OEM-robust path)
- `Intent(ACTION_SEND_MULTIPLE)` with `putParcelableArrayListExtra(EXTRA_STREAM, ArrayList<Uri>)`.
- **Every** URI is ALSO attached to the intent's `ClipData` (first URI seeds it, the rest via
  `clipData.addItem`). Reason: the `FLAG_GRANT_READ_URI_PERMISSION` grant reliably covers only
  URIs present in ClipData on many OEM builds — `EXTRA_STREAM` list alone silently fails to grant
  read on some devices (same class of bug as v5.3 camera capture). This is non-negotiable.
- MIME type: the common type if all selected files share one (`image/*` stays `image/*`), the
  common top-level type if only that matches (`image/png` + `image/jpeg` → `image/*`), else `*/*`.
- Only rows that resolve to a real URI (`resolvedUriAndMime` non-null — a present file, or a
  legacy system-DM row whose file still exists) are included. If the selection contains
  unshareable rows (in-progress/failed/missing), they're silently skipped for share; if NONE
  resolve, no chooser opens (a toast: "Nothing to share").

### Multi-delete
- New VM method `onDeleteDownloads(ids: List<Long>)` — deletes each via the existing per-id delete
  path (files then rows, honoring the legacy system-DM branch). Exits selection mode on completion.

### Pure logic to extract & unit-test — `browser/ShareBundle.kt`
```kotlin
object ShareBundle {
    /** The MIME to tag an ACTION_SEND_MULTIPLE with, given each file's mime (null → "*/*"). */
    fun commonMimeType(mimes: List<String?>): String
    // - empty            -> "*/*"
    // - all identical    -> that exact type ("image/png")
    // - same top-level   -> "image/*"
    // - mixed top-levels  -> "*/*"
    // - any null/blank/malformed (no '/') -> forces "*/*"
}
```
Tested: empty, single, all-identical, same-top-level-differing-subtype, mixed, a null present,
a malformed entry ("garbage") present.

---

## Part B — Andromeda Player

### Entry points
- Download preview sheet: for `audio/*` and `video/*` files, the primary button becomes
  **▶ Play in Andromeda** and a secondary **Open with…** (existing `openWithChooser`) remains.
- Images keep their current in-sheet preview (unchanged).

### Screen & lifecycle (single-activity preserving)
- A full-screen Compose route `player?downloadId=…` in `MainActivity`'s nav host. It hosts a
  Media3 `PlayerView` (or a bare `SurfaceView` wired to ExoPlayer) via `AndroidView`.
- The ExoPlayer instance is created/owned by `media/AndromedaPlayerController` (a plain class, NOT
  in the VM — the player must outlive route recomposition and bind to a service). It exposes the
  Media3 `Player` plus our own `StateFlow`s (position, duration, isPlaying, tracks).
- **Queue:** the route opens on the tapped file but builds a playlist of the current Orbit's other
  downloaded media of the same top-level type (audio→audio, video→video), ordered by `createdAt`,
  so next/prev work. Query via a new `DownloadDao.mediaForOrbit(orbitId, mimePrefix)`.

### Custom Orbit control overlay (this is the "more than the library" layer)
- Play/pause, a scrubber with elapsed/total (`tabular-nums`), skip ±10s buttons, next/prev,
  playback-speed presets (0.5/1/1.25/1.5/2), mute, and a track button (audio + subtitle track
  selection via ExoPlayer `TrackSelectionParameters`).
- **Gestures** (video): single tap toggles the overlay; double-tap left/right = seek ∓10s;
  vertical drag on the LEFT half = screen brightness (window attribute, player-local, restored on
  exit), vertical drag on the RIGHT half = media volume (`AudioManager`). A horizontal drag
  scrubs. Gestures respect `prefers-reduced-motion` for any animated affordance.
- Orbit tokens throughout (surfaces/accent/spacing/radii); NO default ExoPlayer control chrome.

### Resume position (schema v19 → v20)
- New Room table `player_progress(filePath TEXT PRIMARY KEY, positionMs INTEGER, durationMs
  INTEGER, updatedAt INTEGER)`. `MIGRATION_19_20` creates it; exported schema `20.json` committed.
- On open, if a saved position exists and is < 95% of duration, seek there. Progress is persisted
  on pause, on stop, and every ~5 s while playing. A finished item (≥95%) clears its row so it
  restarts next time.
- Keyed by `filePath` (stable, and the file IS the identity); rows for deleted files are cleaned
  up opportunistically (a missing file on open → delete its stale row).

### Background audio + PiP
- **Audio:** `media/AndromedaPlayerService : MediaSessionService` (Media3) owns/exposes the
  ExoPlayer via a `MediaSession`, giving lock-screen + notification transport controls for free.
  Started as a foreground service (`mediaPlayback` type, already permitted). Audio keeps playing
  when the route/app is backgrounded; the notification's delete/stop tears down the player. This
  is a DIFFERENT session from the web-media `MediaHoldService` (that one holds WebView media; this
  one plays downloaded files) — they must not fight: opening the Andromeda Player pauses any
  active web media hold, mirroring single-audio-focus behavior.
- **Video:** entering background with a video playing triggers PiP on `MainActivity`
  (`enterPictureInPictureMode` with the video aspect ratio) — gated to when the player route is
  active AND the item is video AND the user hasn't paused. PiP actions: play/pause.

### External player handoff
- **Open with…** is `openWithChooser` (ACTION_VIEW + chooser) — already implemented; surface it
  as the secondary action on the media preview and inside the player's track/overflow menu.

---

## Testing

### Unit (JVM) — pure logic only (Media3, Room, services are instrumentation/manual)
- `ShareBundleTest`: the `commonMimeType` matrix above.
- `PlayerProgressPolicyTest` (extract the resume rule as pure `fun shouldResume(savedMs,
  durationMs): Boolean` and `fun isFinished(posMs, durationMs): Boolean`): resume when 0 < saved <
  95%; no resume at ≥95% or when saved==0 or duration unknown (≤0); finished at ≥95%.
- `PlayerQueuePolicyTest` (pure `fun buildQueue(items, currentId, mimePrefix): List<…>`): filters
  to same top-level type + Orbit, keeps `createdAt` order, current item present, stable when only
  one item.
- VM: `onDeleteDownloads` deletes every id (reuse FakeDownloadDao); multi-delete of a mixed set
  (DONE + FAILED) removes all rows.
- Selection reducer if extracted (`toggle`, `clear`, prune-vanished) — pure, tested.

### Manual / on-device (engine + service + PiP can't be JVM-tested — documented checklist)
- Multi-select: pick 3 images → Share → all 3 arrive in the target app (WhatsApp/Gmail); pick a
  mix incl. an in-progress row → only completed share; multi-delete removes all + files gone.
- Player: play an MP4 and an MP3; scrub, ±10s, speed, next/prev across the auto-queue; brightness
  and volume swipes; audio/subtitle track switch on a multi-track file.
- Resume: play halfway, leave, reopen → resumes; finish a file → restarts from 0 next time.
- Background audio: play MP3, press Home → keeps playing with lock-screen controls; stop from the
  notification tears it down. Web media hold pauses when the Andromeda Player starts.
- PiP: play MP4, press Home → PiP window with play/pause; tap to restore.
- External: Open with… → system chooser lists other players and hands off.

## Known limitations (documented, honest)
- Format support is ExoPlayer's (excellent for common codecs; exotic containers may not play — the
  "Open with…" handoff is the escape hatch, and FFmpeg is a future engine swap).
- Subtitle rendering uses ExoPlayer's built-in renderer (CEA-608/WebVTT/SRT sidecar if present);
  external sidecar-subtitle picking is a fast-follow.
- Equalizer, sleep timer, and playback-queue reordering are deliberately out of v6.0 scope.
- Horizontal-drag-to-scrub is deferred: the scrubber slider is the scrub control in v6.0. A
  horizontal drag gesture would have to disambiguate against the vertical brightness/volume
  drags, and that isn't worth shipping unverified — revisit as a fast-follow.
