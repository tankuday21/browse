# Camera Capture for Uploads (v5.3)

**Goal:** Complete v4.8's documented gap. `<input type="file" capture>` ("Take photo" buttons)
opens the camera directly, and ordinary image uploads offer the camera alongside the file
picker — Chrome parity.

## Decision logic — `browser/FileUploads` additions (pure, JVM-tested)

`captureMode(mimeTypes, captureEnabled, cameraAvailable): CaptureMode` — enum:
- **None** — camera can't or shouldn't appear: `cameraAvailable` false, or the accept list is
  non-empty and contains no `image/…` type (a PDF-only input gets no camera).
- **Direct** — the page asked for capture (`FileChooserParams.isCaptureEnabled`) and images
  are acceptable → the camera opens immediately, no chooser.
- **Offer** — images are acceptable (empty accept list counts — it means "anything") but no
  capture attribute → the camera appears as an option inside the system chooser
  (`EXTRA_INITIAL_INTENTS`).

`resolveUploadResult(picked, capture, captureHasData)` (generic, pure) — the result-parsing
extension: the picker's URIs win; when the picker returned nothing but the camera wrote into
our capture file, the capture URI is the result; else null (exactly-once contract unchanged —
`FileChooserCoordinator` is untouched).

## Activity side (MainActivity)

- **`cameraAvailable`** = `checkSelfPermission(CAMERA) == GRANTED`. Critical Android gotcha:
  because the manifest DECLARES the CAMERA permission (WebRTC/QR need it), launching
  `ACTION_IMAGE_CAPTURE` without holding it throws `SecurityException` — apps that don't
  declare it may launch freely, we may not. Without the grant the camera option simply doesn't
  appear (the file picker still works); no permission prompt mid-upload in this phase.
- **Capture target:** `cacheDir/captures/upload-<millis>.jpg` exposed via the existing
  FileProvider (new `<cache-path name="captures" path="captures/">` entry). The camera intent
  carries `EXTRA_OUTPUT` + `FLAG_GRANT_READ/WRITE_URI_PERMISSION`. Stale capture files (>1 day)
  are deleted opportunistically on each new chooser.
- **Result:** camera apps return RESULT_OK with a null data intent — `parseChooserResult`
  yields null, then `resolveUploadResult` falls back to the pending capture URI when the file
  has bytes. Cancel deletes the unused temp file. `pendingCaptureUri` is cleared with the
  pending callback lifecycle (one chooser at a time, as before).

## Out of scope

Video capture (`capture` on `accept="video/*"` still gets the picker); requesting the CAMERA
permission from inside the upload flow; front/back hint (`capture="user"`).

## Testing

- Unit (JVM): `captureMode` matrix — no camera → None; pdf-only → None; image/* + capture →
  Direct; image/* no capture → Offer; empty accepts → Offer (or Direct with capture); mixed
  pdf+image → Offer. `resolveUploadResult` — picker wins; camera fallback only with data;
  neither → null.
- On-device: a "Take photo" input opens the camera and the shot uploads; a plain image input
  shows the camera inside the chooser; canceling the camera leaves the input working; a
  PDF-only input shows no camera.
