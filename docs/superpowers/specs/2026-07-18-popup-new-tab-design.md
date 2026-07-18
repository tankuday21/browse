# Popups → New Tab — window.open / target="_blank" (v5.0)

**Goal:** Links with `target="_blank"` and gesture-backed `window.open()` open a NEW tab. Today
`setSupportMultipleWindows` is off and `onCreateWindow` is not overridden, so such links load in
the SAME WebView (replacing the page) or are suppressed — there is no popup handling at all.

## Design: interceptor capture (not transport adoption)

`onCreateWindow` is synchronous — the engine demands a WebView via `WebViewTransport` before the
callback returns — but our tab creation is a suspend DB insert, and tab WebViews are created
lazily on first display. Bridging that with a real adopted popup WebView means surgery on the
tab-id scheme and rebinding races. v5.0 instead uses a **throwaway interceptor WebView**:

1. `settings.setSupportMultipleWindows(true)` in `obtain()` (both branches).
   `javaScriptCanOpenWindowsAutomatically` stays false — the engine itself suppresses
   gesture-less scripted `window.open`, our first popup-blocker layer.
2. `onCreateWindow(view, isDialog, isUserGesture, resultMsg)`:
   - `!isUserGesture` → return false (popup blocked, silently — Chrome-style; no infobar yet);
   - otherwise create a bare interceptor `WebView`, hand it to the transport, and capture the
     popup's FIRST navigation via its `WebViewClient.shouldOverrideUrlLoading` (engine-initiated
     child loads route through it; `onPageStarted` as belt-and-braces for engines that don't).
     On capture: tear the interceptor down (posted, never synchronously inside the callback)
     and fire `Listener.onCreateWindow(parentTabId, url)`.
   - Interceptors are tracked per parent tab and destroyed on `close(tabId)` / `destroyAll()`
     so an uncaptured popup (about:blank that never navigates) can't leak a WebView.
3. **MainActivity** forwards to `BrowserViewModel.onPopupWindow(parentTabId, url)`.
4. **BrowserViewModel.onPopupWindow** mirrors `onOpenInNewTab` but keyed on the PARENT tab
   (not the active tab — defensive): inherits the parent's incognito mode, its Orbit, and its
   tab island via `TabGroupPolicy.groupForNewTab`; the new tab foregrounds (existing `newTab`
   behavior — matches how "open in new tab" already works in this app).

The popup URL then loads through the tab's normal pipeline, so everything downstream applies
for free: HTTPS-Only, ad-block, Safe Browsing, and the v4.9 external-scheme handling (a popup
straight to `upi:`/`intent://` lands in the confirm prompt — gesture bit is gone by then, which
is exactly right for a popup).

## Interceptor containment (review-hardened)

The interceptor must never touch network or disk — an incognito/Orbit parent's popup must not
leak a request through the default profile before capture:
- `shouldInterceptRequest` blanks EVERY request (the decisive layer, whatever callback path the
  engine picks);
- `onPageStarted` calls `stopLoading()` before capturing (POST navigations skip
  `shouldOverrideUrlLoading` contractually);
- `cacheMode = LOAD_NO_CACHE`, JS off (default), incognito parents get the incognito profile as
  a final belt;
- capture is identity-gated on the interceptor map — a late engine callback after supersede or
  parent-close must not resurrect a dropped popup or double-destroy;
- un-navigated interceptors are reaped after a 10s grace period (no pinned renderer).

## Foregrounding rule

The popup foregrounds only while its parent is still the active tab (the overwhelmingly common
case — capture is near-instant). If the user already switched tab/Orbit/mode, the popup opens
in the background: activating a tab from another Orbit would break the active-tab ↔
active-Orbit invariant the tab switcher's filter relies on. `TabManager.newTab` gained a
`foreground: Boolean = true` parameter for this.

## Honest limits (documented, deliberate)

- **`window.opener` is severed.** The popup opens as an independent tab; OAuth popups that
  `postMessage` back to their opener won't complete. Phase 2 (transport adoption) fixes this;
  most sign-in flows offer a redirect fallback.
- **A form POST to `target="_blank"` degrades to a GET of the action URL** — the body can't be
  captured (POSTs bypass `shouldOverrideUrlLoading` and `onPageStarted` only exposes the URL).
  Rare pattern; Phase 2's transport adoption fixes it properly.
- A popup that never navigates (about:blank + `document.write`) is dropped — its interceptor
  is reaped after 10s.
- No "popup blocked" UI for gesture-less attempts (engine + gate just refuse). Gesture-less
  `target="_blank"` clicks simulated by JS previously loaded in-place; they are now blocked —
  intended popup-blocker behavior, Chrome-consistent.

## Incognito

The interceptor WebView never gets a profile, JS bridges, or storage — it exists only to read
the first URL and dies. The real popup tab inherits the parent's incognito flag, so an
incognito page's popup is an incognito tab (profile `"incognito"`, no persistence) — the same
guarantee as `onOpenInNewTab`.

## Testing

- Unit (JVM): `onPopupWindow` — inherits parent incognito (popup from incognito tab is
  incognito); inherits parent Orbit; joins the parent's island per `TabGroupPolicy`; foregrounds
  the new tab; unknown parentTabId falls back to the active tab's context.
- On-device: `target="_blank"` link opens a new tab (old page still in the previous tab);
  gesture-backed `window.open` opens a new tab; scripted no-gesture `window.open` on load is
  blocked; popup from an incognito tab is incognito; parent tab's back stack unaffected.
