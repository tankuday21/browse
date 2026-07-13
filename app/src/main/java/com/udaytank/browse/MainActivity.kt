package com.udaytank.browse

import android.app.PictureInPictureParams
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.util.Rational
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import com.udaytank.browse.ui.theme.Orbit
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
        // Page monitor -> session/notification. Bridge callbacks arrive on a WebView binder
        // thread; startService is thread-safe, so forward straight through.
        holder.mediaStateListener = { title, playing ->
            com.udaytank.browse.media.MediaHoldService.updateState(appContext, title, playing)
        }
        holder.mediaEndedListener = {
            // The monitor's own `ended` handler clicks "next"; native side just refreshes state.
        }
        holder.startMediaMonitor(tabId)

        val title = viewModel.tabs.value.find { it.id == tabId }?.title.orEmpty()
        com.udaytank.browse.media.MediaHoldService.start(this, tabId, host, title)
    }

    fun promptBiometricUnlock() {
        val executor = androidx.core.content.ContextCompat.getMainExecutor(this)
        val prompt = androidx.biometric.BiometricPrompt(
            this,
            executor,
            object : androidx.biometric.BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(
                    result: androidx.biometric.BiometricPrompt.AuthenticationResult,
                ) {
                    viewModel.onIncognitoUnlocked()
                }
            },
        )
        val info = androidx.biometric.BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock incognito")
            .setSubtitle("Verify it's you to view private tabs")
            .setAllowedAuthenticators(
                androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK or
                    androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()
        prompt.authenticate(info)
    }

    /**
     * Hands the active tab's page to the system print service (H5) — its dialog includes
     * Save-as-PDF. The adapter is null when no live WebView exists (home page), which the menu
     * already disables for; the toast covers any race where the page vanished in between.
     */
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
        // Auto-hiding command bar: convert the 24dp hide hysteresis into real pixels once.
        viewModel.setBarHideThresholdPx((24 * resources.displayMetrics.density).toInt())
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
                        override fun onPageStarted(tabId: Long, url: String) =
                            viewModel.onPageStarted(tabId, url)

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
                    },
                    ).also { holderRef[0] = it; webViewHolder = it }
                }
                DisposableEffect(Unit) {
                    onDispose { holder.destroyAll() }
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

                NavHost(
                    navController = navController,
                    startDestination = "browser",
                    enterTransition = {
                        slideInVertically(
                            initialOffsetY = { it / 8 },
                            animationSpec = tween(Orbit.MotionMs, easing = Orbit.Easing),
                        ) + fadeIn(tween(Orbit.MotionMs))
                    },
                    exitTransition = { fadeOut(tween(150)) },
                    popEnterTransition = { fadeIn(tween(Orbit.MotionMs)) },
                    popExitTransition = {
                        slideOutVertically(
                            targetOffsetY = { it / 8 },
                            animationSpec = tween(Orbit.MotionMs, easing = Orbit.Easing),
                        ) + fadeOut(tween(Orbit.MotionMs))
                    },
                ) {
                    composable("browser") {
                        BrowserScreen(
                            viewModel = viewModel,
                            holder = holder,
                            onOpenHistory = { navController.navigate("history") },
                            onOpenBookmarks = { navController.navigate("bookmarks") },
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
