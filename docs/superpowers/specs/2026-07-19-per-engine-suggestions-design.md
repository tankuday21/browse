# Per-Engine Search Suggestions + Incognito Suggest Gate (v5.9)

**Goal:** Address-bar autocomplete suggestions come from the *selected* search engine, and
never leave the device from incognito. Closes the limitation documented in the v5.8 spec
(suggestions were hard-wired to Google regardless of engine choice).

## Privacy rules (the point of the feature)

1. **Suggestions follow the engine.** Google/DuckDuckGo/Bing each have a public
   OpenSearch-style suggest endpoint returning the same JSON shape
   (`["query", ["s1", "s2", …]]`):
   - Google: `https://suggestqueries.google.com/complete/search?client=firefox&q=%s`
   - DuckDuckGo: `https://duckduckgo.com/ac/?type=list&q=%s`
   - Bing: `https://www.bing.com/osjson.aspx?query=%s`
2. **Custom engines get NO network suggestions.** We don't know their suggest endpoint,
   and guessing (or silently falling back to Google) would send keystrokes to a party the
   user didn't pick. Local bookmark/history suggestions still appear.
3. **Incognito never queries the network.** Today `onAddressBarTextChanged` fetches Google
   suggestions even when the active tab is incognito — every keystroke leaves the device.
   Fix: when the active tab is incognito, the network fetch is skipped entirely. Local
   (on-device, Orbit-scoped) suggestions remain, matching Chrome's incognito behavior.

All endpoints are hard-coded HTTPS, source-direct, no API keys (standing constraint).

## Model

- `SearchEngine` enum gains `suggestUrl: String` (the three endpoints above, `%s` = query).
- `ResolvedSearchEngine` gains `suggestUrl: String?` — the built-in's endpoint, `null` when
  a custom engine is selected. `SearchEngines.resolve` fills it.
- `SuggestionEngine.suggest(query, orbitId, suggestUrl: String?)` — `null` skips the
  network branch (fetcher never invoked). Fetcher signature becomes
  `suspend (suggestUrl: String, query: String) -> List<String>`.
- `googleSuggest` → `openSearchSuggest(suggestUrl, query)`: substitutes the URL-encoded
  query into `%s`, keeps the 1500 ms timeouts. Body parsing extracted to a pure
  `parseOpenSearchSuggestions(body): List<String>` (JVM-testable; lenient — malformed →
  empty list, non-string entries dropped).
- VM: the suggest call passes
  `if (activeTabIsIncognito) null else resolvedSearchEngine.value.suggestUrl`.

## Testing

- Pure: `parseOpenSearchSuggestions` with real-shape Google/DDG/Bing bodies, malformed
  JSON, empty array, non-string entries.
- SuggestionEngine: `suggestUrl = null` → fetcher NOT invoked (recording fake that fails
  the test if called), locals still returned; non-null → fetcher receives that exact URL;
  fetcher failure → locals still returned (existing behavior preserved).
- VM: incognito active tab → fetcher not invoked; custom engine selected → fetcher not
  invoked; selecting DuckDuckGo → fetcher receives DDG's suggest URL.
- On-device: type with each built-in selected (watch suggestions change flavor), with a
  custom engine (locals only), and in incognito (locals only).
