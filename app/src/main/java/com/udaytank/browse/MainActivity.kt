package com.udaytank.browse

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.isSystemInDarkTheme
import com.udaytank.browse.ui.theme.Orbit
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
                    }).also { holderRef[0] = it }
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
            }
        }
    }
}
