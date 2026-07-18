# External App Links — non-http(s) scheme handling (v4.9)

**Goal:** Make links that lead out of the web work. Today `shouldOverrideUrlLoading` only does
the HTTPS-Only upgrade and returns false for everything else, so `mailto:` / `tel:` / `sms:` /
`geo:` / `market:` / `upi:` / `whatsapp:` / `intent://` links die inside the engine
(ERR_UNKNOWN_URL_SCHEME) — the mail app, dialer, Play Store, UPI apps are never launched.
v4.9 classifies every navigation and dispatches external schemes as Android Intents.

## Classification — `browser/ExternalLinks.kt` (pure, JVM-tested)

`classify(url): LinkAction` — sealed result:

- **LoadInPage** — `http`, `https` (existing HTTPS-Only upgrade still applies), engine-internal
  schemes the WebView renders itself (`about`, `blob`, `data`), anything with no scheme, and
  anything whose "scheme" fails the RFC 3986 shape (`host:8080/x`) — the engine sorts those out.
- **Ignore** (navigation swallowed, page stays) — unsafe schemes: `file`, `content`,
  `javascript` — a page must never open arbitrary files/content-providers through us.
- **OpenApp(url)** — any other scheme → `ACTION_VIEW` on the URI.
- **IntentUri(url)** — `intent://` → parse + harden + launch (below).

`needsConfirm(hasGesture, incognito)` decides HOW an OpenApp/IntentUri launches:
- gesture-backed tap in a normal tab → launch directly, Chrome-style;
- **no gesture** → confirm prompt, never a silent swallow and never an auto-launch. Rationale:
  `hasGesture()` is unreliable in both directions — redirect legs of a legitimate tap→302→`upi:`
  payment handoff can lose the gesture bit (silent swallow would kill payments with zero
  feedback), while scripted chains can inherit it (auto-launch would enable redirect hijacking).
  The prompt is correct in both worlds: the user decides;
- **incognito** → always confirm — launching another app leaks the browsing context out.

## Dispatch (WebViewHolder — it owns Context and the tab's incognito flag)

- **OpenApp:** `Intent(ACTION_VIEW, uri)` + `FLAG_ACTIVITY_NEW_TASK`. Launch failures of ANY
  kind (`ActivityNotFoundException`, `SecurityException`, `FileUriExposedException`, … — the
  intent is page-controlled, so the catch is `RuntimeException`-wide) → Toast "No app can open
  this link", never a crash.
- **IntentUri hardening** — `browser/IntentHardening.harden(parsed, selfPackage)` (the
  security-critical part, instrumented-tested with hostile URIs):
  - data scheme (and the selector's) re-validated against the unsafe set — `#Intent;scheme=file`
    must not bypass the navigation-level block;
  - `action` forced to `ACTION_VIEW` — a page could otherwise set `ACTION_CALL` (auto-dial);
  - `component = null` and `selector`'s component nulled — a page must not target our own (or
    anyone's) non-exported components;
  - `addCategory(CATEGORY_BROWSABLE)` on intent and selector — only activities that opted into
    browser launching;
  - `flags = FLAG_ACTIVITY_NEW_TASK` (wholesale replace — strips any `FLAG_GRANT_*URI_PERMISSION`
    the URI smuggled in);
  - extras cleared (confused-deputy hygiene; `browser_fallback_url` is read before hardening);
  - `package=` pointing at ourselves de-targeted (no self-loop); other packages kept — that's
    the legitimate way links pick a specific app.
  On launch failure: load `browser_fallback_url` in the page — only when it validates as http(s)
  (`ExternalLinks.safeFallbackUrl`, pure + tested) AND after applying HTTPS-Only upgrade
  (programmatic `loadUrl` bypasses `shouldOverrideUrlLoading`, so the upgrade is applied at the
  call site) — else Toast.
- **Confirm prompt:** reuses the existing `PermissionRequestInfo` bar — "<host> wants to open
  another app" (+ "(this leaves incognito)" in incognito); grant = launch, deny = no-op.

## Accepted residual risks (documented)

- A gesture-backed tap whose navigation is server-redirected to an external scheme launches
  directly (gesture inherits through the redirect) — same residual Chrome carries.
- One gesture window could enqueue more than one launch/prompt (no per-tab debounce yet).

## Out of scope (deferred)

- `window.open` / `target="_blank"` → new tab (`onCreateWindow`) — v5.0, needs tab-model design.
- Per-site "always allow opening app X" remembering.

## Testing

- Unit (JVM): `classify` — http/https/about/blob/data → LoadInPage; file/content/javascript →
  Ignore; mailto/tel/upi/market → OpenApp; intent:// → IntentUri; scheme case-insensitivity;
  no-colon and non-scheme-shape input → LoadInPage. `needsConfirm` — all four gesture×incognito
  combinations. `safeFallbackUrl` — https ok, http ok, intent/javascript/blank → null.
- Instrumented (`IntentHardeningTest`): hostile URIs — component targeting stripped (incl.
  selector), grant flags replaced, ACTION_CALL forced to VIEW, file/content/javascript data
  schemes rejected, extras cleared, self-package de-targeted, other-package kept.
- On-device: tap a mailto: link (mail app opens), tel: (dialer pre-filled), a Play badge
  (Play Store), intent:// link with fallback on a non-installed app (falls back in-page); a
  scripted/no-gesture external navigation shows the confirm prompt (never auto-launches);
  incognito always confirms.
