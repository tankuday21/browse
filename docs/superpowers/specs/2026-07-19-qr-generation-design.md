# QR Generation — Share Page as QR (v5.4)

**Goal:** The bookend to v5.2's scanner: menu → "Share page as QR" renders the current page's
URL as a QR code in a bottom sheet — another phone scans it straight off the screen. Fully
on-device (ZXing's encoder is already bundled and pure Java); zero new dependencies.

## Encoder — `browser/QrGenerate.kt` (pure, JVM-tested)

`encode(text, size = 512): BitMatrix?` — `QRCodeWriter` with error correction M and margin 1;
null for blank input or encoder failure (over-long text). Pure ZXing types, no android.graphics
— the JVM test does a full round trip: encode a URL → wrap the matrix as a luminance source →
decode with the same reader the scanner uses → assert the text survives.

## UI — `ui/components/QrShareSheet.kt`

ModalBottomSheet (Orbit surfaces):
- the QR drawn directly from the BitMatrix on a Compose `Canvas` (no Bitmap needed to display)
  on a WHITE card regardless of theme — scanners need dark-on-light with a quiet zone;
- the page title + URL caption beneath (single line, ellipsized);
- actions: **Copy link** (plain clipboard — a URL isn't sensitive), **Share image** (render the
  matrix into a Bitmap → PNG in `cacheDir/qr/` → existing FileProvider → `ACTION_SEND`
  `image/png` with the URL as `EXTRA_TEXT`), Close. Share files reuse one fixed name
  (`share-qr.png`) — overwritten per share, nothing accumulates.

## Wiring

- `BrowserMenuSheet`: "Share page as QR" row (QrCode2 icon) in the This-page section, hidden on
  the home page (nothing to share).
- `BrowserScreen` owns the sheet state; the current tab's url/title come from uiState.
- `file_paths.xml`: add `<cache-path name="qr" path="qr/">`.
- Incognito: allowed — the user is deliberately showing the URL on their own screen; nothing
  is persisted beyond the overwritten share PNG, and only when Share image is tapped.

## Out of scope

QR for arbitrary text/WiFi; customized styling/logo; SVG export.

## Testing

- Unit (JVM): round trip encode→decode preserves the URL (long URL included); blank → null;
  requested size honored (matrix dimensions ≥ size); over-capacity input → null, no throw.
- On-device: menu → sheet shows a scannable code (scan it with v5.2's scanner on the same
  build for the full-circle demo); Copy link works; Share image attaches a PNG; hidden on home.
