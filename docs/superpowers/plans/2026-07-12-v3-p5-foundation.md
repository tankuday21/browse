# V3 Phase 5 — Foundation — Implementation Plan (compact, build-first)

> Build-first mode per owner: write unit tests but do NOT run suites; verify with assembleDebug + compileDebugUnitTestKotlin only. All testing happens in the consolidated v3 pass.

**Goal:** F1 autofill, J1 backup/restore, J2 onboarding, J4 app shortcuts, C1 home shortcut grid, A2 voice search, A5 bar long-press, A6 clipboard chip, I3 text scaling.

**Branch:** `feature/v3-p5-foundation` off main; merge --no-ff; tag v3-phase-5. Room migration **v8→v9** (`home_shortcuts`).

## Batch A — data: schema v9 + C1 home grid + J1 backup/restore

### Schema v9 + C1 home shortcut grid
- `home_shortcuts` table: `id` PK autoincrement, `url` TEXT NOT NULL, `title` TEXT NOT NULL, `position` INTEGER NOT NULL. DAO: observeAll ORDER BY position, insert, deleteById, `@Update` or position-batch update `move(id,newPos)` implemented as full-list rewrite (`@Transaction` replaceAll(list)). MIGRATION_8_9 creates table AND seeds it from the user's existing speed-dial source (bookmarks currently shown on home — inspect HomePage: if home shows bookmarks, seed top 8 bookmarks as shortcuts so the grid isn't empty after update).
- HomePage: replace bookmark speed-dial with the grid (favicon-letter tile + title, existing tile visuals); "+" tile opens add dialog (prefilled with current clipboard URL if any, else blank url+title fields); long-press tile → dropdown: Remove / Move up / Move left etc. (simple: Remove + drag not required — Move to front) — keep simple: Remove, Move to front. Menu: "Add to home" item adds current page (dedupe by url, toast feedback).
- VM: homeShortcuts StateFlow, onAddShortcut(url,title), onRemoveShortcut(id), onMoveShortcutToFront(id). Fake dao for tests.

### J1 backup/restore
- Pure `browser/BackupCodec.kt`: versioned JSON (schemaVersion=1) containing: settings (all SettingsRepository values as a flat map — enumerate explicitly), bookmarks (with folders — study Bookmark entity), homeShortcuts, readingList METADATA (url/title/addedAt/readAt — no article files), tabGroups (name/color). `encode(Backup): String` / `decode(String): Backup?` (null on bad/unknown-newer version). Unit test: full round-trip + decode(garbage)=null + unknown version rejected.
- Settings > "Backup & restore": "Back up" → SAF CREATE_DOCUMENT (`andromeda-backup-<date>.json`, mime application/json); "Restore" → OPEN_DOCUMENT → confirm dialog ("Merges with your current data") → merge semantics: bookmarks/shortcuts/reading-list dedupe by url, settings overwrite, groups dedupe by name. Toast success/fail counts. Follow BookmarkIO SAF patterns (v2 import/export exists — reuse the launcher wiring style).

## Batch B — UX: J2 onboarding + J4 shortcuts + A2 voice + A5 long-press + A6 clipboard + I3 text scaling

### J2 onboarding (first-run, 3 skippable screens)
- DataStore flag `onboardingDone` default false; MainActivity shows OnboardingScreen overlay when false. HorizontalPager 3 pages: (1) logo + "Your private power browser" + 3 feature bullets; (2) "Bring your bookmarks" → button reuses existing bookmark HTML import (BookmarkIO + SAF launcher); (3) "Make Andromeda your default" → existing RoleManager default-browser flow (find it in SettingsScreen). Skip (top-right) + Next/Done; setting flag ends it forever.

### J4 app shortcuts
- `res/xml/shortcuts.xml` + manifest meta-data: "New tab" (launches MainActivity with EXTRA action `andromeda.NEW_TAB`), "New incognito tab" (`andromeda.NEW_INCOGNITO`). MainActivity onNewIntent/onCreate handles extras → VM new tab actions (find existing new-tab/new-incognito VM calls).

### A2 voice search
- Mic icon in CommandBar edit state (trailing icon next to clear): `RecognizerIntent.ACTION_RECOGNIZE_SPEECH` via rememberLauncherForActivityResult(StartActivityForResult); result top text → submit as search (existing UrlInput/search path). Hide icon if intent unresolvable (packageManager query).

### A5 bar long-press
- Long-press on the URL bar (non-edit display state) → DropdownMenu: "Copy URL", "Paste and go" (clipboard text → navigate/search), "Share". Reuse existing share intent code if present.

### A6 clipboard chip
- On entering bar edit/focus state: read clipboard ONCE (ClipboardManager); if it's a URL (UrlInput's existing URL detection) and differs from current page, show one suggestion chip/row "Go to copied link — <host>" atop suggestions; tap navigates. Never read clipboard except at that moment.

### I3 text scaling
- Settings > Accessibility section: "Text size" slider 50–200% (default 100) writing `SettingsRepository.textScale: Flow<Int>`; applied as base `settings.textZoom` in WebViewHolder for all tabs (site override from site_settings WINS over global — resolver already treats override>0 as absolute; global feeds the resolver's default: change SiteSettingsResolver.resolve to take `globalTextZoom: Int` with unset→global instead of 100; update its test). Live-apply to all open WebViews on change. Plus force-enable pinch zoom (`setSupportZoom(true)`, `builtInZoomControls=true`, `displayZoomControls=false`) — verify current state, set if missing.

## Files
Batch A: data/HomeShortcutEntity+Dao (new), BrowseDatabase v9, browser/BackupCodec.kt (new+test), ui/HomePage.kt, SettingsScreen (backup section), VM, fakes.
Batch B: ui/OnboardingScreen.kt (new), res/xml/shortcuts.xml (new), AndroidManifest, MainActivity, ui/components/CommandBar.kt, ui/components/SuggestionsPanel.kt, WebViewHolder, browser/SiteSettingsResolver.kt (+test update), SettingsRepository (+textScale, onboardingDone), SettingsScreen (accessibility).

Commits: `feat(v3-p5): home shortcut grid + schema v9 (C1)`, `feat(v3-p5): backup and restore (J1)`, `feat(v3-p5): onboarding + app shortcuts (J2 J4)`, `feat(v3-p5): voice search, bar long-press, clipboard chip (A2 A5 A6)`, `feat(v3-p5): global text scaling (I3)` — combine sensibly, min 2 per batch.

**F1 autofill note:** verify-and-fix scope — during the consolidated test pass, check `WebSettings` autofill provisioning (importantForAutofill, hints); no build work unless something is off. Recorded here so it isn't lost.
