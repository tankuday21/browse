# Andromeda v3.1 Premium UI Redesign — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refactor Andromeda's UI onto one premium design system — a shrink-not-hide bottom bar, a Focused customizable home, a bottom-sheet menu, and shared tokens/components across every screen — without adding features or changing architecture.

**Architecture:** A single token layer in `ui/theme/` (surfaces, spacing, radii, type, motion, accent) exposed through a `MaterialTheme` extension so every composable reads the same values. One shared `OmniBar` (full/slim/edit states, content laid out above it), one shared bottom-sheet menu, one shared list-row + top-app-bar, applied across all screens. Pure logic (bar shrink hysteresis, home prefs) is unit-tested; Compose visuals are build-verified and checked on device.

**Tech Stack:** Kotlin, Jetpack Compose (Material3), DataStore, existing `browser/BarScrollPolicy`. No new Gradle dependencies. No Room migration.

## Global Constraints

- No new Gradle dependencies. No DB migration (Home prefs are DataStore only).
- No behavior/feature changes — this is look, layout, spacing, and motion only. Every existing menu action, setting, and screen keeps working identically.
- Dark is the signature theme; light must remain first-class, derived from the same tokens.
- Windows build: `.\gradlew.bat` from `F:\Dev\Browse`; if JAVA_HOME error: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"`.
- Build-first verification per task: `.\gradlew.bat testDebugUnitTest` (for logic) + `.\gradlew.bat assembleDebug`. Full device regression at the end.
- Spacing snaps to `4/8/12/16/24/32`; radii to `chip 10 / card 16 / bar-sheet 22 / pill 50%`; never introduce off-scale literals.
- Incognito rules unchanged: no stats on incognito home; incognito disclaimer preserved.
- Branch: `feature/v3.1-premium-ui` off main; merge `--no-ff`; tag `v3.1` after device verification.

---

## Phase 1 — Design token foundation

### Task 1: Orbit token module

**Files:**
- Modify: `app/src/main/java/com/udaytank/browse/ui/theme/Orbit.kt`
- Modify: `app/src/main/java/com/udaytank/browse/ui/theme/Color.kt`
- Modify: `app/src/main/java/com/udaytank/browse/ui/theme/Type.kt`
- Modify: `app/src/main/java/com/udaytank/browse/ui/theme/Theme.kt`
- Test: `app/src/test/java/com/udaytank/browse/ui/theme/OrbitTokensTest.kt`

**Interfaces produced (later tasks consume verbatim):**
```kotlin
// Orbit.kt — the single source of truth, read via LocalOrbit / MaterialTheme.
object OrbitSpacing { val xs = 4.dp; val sm = 8.dp; val md = 12.dp; val lg = 16.dp; val xl = 24.dp; val xxl = 32.dp }
object OrbitRadii { val chip = 10.dp; val card = 16.dp; val bar = 22.dp; val pill = 50 /*percent*/ }
data class OrbitSurfaces(val base: Color, val surface: Color, val elevated: Color)
data class OrbitText(val primary: Color, val secondary: Color, val muted: Color)
data class OrbitAccent(val solid: Color, val gradient: List<Color>)  // gradient = [#1E4FD8, #35C3F3]
object OrbitMotion {
    val structural: AnimationSpec<Float>   // ~220ms medium-soft spring, for sheets + bar shrink
    val quick: AnimationSpec<Float>        // ~120ms tween, for taps/toggles
    // Dp variants exposed as structuralDp()/quickDp() helpers returning tween/spring<Dp>.
}
data class OrbitScheme(val surfaces: OrbitSurfaces, val text: OrbitText, val accent: OrbitAccent, val dark: Boolean)
val LocalOrbit: ProvidableCompositionLocal<OrbitScheme>
@Composable fun orbit(): OrbitScheme = LocalOrbit.current
val darkOrbit: OrbitScheme   // base #08081C surface #12122E elevated #181840; text #E6E8F5/#C6C8E0/#8A8CB5
val lightOrbit: OrbitScheme  // inverted neutrals, same accent
```

