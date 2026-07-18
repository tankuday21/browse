# Camera Capture for Uploads (v5.3)

**Goal:** Complete v4.8's documented gap. `<input type="file" capture>` ("Take photo" buttons)
opens the camera directly, and ordinary image uploads offer the camera alongside the file
picker — Chrome parity.

## Decision logic — `browser/FileUploads` additions (pure, JVM-tested)

`captureMode(mimeTypes, captureEnabled, cameraAvailable): CaptureMode` — enum:
- **None** — camera can't or shouldn't appear: `cameraAvailable` false, or the accept list is
  non-empty and contains no `image/…` type (a PDF-only input gets no camera).
- **Direct** — the page asked for capture (`FileChooserParams.isCaptureEnabled`) AND declared
  an explicit `image/…` accept → the camera opens immediately, no chooser. (HTML Media
  Capture / Chrome parity: `capture` without an accept means "any file" — jumping to the
  camera would lock a PDF upload out of the picker, so that case only Offers.)
- **Offer** — images are acceptable (empty accept list counts — it means "anything") but no
  Direct conditions → the camera appears as an option inside the system chooser
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
- **Capture target:** a `File.createTempFile` under `cacheDir/captures/` exposed via the
  existing FileProvider (new `<cache-path name="captures">` entry; manifest authority now
  `${applicationId}.files` so a future applicationIdSuffix can't silently break it). The
  camera intent carries `EXTRA_OUTPUT` + grant flags AND explicit ClipData (review-hardened:
  grant flags only cover data/clipData; the platform's EXTRA_OUTPUT→ClipData migration is not
  guaranteed for intents nested in a chooser's `EXTRA_INITIAL_INTENTS` on OEM choosers).
  Stale capture files (>1 day) are reaped opportunistically on each new chooser.
- **Result:** camera apps return RESULT_OK with a null data intent — `parseChooserResult`
  yields null, then `resolveUploadResult` falls back to the pending capture URI when the file
  has bytes. Cancel deletes the unused temp file (including OEM save-then-back-out cancels —
  deliberate, Chrome parity).
- **Generation matching (review-hardened):** `FileChooserCoordinator.begin` now hands the
  launch a generation and `finish(generation, result)` drops mismatches; the Activity keeps a
  FIFO of launched generations to pair each launcher result with its launch. Without this, a
  superseded chooser's late result consumed the NEW request's capture slot — deleting the
  file and silently dropping the photo the user just took.
- **Direct fallback:** if the direct camera launch throws `ActivityNotFoundException`
  (API 30+ package visibility, no system camera), the plain picker launches instead — the
  input stays usable.

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
