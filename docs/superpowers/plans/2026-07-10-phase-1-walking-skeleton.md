# Phase 1: Walking Skeleton Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A minimal but genuinely usable browser: address bar (URL or search), WebView rendering, back/forward/reload, and a loading progress bar.

**Architecture:** Single-activity MVVM. `BrowserViewModel` holds all UI state in one `StateFlow<BrowserUiState>`; the WebView lives inside an `AndroidView` composable and communicates with the ViewModel through callbacks (WebView → VM) and a consumable `pendingCommand` in state (VM → WebView). Input classification (URL vs search) is a pure Kotlin object, fully unit-tested.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), androidx ViewModel + StateFlow, Android System WebView, JUnit 4.

**Learning mode:** Steps marked **👤 OWNER WRITES** are written by Uday (with guidance), not generated. A reference implementation is included after each such step for verification — executor must pause and let the owner attempt first.

## Global Constraints

- Package/namespace: `com.udaytank.browse` (spec working name "Browse")
- minSdk 26, targetSdk/compileSdk 35, Kotlin + Compose via version catalog
- MVVM: no browser logic in composables; composables render state and forward events (spec §4)
- Never proceed silently past SSL errors (spec §6) — Phase 1 uses WebView default (load fails); custom warning screen is Phase 3
- Every task ends with tests passing and a git commit
- Shell commands below are PowerShell, run from the project root. Set Java once per terminal session:
  `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"`

---

### Task 1: Project Scaffold

**Files:**
- Create: entire Android project via Android Studio wizard (in `f:\Uday Tank\Projects\Real Projects\Code - Browse`)
- Create: `.gitignore` (replace wizard default with content below)

**Interfaces:**
- Produces: a building, runnable Compose app with package `com.udaytank.browse`; Gradle wrapper (`gradlew.bat`); version catalog at `gradle/libs.versions.toml`. All later tasks assume this project layout: code in `app/src/main/java/com/udaytank/browse/`, unit tests in `app/src/test/java/com/udaytank/browse/`.

- [ ] **Step 1: Create the project with the Android Studio wizard**

Open Android Studio → New Project → **Empty Activity** (the Compose one). Settings:
- Name: `Browse`
- Package name: `com.udaytank.browse`
- Save location: `f:\Uday Tank\Projects\Real Projects\Code - Browse`
- Minimum SDK: **API 26**
- Build configuration language: Kotlin DSL

Let the wizard finish and Gradle sync complete (first sync downloads dependencies; several minutes).

- [ ] **Step 2: Run the generated app on the emulator**

In Android Studio: select the `Medium_Phone` emulator, press Run ▶.
Expected: emulator boots and shows the template "Hello Android" screen. This proves the entire toolchain works before we write any code.

- [ ] **Step 3: Replace `.gitignore` at project root**

```gitignore
*.iml
.gradle/
local.properties
.idea/
.DS_Store
build/
app/build/
captures/
.externalNativeBuild/
.cxx/
*.apk
*.aab
```

- [ ] **Step 4: Add INTERNET permission**

In `app/src/main/AndroidManifest.xml`, add as the first child of `<manifest>` (above `<application>`):

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

- [ ] **Step 5: Verify a clean build from the command line**

Run: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat assembleDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit**

```powershell
git add -A
git commit -m "feat: scaffold Browse Android project (Compose, minSdk 26)"
```

---

### Task 2: URL-vs-Search Classifier (`UrlInput`)

The address bar's brain: given whatever the user typed, produce a loadable URL. Pure Kotlin — no Android — so it's fully unit-testable.

**Files:**
- Create: `app/src/main/java/com/udaytank/browse/browser/UrlInput.kt`
- Test: `app/src/test/java/com/udaytank/browse/browser/UrlInputTest.kt`

**Interfaces:**
- Produces: `object UrlInput { fun toLoadableUrl(input: String): String }` — Task 3's ViewModel calls exactly this.

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/java/com/udaytank/browse/browser/UrlInputTest.kt`:

```kotlin
package com.udaytank.browse.browser

import org.junit.Assert.assertEquals
import org.junit.Test

class UrlInputTest {

    @Test
    fun `full url with scheme is returned unchanged`() {
        assertEquals("https://bbc.com", UrlInput.toLoadableUrl("https://bbc.com"))
        assertEquals("http://example.org/page", UrlInput.toLoadableUrl("http://example.org/page"))
    }

