# Ad Blocking at Maximum Strength on WebView — research + v3 upgrade plan

## How the serious blockers do it
Every major blocker (uBlock Origin, AdGuard, Adblock Plus, Brave Shields) is built on the same three pillars:

1. **Network filtering** — every subresource request is matched against Adblock-Plus-syntax rules:
   `||domain^` anchors, `|start`/`end|` anchors, `*` wildcards, `^` separators, and crucially the
   `$option` suffixes: `$domain=a.com|~b.com` (rule active only on those pages), `$third-party` /
   `$~third-party`, resource types (`$script,image,stylesheet,xhr,subdocument,font,media`),
   and exception rules `@@...` which override blocks. EasyList alone is ~70k such rules; our v1
   parser keeps only the bare-domain subset (~30% of the list's power).
2. **Cosmetic filtering** — `##selector` (generic), `domain.com##selector` (site-specific) and
   `#@#` exceptions, injected as CSS as early as possible so ads never flash.
3. **Multiple complementary lists** — the standard stack: **EasyList** (ads) + **EasyPrivacy**
   (trackers) + a mobile-specific list (**AdGuard Mobile Ads**) + **Peter Lowe's** hosts list
   (ad/tracking servers, hosts format) + our existing Fanboy Cookie list (annoyances). Lists are
   refreshed periodically (blockers update every few days).

Performance technique (uBlock): don't scan rules linearly — index rules by an 8-char token drawn
from the pattern; look up candidate rules by the tokens present in each request URL. Thousands of
requests/page stay O(tokens).

## WebView-specific reality (honest limits)
- `shouldInterceptRequest` sees HTTP(S) subresources — this is our interception point. It does NOT
  see WebSockets; `$websocket`/`$ping` rules are unenforceable. Main-frame documents are seen but
  we deliberately never block them (bad UX; matches Chrome-mobile blockers).
- No extension APIs → no scriptlet injection, no procedural cosmetics (`:has(...)` etc.). We skip
  those rules safely (they're a small minority).
- YouTube's ads are largely server-stitched/same-origin → partially blockable at best (true for
  every WebView browser; even Brave struggles there).
- Resource type must be inferred (Accept header + URL extension + isForMainFrame) — accurate
  enough in practice.
- Third-party = registrable-domain comparison; we approximate eTLD+1 with a compact multi-part
  TLD table (co.uk, com.au, co.in, ...) instead of bundling the full Public Suffix List.

## What we ship now (branch feature/v3-adblock-max)
1. **AbpParser** (new, replaces FilterListParser semantics; pure, heavily unit-tested):
   full network-rule syntax above incl. hosts-format lines (`0.0.0.0 adhost.com`), skipping
   unsupported rule classes explicitly ($popup/$csp/$redirect/$removeparam/regex//$important
   treated as plain block where safe, else skipped); cosmetic rules incl. per-domain and #@#.
2. **AdBlockEngine v2** (same public surface, new internals): token-indexed rule matching with
   RequestContext(url, requestHost, pageHost, type, thirdParty); exception precedence; per-site
   allowlist and master toggle unchanged; per-host cosmetic CSS composed from generic + specific
   − exceptions, LRU-cached; injected at onPageStarted (early) and onPageFinished (late DOM).
3. **Lists**: bundled snapshots of EasyList (refreshed), EasyPrivacy, AdGuard Mobile Ads,
   Peter Lowe's; **FilterListUpdater** (WorkManager, weekly, unmetered) refreshes into filesDir
   with assets as permanent fallback; per-list toggles + "Update now" + last-updated in Settings >
   Ad blocking; the existing annoyance list rides the same machinery.
4. Blocked-counter/stats and incognito behavior unchanged.

Sources: EasyList/EasyPrivacy (easylist.to), AdGuard filters KB (adguard.com/kb), uBlock static
filter syntax wiki (github.com/gorhill/uBlock), ABP filter cheatsheet (adblockplus.org),
Peter Lowe's list (pgl.yoyo.org).
