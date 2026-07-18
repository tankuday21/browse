# External App Links — non-http(s) scheme handling (v4.9)

**Goal:** Make links that lead out of the web work. Today `shouldOverrideUrlLoading` only does
the HTTPS-Only upgrade and returns false for everything else, so `mailto:` / `tel:` / `sms:` /
`geo:` / `market:` / `upi:` / `whatsapp:` / `intent://` links die inside the engine
(ERR_UNKNOWN_URL_SCHEME) — the mail app, dialer, Play Store, UPI apps are never launched.
v4.9 classifies every navigation and dispatches external schemes as Android Intents.

## Classification — `browser/ExternalLinks.kt` (pure, JVM-tested)

`classify(url, hasGesture): LinkAction` — sealed result:

- **LoadInPage** — `http`, `https` (existing HTTPS-Only upgrade still applies), plus
  engine-internal schemes the WebView renders itself: `about`, `blob`, `data`, and anything
  with no scheme (defensive — the engine sorts it out).
- **Ignore** (navigation swallowed, page stays) —
  - unsafe schemes regardless of gesture: `file`, `content`, `javascript` — a page must never
    open arbitrary files/content-providers through us;
  - ANY external scheme **without a user gesture**: a redirect chain or scripted navigation
    must not be able to bounce the user into another app (background app-launch hijacking).
- **OpenApp(url)** — any other scheme with a gesture → `ACTION_VIEW` on the URI.
- **IntentUri(url)** — `intent://` with a gesture → parse + harden + launch (below).

## Dispatch (WebViewHolder — it owns Context and the tab's incognito flag)

- **OpenApp:** `Intent(ACTION_VIEW, uri)` + `FLAG_ACTIVITY_NEW_TASK`.
  `ActivityNotFoundException` → Toast "No app can open this link".
- **IntentUri hardening** (the security-critical part):
  `Intent.parseUri(url, URI_INTENT_SCHEME)`, then
  - `component = null` and `selector`'s component nulled — a page must not target our own (or
    anyone's) non-exported components;
  - `addCategory(CATEGORY_BROWSABLE)` on intent and selector — only activities that opted into
    browser launching;
  - `flags = FLAG_ACTIVITY_NEW_TASK` (wholesale replace — strips any `FLAG_GRANT_*URI_PERMISSION`
    the URI smuggled in).
  On `ActivityNotFoundException`: load `browser_fallback_url` extra in the page — but only when
  it validates as http(s) (`ExternalLinks.safeFallbackUrl`, pure + tested) — else Toast.
- **Incognito confirm:** launching another app leaks the browsing context out of incognito, so
  incognito tabs route the launch through the existing `PermissionRequestInfo` prompt
  ("<host> wants to open another app (this leaves incognito)"); grant = launch, deny = no-op.
  Normal tabs launch directly on the gesture, Chrome-style.

## Out of scope (deferred)

- `window.open` / `target="_blank"` → new tab (`onCreateWindow`) — v5.0, needs tab-model design.
- Per-site "always allow opening app X" remembering.

## Testing

- Unit (JVM): `classify` — http/https/about/blob/data → LoadInPage; file/content/javascript →
  Ignore even with gesture; mailto/tel/upi/market → OpenApp with gesture, Ignore without;
  intent:// → IntentUri with gesture, Ignore without; scheme case-insensitivity; no-colon input.
  `safeFallbackUrl` — https ok, http ok, intent/javascript/blank → null.
- On-device: tap a mailto: link (mail app opens), tel: (dialer), a Play badge (Play Store),
  intent:// link with fallback on a non-installed app (falls back in-page); redirect-only page
  cannot auto-launch an app; incognito tab shows the confirm first.
