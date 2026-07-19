# Add to Home Screen — launcher pins (v5.7)

**Goal:** Pin any website to the Android launcher as its own icon; tapping it opens the URL in
Andromeda. Distinct from the EXISTING in-app "Add to home" (per-Orbit quick-dial tiles on the
browser's home page) — this one uses `ShortcutManagerCompat.requestPinShortcut`, no new deps
(androidx.core has carried it since 1.0).

## Flow

- Menu → **"Add to Home screen"** (`Icons.Filled.AddToHomeScreen`, This-page section, disabled
  on home). BrowserScreen passes the active tab's (url, title) snapshot up to MainActivity.
- MainActivity `pinToHomeScreen(url, title)`:
  - unsupported launcher (`isRequestPinShortcutSupported` false) → toast, done;
  - shortcut id = `"pin_" + url.hashCode()` — distinct pages pin separately; re-pinning the
    same URL updates in place; never clashes with the static `new_tab`/`new_incognito` ids;
  - intent = `ACTION_VIEW` + the https URI, explicitly targeting MainActivity — the existing
    `handleWebIntent` → `onExternalUrl` path opens it as a new tab in the active Orbit
    (singleTask re-entry already handled);
  - label from `LauncherPins.shortcutLabel(title, host)` (pure, tested): the page title when
    it's meaningful (non-blank, not just the URL), clamped to launcher length; else the host
    without `www.`;
  - icon: the cached favicon (new `FaviconRepository.get(host)` → `iconBytes` decoded), drawn
    CENTERED at ~60% on a solid circle (small favicons scale poorly full-bleed); fallback: a
    letter avatar — first label character on the Andromeda accent circle (the seeded Orbit
    color), white/black by luminance. Both render via android.graphics Canvas into a 192px
    Bitmap → `IconCompat.createWithBitmap` (launchers mask as needed).
- Only http(s) pages offer the row (`!isHome` + the menu's page gating) — internal pages have
  nothing to pin.

## Out of scope

Live favicon fetch when nothing is cached (letter fallback is fine); adaptive-icon inset art;
managing/removing pins from inside the app (the launcher owns pinned shortcuts).

## Testing

- Unit (JVM): `shortcutLabel` — meaningful title used and clamped; URL-as-title falls back to
  host; `www.` stripped; blank title falls back; host fallback when both missing.
- On-device: pin a site → icon appears (favicon or letter); tap opens the page in Andromeda in
  the active Orbit; pinning again updates rather than duplicating; launchers without pin
  support get the toast.