- [ ] **Step 1: Write the failing test** — `OrbitTokensTest.kt`:
```kotlin
package com.udaytank.browse.ui.theme
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test
class OrbitTokensTest {
    @Test fun darkSurfacesAreDistinctLayers() {
        val s = darkOrbit.surfaces
        assertEquals(Color(0xFF08081C), s.base)
        assertEquals(Color(0xFF12122E), s.surface)
        assertEquals(Color(0xFF181840), s.elevated)
        // each layer must differ so nothing is flat-on-flat
        assertEquals(3, setOf(s.base, s.surface, s.elevated).size)
    }
    @Test fun accentGradientIsBrandBlue() {
        assertEquals(listOf(Color(0xFF1E4FD8), Color(0xFF35C3F3)), darkOrbit.accent.gradient)
    }
    @Test fun lightSchemeSharesAccentButInvertsText() {
        assertEquals(darkOrbit.accent.gradient, lightOrbit.accent.gradient)
        assert(lightOrbit.text.primary != darkOrbit.text.primary)
    }
}
```

- [ ] **Step 2: Run test to verify it fails** — Run: `.\gradlew.bat testDebugUnitTest --tests "*OrbitTokensTest*"` → FAIL (unresolved `darkOrbit`/`lightOrbit`).

- [ ] **Step 3: Implement the token module** — In `Color.kt` add the literal `Color(0xFF…)` constants above; in `Type.kt` define named text styles `orbitDisplay` (24sp/W800/-0.3sp), `orbitTitle` (17sp/W700), `orbitBody` (13sp/W400/lineHeight 20sp), `orbitCaption` (11sp/W400); in `Orbit.kt` define the objects/data classes/`LocalOrbit`/`orbit()`/`darkOrbit`/`lightOrbit` exactly as the interface block; in `Theme.kt` wrap the app content in `CompositionLocalProvider(LocalOrbit provides <dark|light per ThemeMode>)` and keep the existing `MaterialTheme` colors in sync with the scheme (map surface→M3 surface, accent.solid→primary, text.primary→onSurface). Motion specs: `structural = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)`, `quick = tween(120)`.

- [ ] **Step 4: Run tests** — Run: `.\gradlew.bat testDebugUnitTest --tests "*OrbitTokensTest*"` → PASS; then `.\gradlew.bat assembleDebug` → BUILD SUCCESSFUL (existing screens still compile; no visual change yet).

- [ ] **Step 5: Commit** — `git add app/src/main/java/com/udaytank/browse/ui/theme app/src/test/java/com/udaytank/browse/ui/theme && git commit -m "feat(v3.1): Orbit design token foundation"`

---

## Phase 2 — OmniBar (shrink-not-hide bottom bar)

### Task 2: BarScrollPolicy full/slim state (pure logic, TDD)

**Files:**
- Modify: `app/src/main/java/com/udaytank/browse/browser/BarScrollPolicy.kt`
- Test: `app/src/test/java/com/udaytank/browse/browser/BarScrollPolicyTest.kt` (extend existing)

**Interfaces produced:**
```kotlin
enum class BarState { Full, Slim }
class BarScrollPolicy(private val shrinkThresholdPx: Int, private val expandThresholdPx: Int) {
    fun onScroll(scrollY: Int, dy: Int): BarState   // Full near top / after cumulative up; Slim after cumulative down
    fun reset()                                     // back to Full (tab switch, nav start, edit, top)
    val state: BarState
}
```

- [ ] **Step 1: Write the failing tests** — replace the old hide/show expectations with full/slim:
```kotlin
@Test fun shrinksAfterCumulativeDownScroll() {
    val p = BarScrollPolicy(shrinkThresholdPx = 60, expandThresholdPx = 8)
    assertEquals(BarState.Full, p.onScroll(0, 0))
    assertEquals(BarState.Full, p.onScroll(40, 40))
    assertEquals(BarState.Slim, p.onScroll(120, 80)) // cumulative down > 60
}
@Test fun expandsOnSmallUpScroll() {
    val p = BarScrollPolicy(60, 8)
    p.onScroll(200, 200)                 // Slim
    assertEquals(BarState.Full, p.onScroll(188, -12))
}
@Test fun alwaysFullNearTop() {
    val p = BarScrollPolicy(60, 8)
    p.onScroll(300, 300)
    assertEquals(BarState.Full, p.onScroll(4, -296))
}
@Test fun directionFlipResetsAccumulation() {
    val p = BarScrollPolicy(60, 8)
    p.onScroll(50, 50); p.onScroll(45, -5) // flip
    assertEquals(BarState.Full, p.state)
}
```

- [ ] **Step 2: Run to verify fail** — `.\gradlew.bat testDebugUnitTest --tests "*BarScrollPolicyTest*"` → FAIL.

