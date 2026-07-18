# File Uploads — `<input type="file">` support (v4.8)

**Goal:** Make file-upload buttons on websites work. Today the app never overrides
`WebChromeClient.onShowFileChooser`, so tapping any "Upload photo" / "Attach file" control does
nothing — silently. v4.8 wires the WebView's file-chooser request to the system document picker.

## The WebView contract (the part that must be right)

`onShowFileChooser(view, filePathCallback, fileChooserParams)` hands us a
`ValueCallback<Array<Uri>>` that MUST be invoked **exactly once**:

- never invoking it leaves the page's input stuck (the WebView won't fire the chooser again for
  that input until resolved);
- invoking it twice throws.

So: every path — picked, cancelled, no picker app installed, a second chooser superseding the
first — resolves the callback exactly once (`null` = "no file chosen").

## Architecture

Mirrors the existing permission-request flow: WebViewHolder forwards to a `Listener` method;
MainActivity (the only Listener implementation) owns the Activity-side plumbing.

1. **WebViewHolder** — override `onShowFileChooser` in the existing `WebChromeClient`, delegate
   to a new `Listener.onShowFileChooser(filePathCallback, params): Boolean`. No incognito gate:
   a file pick is explicit user action and writes nothing to disk on our side.
2. **MainActivity** — holds `pendingFileChooser: ValueCallback<Array<Uri>>?` plus an
   `ActivityResultLauncher<Intent>` (`StartActivityForResult`, registered as a field — required
   before RESUMED). On a new request: resolve any stale pending callback with `null` first, store
   the new one, build an `ACTION_GET_CONTENT` intent honoring the page's `accept` types and
   multi-select mode, and launch. On result: parse single (`data.data`) or multiple
   (`data.clipData`) URIs, resolve, clear. `ActivityNotFoundException` → resolve `null`
   (still return `true` — we consumed the callback).
3. **`browser/FileUploads.kt`** — pure helpers, JVM-unit-testable:
   - `normalizeAcceptTypes(acceptTypes, extensionToMime)` — HTML `accept` entries arrive as MIME
     types (`image/*`) or dot-extensions (`.pdf`); split on commas, trim, lowercase, map
     extensions through an injected `extensionToMime` (production: `MimeTypeMap`), drop unknowns,
     dedupe. Result drives `EXTRA_MIME_TYPES` (single type → `intent.type`, several → `*/*` +
     extras, none → `*/*`).
   - `parseChooserResult(ok, single, clip)` — generic over the URI type so tests run on the JVM:
     cancel → null; clipData (multi-select) wins over single; OK-but-empty → null.

## Intent shape

`ACTION_GET_CONTENT` + `CATEGORY_OPENABLE` (guarantees openable streams — WebView needs to read
the content), `EXTRA_ALLOW_MULTIPLE` when `params.mode == MODE_OPEN_MULTIPLE`, wrapped in
`Intent.createChooser`. No new manifest permissions: SAF grants per-URI read access.

## Out of scope (documented, deferred)

- `capture` attribute → direct camera/mic capture (needs FileProvider temp file + CAMERA
  permission dance). Users can still pick an existing photo.
- Drag-and-drop uploads.

## Testing

- Unit (JVM): `normalizeAcceptTypes` — mixed mime+extension lists, comma-joined single entry,
  blanks, unknown extensions dropped, dedupe, case-folding. `parseChooserResult` — cancel,
  single, multi-beats-single, OK-with-nothing.
- On-device: file input on a real site (e.g. image upload) opens the picker; picked file
  uploads; cancel leaves the page functional and a second tap re-opens the picker.