    @Test
    fun `domain without scheme gets https prefix`() {
        assertEquals("https://bbc.com", UrlInput.toLoadableUrl("bbc.com"))
        assertEquals("https://en.wikipedia.org/wiki/Kotlin", UrlInput.toLoadableUrl("en.wikipedia.org/wiki/Kotlin"))
    }

    @Test
    fun `words with spaces become a search query`() {
        assertEquals(
            "https://www.google.com/search?q=best+pizza",
            UrlInput.toLoadableUrl("best pizza")
        )
    }

    @Test
    fun `single word without a dot is a search`() {
        assertEquals(
            "https://www.google.com/search?q=pizza",
            UrlInput.toLoadableUrl("pizza")
        )
    }

    @Test
    fun `surrounding whitespace is ignored`() {
        assertEquals("https://bbc.com", UrlInput.toLoadableUrl("  bbc.com  "))
    }

    @Test
    fun `special characters in searches are url-encoded`() {
        assertEquals(
            "https://www.google.com/search?q=what+is+2%2B2",
            UrlInput.toLoadableUrl("what is 2+2")
        )
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.udaytank.browse.browser.UrlInputTest"`
Expected: FAIL — `Unresolved reference: UrlInput` (compilation error counts as the failing state).

- [ ] **Step 3: 👤 OWNER WRITES — implement `toLoadableUrl`**

Create `app/src/main/java/com/udaytank/browse/browser/UrlInput.kt` with this skeleton, then the owner fills in the body of `toLoadableUrl` (and a helper if wanted):

```kotlin
package com.udaytank.browse.browser

import java.net.URLEncoder

object UrlInput {

    private const val SEARCH_URL = "https://www.google.com/search?q="

    /**
     * Turns raw address-bar input into a URL the WebView can load.
     * Decide: is it already a URL, a bare domain, or a search?
     */
    fun toLoadableUrl(input: String): String {
        // 👤 OWNER WRITES (~8 lines). The tests in UrlInputTest define
        // the required behavior. Hints:
        //  - String methods: trim(), startsWith(), contains()
        //  - URLEncoder.encode(text, "UTF-8") encodes a search query
        TODO()
    }
}
```

Guidance for the owner: think in three cases, most specific first. What marks something as *already* a URL? What distinguishes a domain (`bbc.com`) from a search (`best pizza`)? Rule of thumb used by real browsers: a space anywhere means search; no dot anywhere means search; otherwise treat as domain.

Reference implementation (executor: reveal only after the owner's attempt; use to verify, not replace):

```kotlin
fun toLoadableUrl(input: String): String {
    val text = input.trim()
    return when {
        text.startsWith("http://") || text.startsWith("https://") -> text
        !text.contains(' ') && text.contains('.') -> "https://$text"
        else -> SEARCH_URL + URLEncoder.encode(text, "UTF-8")
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.udaytank.browse.browser.UrlInputTest"`
Expected: PASS — 6 tests completed, 0 failed.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/com/udaytank/browse/browser/UrlInput.kt app/src/test/java/com/udaytank/browse/browser/UrlInputTest.kt
git commit -m "feat: classify address-bar input as URL or search"
```

---

### Task 3: Browser State & ViewModel

**Files:**
- Create: `app/src/main/java/com/udaytank/browse/browser/BrowserCommand.kt`
- Create: `app/src/main/java/com/udaytank/browse/BrowserViewModel.kt`
- Test: `app/src/test/java/com/udaytank/browse/BrowserViewModelTest.kt`

**Interfaces:**
- Consumes: `UrlInput.toLoadableUrl(input: String): String` (Task 2)
- Produces (used by Tasks 4–5):
  - `data class BrowserUiState(addressBarText: String, currentUrl: String?, isLoading: Boolean, progress: Int, canGoBack: Boolean, canGoForward: Boolean, pendingCommand: BrowserCommand?)`
  - `sealed interface BrowserCommand` with `LoadUrl(url: String)`, `GoBack`, `GoForward`, `Reload`
  - `class BrowserViewModel : ViewModel()` exposing `uiState: StateFlow<BrowserUiState>` and functions: `onAddressBarTextChanged(String)`, `onGoPressed()`, `onBackPressed()`, `onForwardPressed()`, `onReloadPressed()`, `onCommandConsumed()`, `onPageStarted(String)`, `onProgressChanged(Int)`, `onPageFinished()`, `onHistoryChanged(Boolean, Boolean)`

- [ ] **Step 1: Add ViewModel dependency**

In `gradle/libs.versions.toml`, add under `[versions]` (if not present):

```toml
lifecycleViewmodelCompose = "2.8.7"
```

and under `[libraries]`:

```toml
androidx-lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycleViewmodelCompose" }
```

In `app/build.gradle.kts` `dependencies { }` block, add:

```kotlin
implementation(libs.androidx.lifecycle.viewmodel.compose)
```

Run: `.\gradlew.bat assembleDebug` — Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Create the command type**

Create `app/src/main/java/com/udaytank/browse/browser/BrowserCommand.kt`:

```kotlin
package com.udaytank.browse.browser

/** One-shot instructions from the ViewModel to the WebView. */
sealed interface BrowserCommand {
    data class LoadUrl(val url: String) : BrowserCommand
    data object GoBack : BrowserCommand
    data object GoForward : BrowserCommand
    data object Reload : BrowserCommand
}
```

- [ ] **Step 3: Write the failing ViewModel tests**

Create `app/src/test/java/com/udaytank/browse/BrowserViewModelTest.kt`:

```kotlin
package com.udaytank.browse

import com.udaytank.browse.browser.BrowserCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class BrowserViewModelTest {

    @Test
    fun `typing updates address bar text`() {
        val vm = BrowserViewModel()
        vm.onAddressBarTextChanged("bbc.com")
        assertEquals("bbc.com", vm.uiState.value.addressBarText)
    }

    @Test
    fun `go pressed emits LoadUrl command with normalized url`() {
        val vm = BrowserViewModel()
        vm.onAddressBarTextChanged("bbc.com")
        vm.onGoPressed()
        assertEquals(
            BrowserCommand.LoadUrl("https://bbc.com"),
            vm.uiState.value.pendingCommand
        )
    }

    @Test
    fun `consuming a command clears it`() {
        val vm = BrowserViewModel()
        vm.onGoPressed()
        vm.onCommandConsumed()
        assertNull(vm.uiState.value.pendingCommand)
    }

    @Test
    fun `page start sets loading and syncs address bar`() {
        val vm = BrowserViewModel()
        vm.onPageStarted("https://bbc.com/news")
        assertTrue(vm.uiState.value.isLoading)
        assertEquals("https://bbc.com/news", vm.uiState.value.addressBarText)
        assertEquals("https://bbc.com/news", vm.uiState.value.currentUrl)
    }

    @Test
    fun `page finish clears loading`() {
        val vm = BrowserViewModel()
        vm.onPageStarted("https://bbc.com")
        vm.onPageFinished()
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test
    fun `history change updates nav button state`() {
        val vm = BrowserViewModel()
        vm.onHistoryChanged(canGoBack = true, canGoForward = false)
        assertTrue(vm.uiState.value.canGoBack)
        assertFalse(vm.uiState.value.canGoForward)
    }
}
```

- [ ] **Step 4: Run tests to verify they fail**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.udaytank.browse.BrowserViewModelTest"`
Expected: FAIL — `Unresolved reference: BrowserViewModel`.

- [ ] **Step 5: Implement the ViewModel**

Create `app/src/main/java/com/udaytank/browse/BrowserViewModel.kt`:

```kotlin
package com.udaytank.browse

import androidx.lifecycle.ViewModel
import com.udaytank.browse.browser.BrowserCommand
import com.udaytank.browse.browser.UrlInput
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class BrowserUiState(
    val addressBarText: String = "",
    val currentUrl: String? = null,
    val isLoading: Boolean = false,
    val progress: Int = 0,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val pendingCommand: BrowserCommand? = null,
)

class BrowserViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(
        BrowserUiState(pendingCommand = BrowserCommand.LoadUrl(HOME_URL))
    )
    val uiState: StateFlow<BrowserUiState> = _uiState.asStateFlow()

    // --- events from the UI ---

    fun onAddressBarTextChanged(text: String) =
        _uiState.update { it.copy(addressBarText = text) }

    fun onGoPressed() = _uiState.update {
        it.copy(pendingCommand = BrowserCommand.LoadUrl(UrlInput.toLoadableUrl(it.addressBarText)))
    }

    fun onBackPressed() = _uiState.update { it.copy(pendingCommand = BrowserCommand.GoBack) }
    fun onForwardPressed() = _uiState.update { it.copy(pendingCommand = BrowserCommand.GoForward) }
    fun onReloadPressed() = _uiState.update { it.copy(pendingCommand = BrowserCommand.Reload) }

    fun onCommandConsumed() = _uiState.update { it.copy(pendingCommand = null) }

    // --- callbacks from the WebView ---

    fun onPageStarted(url: String) = _uiState.update {
        it.copy(currentUrl = url, addressBarText = url, isLoading = true, progress = 0)
    }

    fun onProgressChanged(percent: Int) = _uiState.update { it.copy(progress = percent) }

    fun onPageFinished() = _uiState.update { it.copy(isLoading = false) }

    fun onHistoryChanged(canGoBack: Boolean, canGoForward: Boolean) =
        _uiState.update { it.copy(canGoBack = canGoBack, canGoForward = canGoForward) }

    companion object {
        const val HOME_URL = "https://www.google.com"
    }
}
```

Note the constructor pre-loads `HOME_URL` as the first command — that's why a fresh app shows a page instead of a blank screen. `go pressed emits LoadUrl` test still passes because `onGoPressed` overwrites the pending command.

- [ ] **Step 6: Run tests to verify they pass**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.udaytank.browse.BrowserViewModelTest"`
Expected: PASS — 6 tests completed, 0 failed. Then run all tests: `.\gradlew.bat testDebugUnitTest` — Expected: all pass (UrlInputTest + BrowserViewModelTest).

- [ ] **Step 7: Commit**

```powershell
git add -A
git commit -m "feat: browser state, commands, and BrowserViewModel"
```

---

### Task 4: WebView Container

The bridge between the Compose world and the classic Android WebView.

**Files:**
- Create: `app/src/main/java/com/udaytank/browse/ui/WebViewContainer.kt`

**Interfaces:**
- Consumes: `BrowserUiState`, `BrowserCommand` (Task 3)
- Produces (used by Task 5):
  ```kotlin
  @Composable
  fun WebViewContainer(
      pendingCommand: BrowserCommand?,
      onCommandConsumed: () -> Unit,
      onPageStarted: (String) -> Unit,
      onProgressChanged: (Int) -> Unit,
      onPageFinished: () -> Unit,
      onHistoryChanged: (canGoBack: Boolean, canGoForward: Boolean) -> Unit,
      modifier: Modifier = Modifier,
  )
  ```

- [ ] **Step 1: Implement the container**

Create `app/src/main/java/com/udaytank/browse/ui/WebViewContainer.kt`:

```kotlin
package com.udaytank.browse.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.udaytank.browse.browser.BrowserCommand

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewContainer(
    pendingCommand: BrowserCommand?,
    onCommandConsumed: () -> Unit,
    onPageStarted: (String) -> Unit,
    onProgressChanged: (Int) -> Unit,
    onPageFinished: () -> Unit,
    onHistoryChanged: (canGoBack: Boolean, canGoForward: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true

                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                        onPageStarted(url)
                    }

                    override fun onPageFinished(view: WebView, url: String) {
                        onPageFinished()
                    }

                    override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
                        onHistoryChanged(view.canGoBack(), view.canGoForward())
                    }
                }

                webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView, newProgress: Int) {
                        onProgressChanged(newProgress)
                    }
                }
            }
        },
        update = { webView ->
            when (pendingCommand) {
                is BrowserCommand.LoadUrl -> webView.loadUrl(pendingCommand.url)
                BrowserCommand.GoBack -> if (webView.canGoBack()) webView.goBack()
                BrowserCommand.GoForward -> if (webView.canGoForward()) webView.goForward()
                BrowserCommand.Reload -> webView.reload()
                null -> Unit
            }
            if (pendingCommand != null) onCommandConsumed()
        },
    )
}
```

How this works: Compose calls `factory` once (WebView created), then `update` every time state changes. We execute at most one pending command per update and immediately mark it consumed, which sets `pendingCommand` back to null in state.

- [ ] **Step 2: Verify it compiles**

Run: `.\gradlew.bat assembleDebug`
Expected: `BUILD SUCCESSFUL` (behavior is verified end-to-end in Task 5 — this component has no UI entry point yet).

- [ ] **Step 3: Commit**

```powershell
git add app/src/main/java/com/udaytank/browse/ui/WebViewContainer.kt
git commit -m "feat: WebView container bridging Compose and WebView"
```

---

### Task 5: Browser Screen UI

Assemble the full screen: address bar + progress bar + WebView + navigation, and wire the system back button.

**Files:**
- Create: `app/src/main/java/com/udaytank/browse/ui/BrowserScreen.kt`
- Modify: `app/src/main/java/com/udaytank/browse/MainActivity.kt` (replace wizard template body)

**Interfaces:**
- Consumes: `BrowserViewModel` (Task 3), `WebViewContainer` (Task 4)
- Produces: `@Composable fun BrowserScreen(viewModel: BrowserViewModel)` — the app's single screen.

- [ ] **Step 1: Implement the screen**

Create `app/src/main/java/com/udaytank/browse/ui/BrowserScreen.kt`:

```kotlin
package com.udaytank.browse.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.udaytank.browse.BrowserViewModel

@Composable
fun BrowserScreen(viewModel: BrowserViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val keyboard = LocalSoftwareKeyboardController.current

    // System back button navigates page history before exiting the app.
    BackHandler(enabled = state.canGoBack) { viewModel.onBackPressed() }

    Scaffold(
        topBar = {
            Column {
                OutlinedTextField(
                    value = state.addressBarText,
                    onValueChange = viewModel::onAddressBarTextChanged,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    singleLine = true,
                    placeholder = { androidx.compose.material3.Text("Search or type URL") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = {
                        viewModel.onGoPressed()
                        keyboard?.hide()
                    }),
                )
                if (state.isLoading) {
                    LinearProgressIndicator(
                        progress = { state.progress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        bottomBar = {
            Row(modifier = Modifier.fillMaxWidth().imePadding()) {
                IconButton(onClick = viewModel::onBackPressed, enabled = state.canGoBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                IconButton(onClick = viewModel::onForwardPressed, enabled = state.canGoForward) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Forward")
                }
                IconButton(onClick = viewModel::onReloadPressed) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Reload")
                }
            }
        },
    ) { innerPadding ->
        WebViewContainer(
            pendingCommand = state.pendingCommand,
            onCommandConsumed = viewModel::onCommandConsumed,
            onPageStarted = viewModel::onPageStarted,
            onProgressChanged = viewModel::onProgressChanged,
            onPageFinished = viewModel::onPageFinished,
            onHistoryChanged = viewModel::onHistoryChanged,
            modifier = Modifier.fillMaxSize().padding(innerPadding),
        )
    }
}
```

- [ ] **Step 2: Wire it into MainActivity**

Replace the body of `app/src/main/java/com/udaytank/browse/MainActivity.kt` (keep the wizard's theme wrapper name if it differs — the wizard generates `BrowseTheme` in `ui/theme/`):

```kotlin
package com.udaytank.browse

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.udaytank.browse.ui.BrowserScreen
import com.udaytank.browse.ui.theme.BrowseTheme

class MainActivity : ComponentActivity() {

    private val viewModel: BrowserViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BrowseTheme {
                BrowserScreen(viewModel)
            }
        }
    }
}
```

If `collectAsStateWithLifecycle` is unresolved, add to `app/build.gradle.kts` dependencies:

```kotlin
implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
```

If `Icons.AutoMirrored` is unresolved, add:

```kotlin
implementation("androidx.compose.material:material-icons-extended")
```

- [ ] **Step 3: Build and run**

Run: `.\gradlew.bat assembleDebug` — Expected: `BUILD SUCCESSFUL`.
Then Run ▶ in Android Studio on the emulator.
Expected: app opens showing Google homepage, address bar on top, back/forward/reload at the bottom.

- [ ] **Step 4: Commit**

```powershell
git add -A
git commit -m "feat: browser screen with address bar, progress, and navigation"
```

---

### Task 6: Phase 1 Acceptance Test & Tag

**Files:** none created — this is the phase's definition-of-done gate (spec §8: "can browse any site usably").

- [ ] **Step 1: Run the full unit test suite**

Run: `.\gradlew.bat testDebugUnitTest`
Expected: all tests pass.

- [ ] **Step 2: Manual acceptance checklist (on the emulator)**

Every item must pass; fix and re-run if any fails:

1. Type `bbc.com` → Go: page loads, address bar shows the full `https://` URL.
2. Type `best pizza near me` → Go: Google search results appear.
3. Tap a link on any page: it opens *inside* the app (not in Chrome).
4. Back button (toolbar) returns to the previous page; Forward returns again.
5. System back gesture/button: navigates page history first; exits app only when history is empty.
6. Progress bar appears during load, disappears when done.
7. Rotate the emulator (Ctrl+Left/Right arrows): app doesn't crash. (Known Phase 1 limitation: the page may reload and history may reset on rotation — WebView state preservation across configuration changes is deferred to Phase 2's tab architecture. Note it, don't fix it now.)
8. Type text with special characters (`what is 2+2`) → Go: search works, query intact.

- [ ] **Step 3: Tag the release**

```powershell
git tag -a phase-1 -m "Phase 1: walking skeleton - usable browser"
git log --oneline
```

Expected: clean history of feat commits, tag `phase-1` present.