- [ ] **Step 3: Implement** — replace the boolean hide/show with `BarState`; keep the cumulative-with-hysteresis logic (accumulate `dy` per direction, flip resets the accumulator, `scrollY <= expandThresholdPx` forces `Full`, cumulative-down `>= shrinkThresholdPx` → `Slim`, cumulative-up `>= expandThresholdPx` → `Full`).

- [ ] **Step 4: Run tests** — `.\gradlew.bat testDebugUnitTest --tests "*BarScrollPolicyTest*"` → PASS.

- [ ] **Step 5: Commit** — `git commit -am "feat(v3.1): BarScrollPolicy full/slim shrink states"`

### Task 3: OmniBar composable + content-above-bar layout

**Files:**
- Create: `app/src/main/java/com/udaytank/browse/ui/components/OmniBar.kt`
- Modify: `app/src/main/java/com/udaytank/browse/ui/components/CommandBar.kt` (delegate/absorb into OmniBar, keep edit-mode + suggestions wiring)
- Modify: `app/src/main/java/com/udaytank/browse/ui/BrowserScreen.kt` (host OmniBar; lay web content in the space ABOVE it, not behind)
- Modify: `app/src/main/java/com/udaytank/browse/MainActivity.kt` (feed scrollY/dy → BarScrollPolicy → BarState; set shrink threshold = `(60*density).toInt()`, expand = `(8*density).toInt()`)

**Interfaces produced:**
```kotlin
@Composable fun OmniBar(
    state: BarState, editing: Boolean, url: String?, tabCount: Int,
    onBarTap: () -> Unit, onMenu: () -> Unit, onBack: () -> Unit,
    // …existing CommandBar params for edit mode + suggestions, restyled to Orbit tokens
)
```

**Behavior:** `Full` → 56dp floating rounded (radius `OrbitRadii.bar`, `orbit().surfaces.surface`, 12dp side/bottom insets) with back + address + tab-count + menu. `Slim` → animates (`OrbitMotion.structuralDp()`) to 30dp centered pill showing only a grab indicator; tapping it or `state==Full` restores. `editing` → full height, above IME, suggestions panel. Content-above-bar: `BrowserScreen` places the WebView/`TabWebView` in a column/box whose bottom padding equals the bar's occupied height at `Full` (so the page never draws under the bar); the slim state reveals extra page area. Fullscreen/reader/PiP hide the bar entirely (unchanged).

- [ ] **Step 1:** Create `OmniBar.kt` with `Full`/`Slim`/`editing` rendering per above, all dimensions/colors/motion from Orbit tokens; move the existing CommandBar edit + suggestions logic in (or have CommandBar delegate) so there is one bar implementation.
- [ ] **Step 2:** In `BrowserScreen.kt`, render the active tab's WebView inset above the bar (bottom padding = full-bar height + inset); remove any logic that let content sit behind the bar. Wire `barState` from the VM/MainActivity.
- [ ] **Step 3:** In `MainActivity.kt`, convert the existing `onPageScrolled` → `BarScrollPolicy.onScroll` → expose `barState` (StateFlow on the VM) and reset on tab switch / page start / edit focus / reader / find.
- [ ] **Step 4: Build** — `.\gradlew.bat assembleDebug` → SUCCESSFUL.
- [ ] **Step 5: Commit** — `git commit -am "feat(v3.1): OmniBar shrink bar with content laid out above it"`

### Task 4: Home uses the same OmniBar

**Files:**
- Modify: `app/src/main/java/com/udaytank/browse/ui/HomePage.kt`
- Modify: `app/src/main/java/com/udaytank/browse/ui/BrowserScreen.kt`

**Behavior:** Remove the separate centered home search pill; the home canvas sits above the same `OmniBar`. Tapping the bar on home enters edit mode exactly as on a web page (suggestions + clipboard chip unchanged). This is the change that removes the home→web "jump."

- [ ] **Step 1:** Delete the centered-pill path in `HomePage`; render the home canvas (logo/wordmark/shortcuts) in the content area above the shared bar.
- [ ] **Step 2:** Ensure edit-mode/suggestions/voice/clipboard still trigger from the bar on home.
- [ ] **Step 3: Build** — `assembleDebug` SUCCESSFUL.
- [ ] **Step 4: Commit** — `git commit -am "feat(v3.1): home shares the OmniBar (no centered-pill jump)"`

---

## Phase 3 — Home (Focused default + curated toggles)

### Task 5: Home preference keys (DataStore, TDD)

