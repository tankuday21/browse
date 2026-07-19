# Custom Search Engines (v5.8)

**Goal:** Add-your-own search engine — name + query template — alongside the built-ins
(Google/DuckDuckGo/Bing). The privacy audience this browser courts wants Kagi, Startpage,
Brave Search, or a self-hosted SearXNG instance.

## Model (minimal churn)

- Built-ins stay the `SearchEngine` enum, persisted as today (`search_engine` = enum name).
- Customs are a JSON list in a new DataStore string pref (`custom_search_engines`, mirroring
  the `weatherCache` JSON-blob pattern) + a `selected_custom_engine` name pref (blank → the
  enum choice applies).
- `browser/SearchEngines.kt` (pure, JVM-tested):
  - `CustomSearchEngine(name, template)` + JSON encode/decode (org.json, already used);
  - `validate(name, template)`: non-blank name, template starts `https://` (an engine
    receiving queries over plaintext is a privacy hole) and contains `%s`;
  - `ResolvedSearchEngine(label, queryUrl)` + `resolve(builtIn, customs, selectedName)` —
    the selected custom when it still exists, else the built-in.
- `UrlInput.toLoadableUrl` search branch learns templates: `%s` present → substitute the
  encoded query (all occurrences); else append as today (built-ins unchanged).

## VM

- `resolvedSearchEngine: StateFlow<ResolvedSearchEngine>` = combine(enum, customs, selected)
  via `SearchEngines.resolve`; the three `searchEngine.value.queryUrl` callsites (suggestion
  tap, address-bar submit, QR search) switch to it.
- `onAddCustomEngine(name, template)` (validated; same-name replaces),
  `onRemoveCustomEngine(name)` (removing the SELECTED one clears the selection — silent
  fallback to the built-in), `onSelectCustomEngine(name?)` (null = a built-in was picked;
  picking a built-in radio also clears the custom selection).

## Settings UI

The existing Search-engine radio section grows: built-in rows (selecting one clears the
custom selection), then each custom engine as a radio row with a delete icon, then an
"Add custom engine" row opening a dialog (name + template `OrbitTextField`s, inline
validation — Save disabled until valid, hint text for `%s`).

## Backup/restore

`settingsSnapshot` gains `customSearchEngines` (the JSON) + `selectedCustomEngine`; restore
applies both leniently (invalid JSON skipped). The existing `searchEngine` enum key unchanged.

## Known limitation (documented, existing behavior)

Address-bar autocomplete suggestions remain Google's endpoint regardless of engine choice
(SuggestionEngine is hard-wired; per-engine suggest URLs are a separate feature). Noted so
the privacy story stays honest.

## Testing

- Unit (JVM): codec round-trip + malformed JSON → empty; validate matrix (blank name,
  http://, missing %s, valid); resolve (custom selected / vanished selection falls back /
  blank selection); toLoadableUrl with %s templates (substitution, multiple %s, encoding)
  and legacy append unchanged.
- VM: add → selectable → resolved queryUrl drives the search callsites; same-name add
  replaces; remove selected → fallback to built-in; backup/restore round-trips customs +
  selection.
- On-device: add Startpage (`https://www.startpage.com/sp/search?query=%s`), search from the
  address bar, QR-search, suggestion tap; delete it; restore a backup containing customs.
