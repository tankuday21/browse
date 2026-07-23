# Translate Models over Wi-Fi Only (v6.4)

**Goal:** Let the user avoid spending ~30 MB of mobile data per language on the ML Kit translate
model download. Deferred from v6.1, where `ensureModel(..., requireWifi = false)` was hardcoded.

## Change (small, self-contained)

- New setting `translateWifiOnly: Flow<Boolean>` (default **false** — translate should Just Work
  out of the box; the size is already disclosed in the download bar). 4-layer pref pattern.
- `BrowserViewModel.onTranslatePage` reads it and passes it to `ensureModel(source, target,
  requireWifi = <pref>)`. Read once at the top of the coroutine (alongside `resolveTranslateTarget`).
- When `requireWifi` is on and there's no Wi-Fi, ML Kit's `downloadModelIfNeeded` fails its Task;
  the existing failure path already surfaces `Error("Couldn't download the <lang> language pack")`.
  Refine that copy when the Wi-Fi-only pref is on to name the cause: append " (Wi-Fi only is on)".
- Settings: a toggle under the existing Search-engine/translate area or a small "Translate"
  group — **"Download language packs over Wi-Fi only"** with a caption. (If no translate section
  exists, add a one-row group near the search settings.)

## Testing

- VM: with `translateWifiOnly = true`, `onTranslatePage` calls the engine's `ensureModel` with
  `requireWifi = true` (fake TranslateEngine records the flag); with it false, `requireWifi =
  false`. Extend `FakeTranslateEngine` to capture the last `requireWifi`.
- On-device: toggle on, turn off Wi-Fi, translate an un-downloaded language → graceful error
  naming Wi-Fi; on Wi-Fi it downloads and translates.