**Files:**
- Modify: `app/src/main/java/com/udaytank/browse/data/SettingsRepository.kt`
- Modify: `app/src/test/java/com/udaytank/browse/FakeSettingsRepository.kt`
- Test: `app/src/test/java/com/udaytank/browse/data/HomePrefsTest.kt` (or extend an existing settings test)

**Interfaces produced:**
```kotlin
enum class ShortcutDensity { FEW, MORE }
val showGreeting: Flow<Boolean>      // default false
val showHomeStats: Flow<Boolean>     // default false
val shortcutDensity: Flow<ShortcutDensity> // default FEW
val homeWallpaper: Flow<String>      // default "" (none); id of a bundled backdrop
suspend fun setShowGreeting(v: Boolean)
suspend fun setShowHomeStats(v: Boolean)
suspend fun setShortcutDensity(v: ShortcutDensity)
suspend fun setHomeWallpaper(v: String)
```

- [ ] **Step 1: Write failing test** — round-trip each pref through a fake/in-memory DataStore: defaults are `false/false/FEW/""`; setters update the flow; enum parse is null-safe (unknown → FEW).
- [ ] **Step 2: Run → FAIL.**
- [ ] **Step 3: Implement** — add keys following the existing `booleanPreferencesKey`/`stringPreferencesKey` pattern; extend `FakeSettingsRepository`.
- [ ] **Step 4: Run → PASS; assembleDebug SUCCESSFUL.**
- [ ] **Step 5: Commit** — `git commit -am "feat(v3.1): home customization prefs"`

### Task 6: Focused home + live toggle response

**Files:**
- Modify: `app/src/main/java/com/udaytank/browse/ui/HomePage.kt`
- Modify: `app/src/main/java/com/udaytank/browse/BrowserViewModel.kt` (expose the 4 prefs as StateFlows)

**Behavior:** Focused layout by default (logo + wordmark + one shortcut row). `showGreeting` → greeting line; `showHomeStats` (non-incognito only) → stats card; `shortcutDensity == MORE` → full grid instead of one row; `homeWallpaper` → subtle backdrop behind the canvas. All spacing/radii/type/surfaces from Orbit tokens. Incognito disclaimer + no-stats rule preserved.

- [ ] **Step 1:** VM: expose `showGreeting/showHomeStats/shortcutDensity/homeWallpaper` StateFlows.
- [ ] **Step 2:** HomePage: render Focused default; apply each toggle; token-ize all styling.
- [ ] **Step 3: Build** — assembleDebug SUCCESSFUL.
- [ ] **Step 4: Commit** — `git commit -am "feat(v3.1): Focused home honoring customization toggles"`

### Task 7: Settings → Home section

**Files:**
- Modify: `app/src/main/java/com/udaytank/browse/ui/SettingsScreen.kt`
- Modify: `app/src/main/java/com/udaytank/browse/BrowserViewModel.kt` (setter passthroughs)

**Behavior:** New "Home" section: switches for Show greeting, Show privacy stats; a two-option selector for Shortcut density (Few/More); a wallpaper picker (None + a couple bundled backdrops). Rows use the shared token spacing/type.

- [ ] **Step 1:** Add the Home section + wire setters.
- [ ] **Step 2: Build** — assembleDebug SUCCESSFUL.
- [ ] **Step 3: Commit** — `git commit -am "feat(v3.1): Settings Home customization section"`

---

## Phase 4 — Bottom-sheet menu

### Task 8: Menu as ModalBottomSheet

**Files:**
- Modify: `app/src/main/java/com/udaytank/browse/ui/BrowserScreen.kt` (replace the `DropdownMenu` with an M3 `ModalBottomSheet`)
- Create: `app/src/main/java/com/udaytank/browse/ui/components/BrowserMenuSheet.kt`

**Behavior:** A `ModalBottomSheet` (grab-handle, `orbit().surfaces.elevated`, top radius `OrbitRadii.bar`) with: an icon action row (back / forward / refresh / share / add-to-home) then the exact existing grouped items and ad-block footer, dividers on the spacing scale, badges (Downloads active count, Reading-list unread) preserved, incognito-conditional items preserved. Swipe-down/scrim-tap dismiss. Every existing `onClick` action is carried over unchanged — this task only changes the container and styling.

