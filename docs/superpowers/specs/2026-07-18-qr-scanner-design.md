# QR Code Scanner (v5.2)

**Goal:** Scan a QR code from inside the browser (deferred since v2). Web QRs open in a new
tab; app QRs (upi:, whatsapp:, intent://) hand off to the target app through the same hardened
v4.9 pipeline; plain-text QRs offer copy/search. Everything decodes **on-device** — ZXing core,
no Google Play Services, no network — consistent with Andromeda's privacy stance.

## Dependencies (new)

- `com.google.zxing:core` 3.5.3 — pure-Java QR decoding, offline.
- CameraX 1.3.4: `camera-camera2`, `camera-lifecycle`, `camera-view` (PreviewView).
No new manifest permissions — `CAMERA` is already declared (WebRTC prompts use it).

## Payload classification — `browser/QrPayload.kt` (pure, JVM-tested)

`classify(raw): Payload` — sealed:
- **Web(url)** — `http(s)://` as-is; ALSO a bare-domain heuristic (no whitespace, contains a
  dot, `UrlHosts.of("https://$it")` parses) → `https://` prefixed, since URL QRs in the wild
  often omit the scheme.
- **App(url)** — any other RFC-3986-schemed URI that `ExternalLinks.classify` maps to
  OpenApp/IntentUri (upi:, mailto:, whatsapp:, market:, intent://…).
- **Text(raw)** — everything else, INCLUDING unsafe schemes (`javascript:`, `file:`,
  `content:` per `ExternalLinks.unsafeSchemes`): a QR must never execute script or open local
  content — display-only, with copy/search actions.

## Scan screen — `ui/QrScanScreen.kt`

- Camera permission via `rememberLauncherForActivityResult(RequestPermission)`; denied state
  shows an inline explanation + retry (no dead screen).
- CameraX `PreviewView` in an `AndroidView`, back camera, `ImageAnalysis`
  (STRATEGY_KEEP_ONLY_LATEST) feeding ZXing (`MultiFormatReader`, QR_CODE only) with the
  Y-plane as `PlanarYUVLuminanceSource` (QR finder patterns are rotation-invariant).
- First successful decode wins: analyzer cleared immediately (no double-fire), haptic tick,
  then routing. Torch toggle + close button; a dimmed frame overlay marks the scan area.
- Camera is bound to the composable's lifecycle and unbound on dispose.

## Routing (MainActivity + VM)

- Nav route `"qrscan"`; menu entry "Scan QR code" in the browser menu sheet.
- **Web(url)** → `viewModel.onOpenInNewTab(url)`, pop back to browser.
- **App(url)** → user-initiated by construction (they aimed the camera), so dispatch directly:
  `intent://` through `IntentHardening.harden` (same component/BROWSABLE/flags/extras rules as
  v4.9), everything else as `ACTION_VIEW` + NEW_TASK; `RuntimeException`-wide catch → toast
  "No app can open this code". Pop back.
- **Text(raw)** → stays on the scan screen: result card with the text, Copy and Search
  buttons; Search runs `UrlInput.toLoadableUrl(text, searchEngine)` in a new tab (VM
  `onSearchFromQr`).

## Out of scope

Generating QR codes; scanning from a gallery image; barcode formats other than QR;
WiFi-QR auto-join (shows as text).

## Testing

- Unit (JVM): `QrPayload.classify` — http/https pass-through; bare domain → https-prefixed
  Web; upi//mailto/market/intent → App; javascript:/file:/content: → Text (never App/Web);
  random sentence → Text; whitespace trimmed; case-insensitive scheme.
- VM: `onSearchFromQr` opens a search-URL tab.
- On-device: scan a URL QR (tab opens), a UPI QR (payment app opens), a plain-text QR
  (copy/search work), deny camera permission (explanation + retry), torch toggle.
