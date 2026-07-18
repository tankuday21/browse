package com.udaytank.browse

import android.app.PictureInPictureParams
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.util.Rational
import android.view.View
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import com.udaytank.browse.ui.theme.OrbitMotion
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.udaytank.browse.browser.FileChooserCoordinator
import com.udaytank.browse.browser.FileUploads
import com.udaytank.browse.browser.UrlHosts
import kotlinx.coroutines.launch
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.udaytank.browse.data.ThemeMode
import com.udaytank.browse.ui.SettingsScreen
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.udaytank.browse.ui.BookmarksScreen
import com.udaytank.browse.ui.BrowserScreen
import com.udaytank.browse.ui.DownloadsScreen
import com.udaytank.browse.ui.HistoryScreen
import com.udaytank.browse.ui.PasswordsScreen
import com.udaytank.browse.ui.IncognitoLockScreen
import com.udaytank.browse.ui.OnboardingScreen
import com.udaytank.browse.ui.ReadingListScreen
import com.udaytank.browse.ui.TabSwitcherScreen
import com.udaytank.browse.ui.WebViewHolder
import com.udaytank.browse.ui.theme.BrowseTheme

class MainActivity : FragmentActivity() {

    private companion object {
        /** Intent extra carried by the static launcher shortcuts (res/xml/shortcuts.xml). */
        const val SHORTCUT_EXTRA = "andromeda.shortcut"
    }

    private val viewModel: BrowserViewModel by viewModels { BrowserViewModel.Factory }

    /** Set once the composable creates its [WebViewHolder]; used from onStop/onStart, which run
     *  outside the compose tree. */
    private var webViewHolder: WebViewHolder? = null

    /** Non-null while an HTML5 video (or other WebChromeClient custom view) is fullscreen.
     *  Activity-scoped (not per-composition) state so the Compose tree, [onUserLeaveHint] and
     *  [onPictureInPictureModeChanged] all see the same value. */
    private var fullscreenVideoView by mutableStateOf<View?>(null)

    /**
     * Exactly-once lifecycle of the WebView's pending `<input type="file">` callback (v4.8) —
     * the state machine lives in [FileChooserCoordinator] (JVM unit-tested); this class only
     * supplies the platform pieces (intent, launcher, MimeTypeMap).
     */
    private val fileChooser = FileChooserCoordinator<Array<Uri>>()

