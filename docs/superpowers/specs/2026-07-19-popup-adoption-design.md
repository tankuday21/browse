# Popup Adoption — window.opener support (v5.6)

**Goal:** Replace v5.0's interceptor-capture with TRUE popup adoption: the WebView handed to
the engine's `WebViewTransport` IS the new tab's WebView. This restores `window.opener` /
`postMessage` (OAuth sign-in popups complete), fixes `target="_blank"` form POSTs (the body is
no longer lost), and lets `window.close()` close the popup tab. v5.0's two documented limits
disappear.

## The key design move: synchronous tab-id allocation

v5.0 couldn't adopt because `onCreateWindow` must return a WebView synchronously while tab ids
came from a suspend Room autoincrement — and the WebView builder's callbacks capture `tabId`
in closures, so a provisional-id-then-rebind design would mis-attribute every later callback.

Fix: **TabManager allocates ids synchronously.**
- `allocateTabId(incognito)`: incognito → the existing `nextIncognitoId--`; normal → an
  `AtomicLong` seeded in `initialize()` at `max(stored ids) + 1`.
- ALL tab creation goes through the allocator (`newTab` included): rows insert with EXPLICIT
  ids. SQLite accepts explicit primary keys and advances the autoincrement sequence past them,
  and with every path serialized through one AtomicLong there is no allocate/insert collision
  window at all.
- The popup WebView is therefore born under its REAL tab id — no rebinding, no closure
  refactor, callbacks attribute correctly from the first byte.

## Flow

1. `onCreateWindow(view, isDialog, isUserGesture, resultMsg)` — `!isUserGesture` → return
   false (popup blocker, unchanged).
2. `Listener.onCreatePopup(parentTabId): PopupTabSpec?` — SYNCHRONOUS. The VM allocates the id
   and returns `(tabId, incognito, profileKey)` derived from the PARENT tab (incognito parent →
   incognito popup with the `"incognito"` profile; normal parent → the parent Orbit's profile).
   Null (no VM / parent vanished) → return false, popup blocked.
3. Holder: `obtain(spec.tabId, spec.incognito, spec.profileKey)` — the NORMAL builder, full
   settings/clients/bridges — hand it to the transport, `sendToTarget()`.
4. `Listener.onPopupReady(tabId, parentTabId)` — the VM registers the tab IN MEMORY
   synchronously via `TabManager.registerTabInMemory(...)` with `url = ""` (review-hardened:
   the engine starts the transport load immediately, and onPageStarted/onContentChanged must
   FIND the tab or the first URL is dropped and history mis-attributes to the active Orbit);
   only the row insert (`persistRegisteredTab`) is async. Foreground rule unchanged from v5.0:
   foreground only while the parent is still the active tab. Island/Orbit inheritance unchanged.
   **Cold-start gate (review Critical):** `newTab` awaits an `initialized` deferred completed
   after `initialize()` seeds the allocator — an external VIEW intent can reach `newTab` before
   the DB read finishes, and an unseeded allocation would insert id 1 over an existing row
   (Room ABORT → crash on every link tap from another app).
5. `TabWebView`'s create-on-first-show guard becomes `if (wv.url == null && tabUrl.isNotBlank())`
   — a blank-url popup row must NOT trigger a load that would clobber the engine's transport
   load. The row's real URL arrives via the normal `onPageStarted`/`onContentChanged` path, so
   process-death restore reloads the committed URL as usual.
6. `onCloseWindow(window)` — new: the page called `window.close()`. Delivered to the closing
   window's own chrome client (closure tabId identifies it) and GATED to adopted popup ids
   (review-hardened): Chromium also honors `close()` on any window whose session history is
   length 1, and a page must never be able to close a tab the USER opened. The VM closes
   through the normal path (close-ring, next-active selection).

The v5.0 interceptor machinery (`popupInterceptors`, reap timer, capture identity gates) is
DELETED — adoption makes it dead code. `ExternalLinks`/HTTPS-Only/ad-block still apply to the
popup's navigations because it runs the full standard WebViewClient.

## Edge behavior

- Popup row persists with its committed URL (via onContentChanged) → survives restart like any
  tab; `url = ""` rows that never navigated restore as blank tabs and can be closed normally.
- A popup from an incognito parent is a NEGATIVE-id in-memory tab (never persisted) — same as
  every incognito tab; `registerPopupTab` routes accordingly.
- If the row insert somehow never lands, the WebView sits unused in the holder map until
  `destroyAll` — bounded, no user-visible effect.

## Testing

- Unit (JVM): TabManager — allocator yields unique ids interleaved with `newTab` inserts;
  `registerPopupTab` inserts with the explicit id, background by default, foregrounds when
  asked; incognito allocation stays negative/in-memory. VM — `onCreatePopup` inherits the
  parent's incognito + Orbit profile and returns null for unknown parents; `onPopupReady`
  applies the v5.0 foreground rule + island inheritance (existing popup tests adapt);
  `onCloseWindow` closes exactly the popup tab.
- On-device: an OAuth-style popup (window.opener.postMessage) completes; target="_blank" form
  POST submits the body; window.close() closes the popup tab; popup from incognito stays
  incognito; popup persists across restart with its URL.