- [ ] **Step 1:** Build `BrowserMenuSheet.kt` reproducing all current menu entries + actions + badges + conditionals, styled to tokens.
- [ ] **Step 2:** In `BrowserScreen.kt`, swap the dropdown for the sheet (state hoisted; menu button opens it).
- [ ] **Step 3: Build** — assembleDebug SUCCESSFUL; confirm no menu action was dropped (diff the action list against the old dropdown).
- [ ] **Step 4: Commit** — `git commit -am "feat(v3.1): bottom-sheet browser menu"`

---

## Phase 5 — Consistency sweep

### Task 9: Shared list row + top-app-bar components

**Files:**
- Create: `app/src/main/java/com/udaytank/browse/ui/components/OrbitListRow.kt`
- Create: `app/src/main/java/com/udaytank/browse/ui/components/OrbitTopBar.kt`

**Interfaces produced:**
```kotlin
@Composable fun OrbitListRow(leadingIcon: ImageVector?, title: String, subtitle: String?, trailing: (@Composable () -> Unit)? = null, onClick: (() -> Unit)? = null)
@Composable fun OrbitTopBar(title: String, onBack: () -> Unit, actions: (@Composable RowScope.() -> Unit)? = null)
```
Unified height (56dp row / 56dp bar), padding (`OrbitSpacing.lg`), icon size (24dp), title (`orbitBody`/`orbitTitle`), subtitle (`orbitCaption`/muted), surfaces + ripple from tokens.

- [ ] **Step 1:** Create both components against Orbit tokens.
- [ ] **Step 2: Build** — assembleDebug SUCCESSFUL.
- [ ] **Step 3: Commit** — `git commit -am "feat(v3.1): shared OrbitListRow + OrbitTopBar"`

### Task 10: Apply shared components + tokens across screens

**Files (modify, one screen per commit):**
- `ui/TabSwitcherScreen.kt`, `ui/BookmarksScreen.kt`, `ui/HistoryScreen.kt`, `ui/DownloadsScreen.kt`, `ui/ReadingListScreen.kt`, `ui/ReaderOverlay.kt`, `ui/OnboardingScreen.kt`, `ui/IncognitoLockScreen.kt`

**Behavior:** Replace bespoke rows/app-bars/spacing with `OrbitListRow`/`OrbitTopBar` and token values; unify empty states; swipe/undo and screen transitions use `OrbitMotion`. No behavior changes — verify each screen still does exactly what it did.

- [ ] **Step 1:** TabSwitcher → tokens + card radius/surface + sheet context menu; build; commit `refactor(v3.1): tab switcher on Orbit tokens`.
- [ ] **Step 2:** Bookmarks/History/Downloads/ReadingList → `OrbitListRow`/`OrbitTopBar`/empty states; build; commit `refactor(v3.1): library screens on shared components`.
- [ ] **Step 3:** Reader controls + Onboarding + IncognitoLock → tokens; build; commit `refactor(v3.1): reader, onboarding, lock on Orbit tokens`.
- [ ] **Step 4:** Motion pass — ensure all sheets/dialogs/transitions reference `OrbitMotion`; build; commit `refactor(v3.1): unified motion curve`.

### Task 11: Final verification + ship

- [ ] **Step 1:** `.\gradlew.bat testDebugUnitTest` → all green; `.\gradlew.bat assembleDebug` → SUCCESSFUL.
- [ ] **Step 2:** Dispatch a whole-branch UI review (code-reviewer) for token consistency, dropped actions, and incognito rules; fix findings.
- [ ] **Step 3:** Device sweep on the emulator (cold-boot; force-stop launcher if it ANRs): bar shrinks on scroll & never covers the page; home↔web no jump; bottom-sheet menu; every library screen shares spacing/type/motion; light + dark both intentional; incognito home has no stats; 0 crashes in logcat.
- [ ] **Step 4:** Merge `--no-ff` to main, bump versionName `3.1`/versionCode `4`, build signed release, tag `v3.1`, push, update memory + README.

## Self-review notes

- **Spec coverage:** §2 tokens → Task 1; §3 toolbar → Tasks 2–4; §4 home → Tasks 5–7; §5 menu → Task 8; §6 consistency sweep → Tasks 9–10; §8 phasing mirrored 1:1; §10 acceptance → Task 11 Step 3.
- **No placeholders:** all tasks name exact files, interfaces, and behavior; logic tasks carry real test code.
- **Type consistency:** `BarState`, `OrbitScheme`/`orbit()`, `OmniBar`, `OrbitListRow`/`OrbitTopBar`, `ShortcutDensity`, and the four Home prefs are referenced with the same names across tasks.
