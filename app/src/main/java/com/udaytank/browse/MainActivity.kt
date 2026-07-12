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
import com.udaytank.browse.ui.TabSwitcherScreen
import com.udaytank.browse.ui.WebViewHolder
import com.udaytank.browse.ui.theme.BrowseTheme

class MainActivity : FragmentActivity() {

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
    }

    override fun onStop() {
        super.onStop()
        // Re-lock incognito whenever the app leaves the foreground.
        if (viewModel.lockIncognito.value && viewModel.tabs.value.any { it.isIncognito }) {
            viewModel.onIncognitoLocked()
        }
        maybeKeepBackgroundMediaAlive()
    }

    override fun onStart() {
        super.onStart()
        com.udaytank.browse.media.MediaHoldService.stop(this)
        val tabId = viewModel.activeTabId.value
        if (tabId != null) webViewHolder?.setKeepAlive(tabId, false)
    }

    /**
     * Experimental background media playback (per-site opt-in, G3). Honest limits: this only
     * gives the OS a foreground-service reason to keep the process around and gives the user a
     * visible notification/control surface - an OEM battery manager (or the OS under memory
     * pressure) can still kill the process regardless. There is no existing "pause all WebViews
     * on background" path in this app (Activity lifecycle changes never call WebView.onPause), so
     * there is nothing to skip here beyond marking the tab exempt for if/when such a path is added.
     */
    private fun maybeKeepBackgroundMediaAlive() {
        if (!viewModel.backgroundMedia.value) return
        val tabId = viewModel.activeTabId.value ?: return
        // Never let an incognito tab trigger the service/notification, regardless of allowlist
        // membership - the host itself must never surface outside the tab's private session.
        if (viewModel.tabs.value.find { it.id == tabId }?.isIncognito == true) return
        val url = viewModel.uiState.value.currentUrl ?: return
        if (!(url.startsWith("http://") || url.startsWith("https://"))) return
        val host = com.udaytank.browse.browser.UrlHosts.of(url) ?: return
        if (host !in viewModel.backgroundMediaSites.value) return
        val audioManager = getSystemService(AUDIO_SERVICE) as android.media.AudioManager
        if (!audioManager.isMusicActive) return
        val holder = webViewHolder ?: return

        holder.setKeepAlive(tabId, true)
        com.udaytank.browse.media.MediaHoldService.controller = {
            holder.activeWebView(tabId)?.evaluateJavascript(
                "document.querySelectorAll('video,audio').forEach(m=>m.paused?m.play():m.pause())",
                null,
            )
        }
        com.udaytank.browse.media.MediaHoldService.onStopped = {
            holder.setKeepAlive(tabId, false)
        }
        com.udaytank.browse.media.MediaHoldService.start(this, tabId, host)
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

    private fun handleWebIntent(intent: Intent?) {
        val url = intent?.takeIf { it.action == Intent.ACTION_VIEW }?.dataString ?: return
        if (url.startsWith("http://") || url.startsWith("https://")) {
            viewModel.onExternalUrl(url)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleWebIntent(intent)
        setContent {
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            BrowseTheme(
                darkTheme = when (themeMode) {
                    ThemeMode.SYSTEM -> isSystemInDarkTheme()
                    ThemeMode.LIGHT -> false
                    ThemeMode.DARK -> true
                }
            ) {
                val navController = rememberNavController()
                val holderRef = remember { arrayOfNulls<WebViewHolder>(1) }
                val holder = remember {
                    WebViewHolder(this, adBlock = (application as BrowseApplication).adBlockEngine, listener = object : WebViewHolder.Listener {
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
                    }).also { holderRef[0] = it; webViewHolder = it }
                }
                DisposableEffect(Unit) {
                    onDispose { holder.destroyAll() }
                }

                val forceDark by viewModel.forceDark.collectAsStateWithLifecycle()
                LaunchedEffect(forceDark) { holder.forceDark = forceDark }

                val httpsOnly by viewModel.httpsOnly.collectAsStateWithLifecycle()
                LaunchedEffect(httpsOnly) { holder.httpsOnly = httpsOnly }

                val useSystemDownloader by viewModel.useSystemDownloader.collectAsStateWithLifecycle()
                LaunchedEffect(useSystemDownloader) { holder.useSystemDownloader = useSystemDownloader }

                val jsEnabled by viewModel.javaScriptEnabled.collectAsStateWithLifecycle()
                val cookiesEnabled by viewModel.cookiesEnabled.collectAsStateWithLifecycle()
                LaunchedEffect(jsEnabled, cookiesEnabled) {
                    holder.applyPolicy(jsEnabled, cookiesEnabled)
                }

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
                        )
                    }
                    composable("downloads") {
                        DownloadsScreen(
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
