# ADR-0008: Media3/ExoPlayer as the engine, with fully custom UI

**Status:** Accepted
**Date:** 2026-07-21
**Version:** v6.0 "Andromeda Player"

## Context

Downloaded audio and video should play inside Andromeda — with background playback, lock-screen
controls, queueing, resume, speed control, track selection and PiP — rather than handing files to
another app.

## Options considered

1. **Media3/ExoPlayer as the decode engine + 100% custom Compose UI** *(chosen)* — ExoPlayer is an
   *engine*, not a player. Using its `MediaSessionService` gives lock-screen/notification integration
   and audio focus for free, while custom UI gives gestures and Orbit styling that
   `PlayerControlView` cannot.
2. **`PlayerView` with ExoPlayer's stock controls** — fastest path. Rejected: the stock controls
   cannot express the intended interactions (double-tap ±10 s, vertical-drag brightness/volume,
   custom speed presets, Orbit design language), and theming them fights the widget.
3. **Platform `MediaPlayer`** — no dependency, small. Rejected: markedly weaker format support, no
   adaptive streaming, clumsy track selection, and we would rebuild session/notification plumbing by
   hand.
4. **Bundle FFmpeg** — maximal format coverage. Rejected on two counts: **+20–40 MB APK**, and the
   licensing burden (GPL/LGPL obligations) is not something a portfolio project should take on
   casually.

## Decision

`AndromedaPlayerService : MediaSessionService` owns **the single** `ExoPlayer` instance. The UI drives
it through a `MediaController` — never a second player, which would cause double audio. `PlayerView`
is used with `useController = false`; all controls are custom Compose.

## Consequences

**Good**
- Background audio, lock-screen transport controls, and audio focus (so web media yields) come from
  the framework rather than from us.
- Custom gestures and Orbit-native visuals; features (sleep timer, queue policy) added as pure cores.
- Resume positions persist in `player_progress`, and are purged whenever the download row is deleted —
  no residual viewing trace.

**Bad**
- Media3 is the largest pure-code dependency (APK went ~5.3 MB → 6.85 MB at v6.0).
- **Exactly one player instance** is a real invariant: an accidental second `ExoPlayer` in the UI
  produced double audio during development.
- Lifecycle subtleties are ours: progress must be flushed **synchronously** in `onDestroy` before the
  scope is cancelled, and PiP vs Back must take distinct paths (dispose must not pause, or PiP
  breaks).
- Format support is ExoPlayer's, so exotic codecs still fail — accepted rather than bundling FFmpeg.

## Revisit when

Users hit real format gaps often enough to justify the size and licensing cost of a software decoder —
at which point the ADR to write is about FFmpeg, not about the UI.
