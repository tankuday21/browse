# Add to Home Screen — launcher pins (v5.7)

**Goal:** Pin any website to the Android launcher as its own icon; tapping it opens the URL in
Andromeda. Distinct from the EXISTING in-app "Add to home" (per-Orbit quick-dial tiles on the
browser's home page) — this one uses `ShortcutManagerCompat.requestPinShortcut`, no new deps
(androidx.core has carried it since 1.0).

## Flow

- Menu → **"Add to Home screen"** (`Icons.Filled.AddToHomeScreen`, This-page section, disabled
  on home). BrowserScreen passes the active tab's (url, title) snapshot up to MainActivity.
- MainActivity `pinToHomeScreen(url, title)`:
  - unsupported launcher (`isRequestPinShortcutSupported` false) → toast; non-web URL (no
    host) → toast ("Only web pages can be pinned") — never a silent no-op;
  - shortcut id = `"pin_" + sha256(url).take(16)` (review-hardened: a 32-bit hashCode
    collision would silently REPLACE an unrelated pin); re-pinning the same URL updates in
    place; never clashes with the static `new_tab`/`new_incognito` ids;
  - intent = `ACTION_VIEW` + the https URI, explicitly targeting MainActivity — the existing
    `handleWebIntent` → `onExternalUrl` path opens it as a new tab in the active Orbit
    (singleTask re-entry already handled; the receiver re-validates the scheme);
  - label from `LauncherPins.shortcutLabel(title, url, host)` (pure, tested): the page title
    when it's meaningful (non-blank, not the URL/host echoed back), clamped; else the host
    without `www.`;
  - icon (review-hardened): cached favicon bytes; hosts whose cache holds only a DECLARED
    touch-icon URL (higher-res, never downgraded to bytes) get a one-shot source-direct
    HTTPS fetch at pin time (neutral UA, 5s timeouts, ≤1MB) — else those best-iconed sites
    would systematically degrade to letters. Fallback: the first label code point (surrogate-
    safe) in white on the Andromeda accent. Rendered as a FULL-BLEED 432px square with the
    content inside the ~66% adaptive safe zone → `IconCompat.createWithAdaptiveBitmap`
    (a pre-masked circle via createWithBitmap renders visibly smaller on adaptive launchers).
- Gating: `!isHome && !isIncognito` — a pinned URL/title is a persistent launcher-owned trace
  that even Black Hole can't wipe, so a private page must never create one (Chrome/Firefox
  suppress the row in private mode too).

## Out of scope

Live favicon fetch when nothing is cached (letter fallback is fine); adaptive-icon inset art;
managing/removing pins from inside the app (the launcher owns pinned shortcuts).

## Testing

- Unit (JVM): `shortcutLabel` — meaningful title used and clamped; URL-as-title falls back to
  host; `www.` stripped; blank title falls back; host fallback when both missing.
- On-device: pin a site → icon appears (favicon or letter); tap opens the page in Andromeda in
  the active Orbit; pinning again updates rather than duplicating; launchers without pin
  support get the toast.
