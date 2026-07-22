# Full-Page Translate (v6.1)

**Goal:** Translate the live web page in place, like Chrome — on-device, offline, no API keys,
no third-party servers. The text being read never leaves the phone; only the language *model*
is downloaded once from Google's ML Kit servers.

## Engine

- `com.google.mlkit:translate` — on-device translation. Per-language model (~30 MB) downloaded
  on demand; translation inference is fully local.
- `com.google.mlkit:language-id` — detects the page's source language from a text sample.
- Privacy: page text is translated locally and never transmitted. The one network touch is the
  one-time model download (Google SDK, not our code, per language). Surfaced honestly in the UI
  ("Downloading <Language> — one time"). Works in incognito (on-device, nothing persisted/leaked).

## Pure core (JVM-tested)

- `translate/TranslateLang.kt`:
  - `data class Lang(val code: String, val display: String)` + the ML Kit supported-language set
    with human names.
  - `defaultTarget(deviceLangCode): String` — the device language if supported, else "en".
  - `displayName(code): String`; `isSupported(code): Boolean`.
  - `needsTranslation(source, target): Boolean` — false when equal or source unknown.
- `translate/TranslatePayload.kt`:
  - `parseCollected(json: String): List<String>` — the ordered text strings the page collector
    returns (lenient: malformed → empty).
  - `buildApplyPayload(translations: List<String>): String` — the JSON array the apply script
    consumes, correctly escaped (a translated string may contain quotes, backslashes, newlines,
    `</script>`-like content). Round-trip and escaping are the test focus.

## ML Kit wrapper

- `translate/TranslateManager.kt` (Android, not unit-tested — thin Task→coroutine bridge):
  - `suspend fun detect(sample: String): String?` — BCP-47 code or null/"und".
  - `suspend fun ensureModel(source, target, requireWifi): Result<Unit>` — `downloadModelIfNeeded`.
  - `suspend fun translateAll(source, target, texts: List<String>): List<String>` — one Translator
    client for the pair, `translate` each; a failed item falls back to its original string (never
    drops a node). Client closed when done.
  - Caches nothing persistent; every call closes its client (models stay cached by ML Kit on disk).

## Page collection / application (device-verified)

- Injected JS via `WebView.evaluateJavascript` **round-trip** — NO persistent `@JavascriptInterface`
  (smaller attack surface; consistent with the privacy posture):
  - **Collect script**: walk the DOM, gather translatable text nodes (skip `script`/`style`/
    `noscript`/`textarea`/`code`/`pre`, hidden nodes, whitespace-only), tag each node with a
    `data-andromeda-tr` index and stash its original text on the element, return the ordered array
    of strings as JSON. Cap at `MAX_NODES = 2000` (documented; logs when truncated).
  - **Apply script**: given the translations array, write each back to its indexed node.
  - **Restore script**: swap every tagged node back to its stashed original ("Show original").
- `WebViewHolder.collectTranslatableText(tabId, cb)` / `applyTranslations(tabId, json)` /
  `restoreOriginal(tabId)` — thin evaluateJavascript wrappers on the tab's WebView.

## ViewModel + UI

- `BrowserViewModel.translateState: StateFlow<TranslateState>` —
  `Idle | Detecting | Downloading(lang) | Translating | Shown(target) | Error(msg)`.
- `onTranslatePage()`: collect text → detect source (sample of first N strings) → if
  `!needsTranslation` toast "Already in <lang>" → `ensureModel` (Downloading state) → `translateAll`
  (Translating) → `applyTranslations` → `Shown`. `onShowOriginal()`: `restoreOriginal` → `Idle`.
- Menu: "Translate page" in `BrowserMenuSheet` (disabled on `browse://home`).
- A slim translate bar (Orbit-styled) shows status; when `Shown`, offers target-language change
  and "Show original". Target language persisted: `SettingsRepository.translateTarget` (4-layer
  pref pattern), default = device language.

## Known limitations (documented)

- Translates the DOM present at trigger time; dynamically-added content (infinite scroll, SPA
  route changes) isn't re-translated in v6.1 — re-run Translate. (A MutationObserver auto-retrans
  is a fast-follow.)
- Node cap of 2000 (very long pages truncate the tail; logged, honest).
- ML Kit supports ~50+ languages; unsupported source → "Can't translate this page".

## Testing

- Unit (JVM): `TranslateLang` (default target from various locales incl. unsupported → en;
  needsTranslation equal/unknown/normal); `TranslatePayload` (parse malformed → empty; apply-payload
  escaping for quotes/backslashes/newlines/`</script>`; round-trip). VM: translateState transitions
  with a fake TranslateManager + fake collector (detect → download → translate → shown → original);
  already-in-target short-circuit; error path leaves a usable page.
- On-device: translate a Spanish news page to English, change target, Show original; a page already
  in the target; incognito; airplane-mode before model download (graceful error); a huge page.