    /** System document picker for file uploads (v4.8). Field-registered — required pre-RESUMED. */
    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        val clip = data?.clipData?.let { c -> List(c.itemCount) { c.getItemAt(it).uri } } ?: emptyList()
        val uris = FileUploads.parseChooserResult(
            ok = result.resultCode == RESULT_OK,
            single = data?.data,
            clip = clip,
        )
        fileChooser.finish(uris?.toTypedArray())
    }

    /**
     * Opens the system picker for a page's file input. Always consumes [filePathCallback]
     * (returns true): the coordinator supersedes any stale pending chooser (resolved null) and
     * resolves null on launch failure rather than leaving the input stuck.
     */
    private fun showFileChooser(
        filePathCallback: ValueCallback<Array<Uri>>,
        params: WebChromeClient.FileChooserParams,
    ): Boolean {
        val mimeTypes = FileUploads.normalizeAcceptTypes(params.acceptTypes.orEmpty().toList()) { ext ->
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
        }
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            // A single concrete type filters directly; several go via EXTRA_MIME_TYPES over */*.
            type = mimeTypes.singleOrNull() ?: "*/*"
            if (mimeTypes.size > 1) putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes.toTypedArray())
            // MODE_SAVE / MODE_OPEN_FOLDER: WebView never emits these; they fall through to
            // a plain single-select picker.
            if (params.mode == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE) {
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            }
        }
        val title = params.title?.toString()?.takeIf { it.isNotBlank() } ?: "Choose a file"
        fileChooser.begin(callback = { filePathCallback.onReceiveValue(it) }) {
            try {
                fileChooserLauncher.launch(Intent.createChooser(intent, title))
                true
            } catch (_: ActivityNotFoundException) {
                false
            }
        }
        return true
    }

    /**
     * Video went fullscreen while the user is leaving the app (e.g. Home button, recents) -
     * enter Picture-in-Picture instead of just pausing/backgrounding the WebView.
     */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (fullscreenVideoView != null) {
            enterPictureInPictureMode(
                PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build()
            )
        }
    }

    /**
     * When PiP closes because the user dismissed the PiP window (swiped it away) rather than
     * tapping back into the app, the activity won't be RESUMED again on its own - treat that as
     * "the user is done with this video" and drop out of fullscreen. If they instead tapped the
     * PiP window to expand back to the full app, the activity is resumed and we leave the video
     * playing fullscreen as before.
     */
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (!isInPictureInPictureMode && !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            webViewHolder?.exitFullscreen()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleWebIntent(intent)
        handleShortcutIntent(intent)
    }

    override fun onStop() {
        super.onStop()
        // Re-lock incognito whenever the app leaves the foreground.
        if (viewModel.lockIncognito.value && viewModel.tabs.value.any { it.isIncognito }) {
            viewModel.onIncognitoLocked()
        }
        // Re-lock the Passwords screen too (v5.1) — one auth lasts one foreground session.
        viewModel.onPasswordsLocked()
        // Marks the opted-in tab keep-alive (and starts the media session) BEFORE pausing the
        // rest, so the tab we want to keep playing is already exempt when the pause sweep runs.
        maybeKeepBackgroundMediaAlive()
        webViewHolder?.pauseAllExceptKeptAlive()
    }

    override fun onStart() {
        super.onStart()
        webViewHolder?.resumeAll()
        com.udaytank.browse.media.MediaHoldService.stop(this)
        // NOTE: do NOT clear the keep-alive flag here. It is owned by the proactive
        // setBackgroundPlaybackTab effect (keyed on the toggle + active tab), so it must survive
        // foreground/background cycles - clearing it on foreground would leave the NEXT lock
        // unprotected (the effect won't re-run when nothing changed). On foreground the tab is
        // VISIBLE anyway, so the flag is inert until the next background transition.
        // Foreground again: the notification's controls take over, so drop the JS-monitor bridge.
        webViewHolder?.mediaStateListener = null
        webViewHolder?.mediaEndedListener = null
    }

    /**
     * Experimental background media playback (per-site opt-in, G3). When the opted-in, non-incognito
     * foreground tab is actually playing, this marks it keep-alive (so [WebViewHolder.pauseAllExceptKeptAlive]
     * leaves it running), starts the foreground [MediaHoldService] with its real [BrowserMediaSession]
     * for lock-screen controls, injects the JS media monitor for state reporting + auto-advance, and
     * bridges the transport buttons to [MediaControl] JS commands on that tab.
     *
     * Honest limits: this only gives the OS a foreground-service reason to keep the process around
     * and a visible control surface — an OEM battery manager (or the OS under memory pressure) can
     * still kill the process regardless.
     */
    private fun maybeKeepBackgroundMediaAlive() {
        if (!viewModel.backgroundMedia.value) return
        val tabId = viewModel.activeTabId.value ?: return
        // Never let an incognito tab trigger the service/notification/monitor, regardless of
        // allowlist membership - the host/title must never surface outside the private session.
        if (viewModel.tabs.value.find { it.id == tabId }?.isIncognito == true) return
        val url = viewModel.uiState.value.currentUrl ?: return
        if (!(url.startsWith("http://") || url.startsWith("https://"))) return
        val host = com.udaytank.browse.browser.UrlHosts.of(url) ?: return
        // The keep-alive flag itself is armed proactively (setBackgroundPlaybackTab) so playback
        // survives the lock regardless; this method only decides whether to also raise the
        // foreground media notification, which is pointless unless audio is actually playing.
        val audioManager = getSystemService(AUDIO_SERVICE) as android.media.AudioManager
        if (!audioManager.isMusicActive) return
        val holder = webViewHolder ?: return

        holder.setKeepAlive(tabId, true)
        val appContext = applicationContext
        com.udaytank.browse.media.MediaHoldService.controller = {
            holder.runMediaCommand(tabId, com.udaytank.browse.browser.MediaControl.PLAY_PAUSE)
        }
        com.udaytank.browse.media.MediaHoldService.onNext = {
            holder.runMediaCommand(tabId, com.udaytank.browse.browser.MediaControl.NEXT)
        }
        com.udaytank.browse.media.MediaHoldService.onPrevious = {
            holder.runMediaCommand(tabId, com.udaytank.browse.browser.MediaControl.PREVIOUS)
        }
        com.udaytank.browse.media.MediaHoldService.onStopped = {
            // Don't clear keep-alive here: this fires on every routine foreground return (onStart
            // stops the service), and the proactive effect owns the flag. Just drop the bridge.
            holder.mediaStateListener = null
            holder.mediaEndedListener = null
        }
        com.udaytank.browse.media.MediaHoldService.onSeek = { positionMs ->
            holder.runMediaCommand(tabId, com.udaytank.browse.browser.MediaControl.seekTo(positionMs))
        }
        // Page monitor -> session/notification. Bridge callbacks arrive on a WebView binder
        // thread; startService is thread-safe, so forward straight through.
        holder.mediaStateListener = { title, playing, positionMs, durationMs ->
            com.udaytank.browse.media.MediaHoldService.updateState(appContext, title, playing, positionMs, durationMs)
        }
        holder.mediaEndedListener = {
            // The monitor's own `ended` handler clicks "next"; native side just refreshes state.
        }
        holder.startMediaMonitor(tabId)

        val title = viewModel.tabs.value.find { it.id == tabId }?.title.orEmpty()
        com.udaytank.browse.media.MediaHoldService.start(this, tabId, host, title)
    }

    fun promptBiometricUnlock() =
        promptUnlock("Unlock incognito", "Verify it's you to view private tabs") {
            viewModel.onIncognitoUnlocked()
        }

    /** v5.1 Passwords gate: same device auth, different copy + unlock target. */
    fun promptPasswordsUnlock() =
        promptUnlock("Unlock passwords", "Verify it's you to view saved passwords") {
            viewModel.onPasswordsUnlocked()
        }

    /**
     * Shared biometric/device-credential prompt (v5.1 — extracted from the incognito lock).
     * Failure or cancel is deliberately a no-op: the gate simply stays up and the LockGate
     * button is the retry path.
     */
    private fun promptUnlock(title: String, subtitle: String, onSuccess: () -> Unit) {
        val executor = androidx.core.content.ContextCompat.getMainExecutor(this)
        val prompt = androidx.biometric.BiometricPrompt(
            this,
            executor,
            object : androidx.biometric.BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(
                    result: androidx.biometric.BiometricPrompt.AuthenticationResult,
                ) {
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    // Cancel/lockout/no-hardware: keep the gate up; the button retries.
                }
            },
        )
        val info = androidx.biometric.BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            // WEAK (not STRONG) + DEVICE_CREDENTIAL is deliberate: this is an app-level gate —
            // the Keystore credential key is not auth-bound — and Chrome makes the same call.
            // DEVICE_CREDENTIAL also forbids setNegativeButtonText (the PIN path replaces it).
            .setAllowedAuthenticators(
                androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK or
                    androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()
        prompt.authenticate(info)
    }

    /**
     * No-lockout rule (v5.1), FAIL-CLOSED: the gate is skipped only when the device provably
     * has no screen lock enrolled (auth can never succeed, and users must not lose their own
     * passwords — Chrome behaves the same). Deliberately NOT BiometricManager.canAuthenticate:
     * that returns non-SUCCESS codes (STATUS_UNKNOWN on API 28-29, HW_UNAVAILABLE, …) on
     * devices that DO have a PIN — where skipping would silently disable the gate even though
     * the DEVICE_CREDENTIAL prompt would work fine via the keyguard.
     */
    fun hasDeviceLock(): Boolean =
        getSystemService(android.app.KeyguardManager::class.java)?.isDeviceSecure == true

    /**
     * Hands the active tab's page to the system print service (H5) — its dialog includes
     * Save-as-PDF. The adapter is null when no live WebView exists (home page), which the menu
     * already disables for; the toast covers any race where the page vanished in between.
     */
    /**
     * Black Hole: relaunch the app in a fresh task and kill this process, so a clean cold start
     * rebuilds all in-memory state (a pristine default Orbit + one home tab) with no half-torn-
     * down WebViews or stale tab state lingering.
     */
    private fun restartProcess() {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
            ?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK) }
        if (intent != null) startActivity(intent)
        finishAffinity()
        Runtime.getRuntime().exit(0)
    }

    private fun printCurrentPage(holder: WebViewHolder) {
        val tabId = viewModel.activeTabId.value
        val adapter = tabId?.let { holder.printAdapter(it) }
        if (adapter == null) {
            android.widget.Toast.makeText(this, "Nothing to print on this page", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        val title = viewModel.tabs.value.find { it.id == tabId }?.title?.takeIf { it.isNotBlank() }
            ?: viewModel.currentHost()
            ?: "page"
        val printManager = getSystemService(PRINT_SERVICE) as android.print.PrintManager
        printManager.print("Andromeda - $title", adapter, android.print.PrintAttributes.Builder().build())
    }

    private fun handleWebIntent(intent: Intent?) {
        val url = intent?.takeIf { it.action == Intent.ACTION_VIEW }?.dataString ?: return
        if (url.startsWith("http://") || url.startsWith("https://")) {
            viewModel.onExternalUrl(url)
        }
    }

    /** Static launcher shortcuts (J4): the shortcuts.xml intents carry an extra, no data uri. */
    private fun handleShortcutIntent(intent: Intent?) {
        when (intent?.getStringExtra(SHORTCUT_EXTRA)) {
            "new_tab" -> viewModel.onNewTab()
            "new_incognito" -> viewModel.onNewIncognitoTab()
        }
    }

    /**
     * The default-browser ask (J2 page 3 and Settings share this): the system RoleManager
     * sheet where available, otherwise the default-apps settings screen.
     */
    fun requestDefaultBrowserRole() {
        val intent = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(android.app.role.RoleManager::class.java)
            if (roleManager != null && roleManager.isRoleAvailable(android.app.role.RoleManager.ROLE_BROWSER) &&
                !roleManager.isRoleHeld(android.app.role.RoleManager.ROLE_BROWSER)
            ) {
                roleManager.createRequestRoleIntent(android.app.role.RoleManager.ROLE_BROWSER)
            } else {
                Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
            }
        } else {
            Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
        }
        runCatching { startActivity(intent) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // OmniBar shrink hysteresis: convert the dp thresholds into real pixels once.
        val barDensity = resources.displayMetrics.density
        viewModel.setBarScrollThresholds(
            shrinkPx = (60 * barDensity).toInt(),
            expandPx = (8 * barDensity).toInt(),
        )
        handleWebIntent(intent)
        // Only a fresh launch acts on a shortcut extra — a recreation (process death restore)
        // still carries the old intent and must not open yet another tab.
        if (savedInstanceState == null) handleShortcutIntent(intent)
        setContent {
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            BrowseTheme(
                darkTheme = when (themeMode) {
                    ThemeMode.SYSTEM -> isSystemInDarkTheme()
                    ThemeMode.LIGHT -> false
                    ThemeMode.DARK -> true
                }
            ) {
                // First-run gate (J2): render onboarding INSTEAD of the browser tree, and a
                // plain background frame while the flag is still loading, so the real UI never
                // flashes underneath. Any onboarding exit path flips the flag permanently.
                val onboardingDone by viewModel.onboardingDone.collectAsStateWithLifecycle()
                if (onboardingDone != true) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background),
                    ) {
                        if (onboardingDone == false) {
                            OnboardingScreen(
                                onImportBookmarks = viewModel::importBookmarksHtml,
                                onSetDefaultBrowser = ::requestDefaultBrowserRole,
                                onDone = viewModel::onOnboardingFinished,
                            )
                        }
                    }
                    return@BrowseTheme
                }

                val navController = rememberNavController()
                val holderRef = remember { arrayOfNulls<WebViewHolder>(1) }
                val holder = remember {
                    WebViewHolder(
                        this,
                        adBlock = (application as BrowseApplication).adBlockEngine,
                        annoyance = (application as BrowseApplication).annoyanceEngine,
                        listener = object : WebViewHolder.Listener {
                        override fun onPageStarted(tabId: Long, url: String) {
                            viewModel.onPageStarted(tabId, url)
                            // v4.0: re-apply this host's zapped elements as the page starts (CSS
                            // hides matching nodes whenever they appear, so no flash).
                            UrlHosts.of(url)?.let { host ->
                                this@MainActivity.lifecycleScope.launch {
                                    val sels = viewModel.zapSelectorsForHost(host)
                                    if (sels.isNotEmpty()) holderRef[0]?.applyZaps(tabId, sels)
                                }
                            }
                        }

                        override fun onProgressChanged(tabId: Long, percent: Int) =
                            viewModel.onProgressChanged(tabId, percent)

                        override fun onPageFinished(tabId: Long, url: String, title: String?) {
                            viewModel.onPageFinished(tabId, url, title)
                            holderRef[0]?.captureThumbnail(tabId)
                        }

                        override fun onHistoryChanged(tabId: Long, canGoBack: Boolean, canGoForward: Boolean) =
                            viewModel.onHistoryChanged(tabId, canGoBack, canGoForward)

                        override fun onSslError(tabId: Long, url: String) =
                            viewModel.onSslError(tabId, url)

                        override fun onSafeBrowsingHit(tabId: Long, url: String, threatLabel: String) =
                            viewModel.onSafeBrowsingHit(tabId, url, threatLabel)

                        override fun onRequestBlocked(tabId: Long) =
                            viewModel.onRequestBlocked(tabId)

                        override fun onLongPress(tabId: Long, url: String, isImage: Boolean) =
                            viewModel.onLongPress(tabId, url, isImage)

                        override fun onDownloadStarted(downloadId: Long, fileName: String, url: String) =
                            viewModel.onDownloadStarted(downloadId, fileName, url)

                        override fun onDownloadRequested(
                            url: String,
                            fileName: String,
                            mimeType: String?,
                            userAgent: String?,
                        ) = viewModel.onDownloadRequested(url, fileName, mimeType, userAgent)

                        override fun onPageError(tabId: Long, description: String) =
                            viewModel.onPageError(tabId, description)

                        override fun onFindResult(tabId: Long, ordinal: Int, total: Int) =
                            viewModel.onFindResult(tabId, ordinal, total)

                        override fun onPermissionRequest(request: com.udaytank.browse.ui.PermissionRequestInfo) =
                            viewModel.onPermissionRequested(request)

                        override fun onTitleUpdated(tabId: Long, url: String, title: String) =
                            viewModel.onTitleUpdated(tabId, url, title)

                        override fun onFullscreenVideo(view: View?) {
                            fullscreenVideoView = view
                        }

                        override fun onPageScrolled(tabId: Long, scrollY: Int, dy: Int) =
                            viewModel.onPageScrolled(tabId, scrollY, dy)

                        override fun onZapPicked(tabId: Long, host: String, selector: String, label: String) =
                            viewModel.onZapPicked(tabId, host, selector, label)

                        override fun onLoginSubmitted(tabId: Long, host: String, username: String, password: String) =
                            viewModel.onLoginSubmitted(tabId, host, username, password)

                        override fun onTouchIconUrl(host: String, url: String) =
                            viewModel.onTouchIconUrl(host, url)

                        override fun onFaviconBitmap(host: String, bitmap: android.graphics.Bitmap) =
                            viewModel.onFaviconBitmap(host, bitmap)

                        override fun onShowFileChooser(
                            filePathCallback: ValueCallback<Array<Uri>>,
                            params: WebChromeClient.FileChooserParams,
                        ): Boolean = showFileChooser(filePathCallback, params)

                        override fun onCreateWindow(parentTabId: Long, url: String) =
                            viewModel.onPopupWindow(parentTabId, url)
                    },
                    ).also { holderRef[0] = it; webViewHolder = it }
                }
                DisposableEffect(Unit) {
                    onDispose { holder.destroyAll() }
                }

                // Orbit deletion (v4.2): the VM only closes its tabs' StateFlow/DB rows before
                // emitting — it never touches the native WebViews. Destroy those here FIRST
                // (holder.close per tabId) so no live WebView is still using the profile, THEN
                // delete the profile; ProfileStore.deleteProfile silently fails otherwise.
                LaunchedEffect(Unit) {
                    viewModel.orbitProfileToDelete.collect { deletion ->
                        deletion.tabIds.forEach { tabId -> holder.close(tabId) }
                        holder.deleteProfile(deletion.profileKey)
                    }
                }

                // v4.7 Passwords: the user chose a saved login to fill; inject it into the tab's
                // page. The password only reaches the WebView on this explicit user action.
                LaunchedEffect(Unit) {
                    viewModel.fillCredentialRequest.collect { fill ->
                        holder.fillCredentials(fill.tabId, fill.username, fill.password)
                    }
                }

                // Black Hole (v4.5): the VM has already wiped all data + files by the time it
                // emits. Finish the native side — destroy every WebView, delete every profile
                // (cookies/DOM storage), clear cached storage + thumbnails — then restart the
                // process so a cold start rebuilds a pristine default Orbit + one home tab.
                LaunchedEffect(Unit) {
                    viewModel.blackHoleReady.collect { profileKeys ->
                        // clearBrowsingData() clears each live WebView's HTTP cache, so it must run
                        // BEFORE destroyAll() empties the WebView map (else the cache clear no-ops).
                        holder.clearBrowsingData()
                        holder.destroyAll()
                        profileKeys.forEach { holder.deleteProfile(it) }
                        holder.clearThumbnails()
                        restartProcess()
                    }
                }

                // Fullscreen custom views are only honored for the tab the user is looking at;
                // the holder reads this in onShowCustomView (P6 improve pass).
                val holderActiveTabId by viewModel.activeTabId.collectAsStateWithLifecycle()
                LaunchedEffect(holderActiveTabId) { holder.activeTabId = holderActiveTabId }

                // Background media (v3.0.1): choose the keep-alive tab PROACTIVELY while the app
                // is still foreground, so KeepAliveWebView's visibility override is armed before
                // the screen-lock transition. Setting it in onStop is too late - the window goes
                // invisible (and Chromium pauses the media) before onStop runs. Re-runs on toggle
                // flips, tab switches, and incognito changes; obtain() arms a not-yet-created tab.
                val bgMediaEnabled by viewModel.backgroundMedia.collectAsStateWithLifecycle()
                val allTabsForBgMedia by viewModel.tabs.collectAsStateWithLifecycle()
                LaunchedEffect(bgMediaEnabled, holderActiveTabId, allTabsForBgMedia) {
                    val activeId = holderActiveTabId
                    val incognito = allTabsForBgMedia.find { it.id == activeId }?.isIncognito == true
                    holder.setBackgroundPlaybackTab(
                        if (bgMediaEnabled && activeId != null && !incognito) activeId else null
                    )
                }

                val forceDark by viewModel.forceDark.collectAsStateWithLifecycle()
                LaunchedEffect(forceDark) { holder.forceDark = forceDark }

                // Global text scale (I3): live re-apply to every open tab; site overrides win
                // inside the holder's re-resolve. Also seeds fresh WebViews via obtain().
                val textScale by viewModel.textScale.collectAsStateWithLifecycle()
                LaunchedEffect(textScale) { holder.applyGlobalTextScale(textScale) }

                // Page-start site-settings lookup: a plain read of the VM's in-memory host map,
                // so the WebView client callback never blocks on the database.
                LaunchedEffect(Unit) {
                    holder.siteSettingsProvider = { host -> viewModel.siteSettingsByHost.value[host] }
                }

                val httpsOnly by viewModel.httpsOnly.collectAsStateWithLifecycle()
                LaunchedEffect(httpsOnly) { holder.httpsOnly = httpsOnly }

                val useSystemDownloader by viewModel.useSystemDownloader.collectAsStateWithLifecycle()
                LaunchedEffect(useSystemDownloader) { holder.useSystemDownloader = useSystemDownloader }

                val jsEnabled by viewModel.javaScriptEnabled.collectAsStateWithLifecycle()
                val cookiesEnabled by viewModel.cookiesEnabled.collectAsStateWithLifecycle()
                val safeBrowsing by viewModel.safeBrowsing.collectAsStateWithLifecycle()
                LaunchedEffect(jsEnabled, cookiesEnabled, safeBrowsing) {
                    holder.applyPolicy(jsEnabled, cookiesEnabled, safeBrowsing)
                }

                val dismissCookieBanners by viewModel.dismissCookieBanners.collectAsStateWithLifecycle()
                LaunchedEffect(dismissCookieBanners) { holder.dismissCookieBanners = dismissCookieBanners }

                val gpcEnabled by viewModel.gpcEnabled.collectAsStateWithLifecycle()
                LaunchedEffect(gpcEnabled) { holder.gpcEnabled = gpcEnabled }

                val adBlockEnabled by viewModel.adBlockEnabled.collectAsStateWithLifecycle()
                val adAllowedSites by viewModel.adAllowedSites.collectAsStateWithLifecycle()
                LaunchedEffect(adBlockEnabled, adAllowedSites) {
                    (application as BrowseApplication).adBlockEngine
                        .updatePolicy(adBlockEnabled, adAllowedSites)
                }

                // Screen transitions on the Orbit v3.1 motion curve (unification pass): fades use
                // OrbitMotion.structural/quick directly (both are actually finite specs — spring()/
                // tween() under the hood — just cast to the Finite* type fadeIn/fadeOut require).
                // The slide's IntOffset has no Orbit-token spec variant, so it mirrors
                // OrbitMotion.structural's exact spring (dampingRatio/stiffness) rather than a
                // divergent one-off tween.
                val structuralFade = OrbitMotion.structural as FiniteAnimationSpec<Float>
                val quickFade = OrbitMotion.quick as FiniteAnimationSpec<Float>
                val slideSpec = spring<androidx.compose.ui.unit.IntOffset>(
                    dampingRatio = 0.85f,
                    stiffness = Spring.StiffnessMediumLow,
                )
                NavHost(
                    navController = navController,
                    startDestination = "browser",
                    enterTransition = {
                        slideInVertically(
                            initialOffsetY = { it / 8 },
                            animationSpec = slideSpec,
                        ) + fadeIn(structuralFade)
                    },
                    exitTransition = { fadeOut(quickFade) },
                    popEnterTransition = { fadeIn(structuralFade) },
                    popExitTransition = {
                        slideOutVertically(
                            targetOffsetY = { it / 8 },
                            animationSpec = slideSpec,
                        ) + fadeOut(structuralFade)
                    },
                ) {
                    composable("browser") {
                        BrowserScreen(
                            viewModel = viewModel,
                            holder = holder,
                            onOpenHistory = { navController.navigate("history") },
                            onOpenBookmarks = { navController.navigate("bookmarks") },
                            onOpenPasswords = { navController.navigate("passwords") },
                            onOpenTabs = { navController.navigate("tabs") },
                            onOpenSettings = { navController.navigate("settings") },
                            onOpenDownloads = { navController.navigate("downloads") },
                            onOpenReadingList = { navController.navigate("reading") },
                            onPrint = { printCurrentPage(holder) },
                        )
                    }
                    composable("downloads") {
                        DownloadsScreen(
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable("reading") {
                        ReadingListScreen(
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable("settings") {
                        SettingsScreen(
                            viewModel = viewModel,
                            onClearBrowsingData = { holder.clearBrowsingData() },
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable("tabs") {
                        TabSwitcherScreen(
                            viewModel = viewModel,
                            holder = holder,
                            onTabChosen = { navController.popBackStack() },
                            onCloseTabView = { tabId -> holder.close(tabId) },
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable("history") {
                        HistoryScreen(
                            viewModel = viewModel,
                            onOpenUrl = { url ->
                                viewModel.onOpenUrl(url)
                                navController.popBackStack()
                            },
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable("bookmarks") {
                        BookmarksScreen(
                            viewModel = viewModel,
                            onOpenUrl = { url ->
                                viewModel.onOpenUrl(url)
                                navController.popBackStack()
                            },
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable("passwords") {
                        // Keep this route out of Recents thumbnails and screenshots — a
                        // revealed password must not survive in the Recents card after
                        // re-lock (v5.1 review). Scoped to the route, cleared on leave.
                        DisposableEffect(Unit) {
                            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
                            onDispose { window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE) }
                        }
                        // v5.1: gated behind device auth (pref default ON); hasDeviceLock is
                        // the fail-closed no-lockout rule (see its KDoc).
                        val lockPref by viewModel.lockPasswords.collectAsStateWithLifecycle()
                        val pwLocked by viewModel.passwordsLocked.collectAsStateWithLifecycle()
                        if (lockPref && pwLocked && hasDeviceLock()) {
                            com.udaytank.browse.ui.components.LockGate(
                                title = "Passwords locked",
                                subtitle = "Your saved logins are hidden. Verify to continue.",
                                onUnlock = { promptPasswordsUnlock() },
                            )
                        } else {
                            PasswordsScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() },
                            )
                        }
                    }
                }

                // Biometric gate over incognito content.
                val locked by viewModel.incognitoLocked.collectAsStateWithLifecycle()
                val tabs by viewModel.tabs.collectAsStateWithLifecycle()
                val activeId by viewModel.activeTabId.collectAsStateWithLifecycle()
                val activeIsIncognito = tabs.find { it.id == activeId }?.isIncognito == true
                if (locked && activeIsIncognito) {
                    IncognitoLockScreen(onUnlock = { promptBiometricUnlock() })
                }

                // Fullscreen HTML5 video (WebChromeClient custom view) - drawn above everything
                // else, including the incognito lock screen, since it's the topmost content the
                // user was already looking at.
                val fullscreenView = fullscreenVideoView
                LaunchedEffect(fullscreenView) {
                    val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                    if (fullscreenView != null) {
                        insetsController.systemBarsBehavior =
                            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                        insetsController.hide(WindowInsetsCompat.Type.systemBars())
                    } else {
                        insetsController.show(WindowInsetsCompat.Type.systemBars())
                    }
                }
                if (fullscreenView != null) {
                    BackHandler { holder.exitFullscreen() }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black)
                    ) {
                        // key() forces the AndroidView node to be recreated when the engine
                        // swaps custom views without an intervening hide (chained fullscreen videos).
                        androidx.compose.runtime.key(fullscreenView) {
                            AndroidView(
                                factory = {
                                    (fullscreenView.parent as? ViewGroup)?.removeView(fullscreenView)
                                    fullscreenView
                                },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }
        }
    }
}
