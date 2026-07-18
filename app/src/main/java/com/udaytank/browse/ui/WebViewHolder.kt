package com.udaytank.browse.ui

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.os.Environment
import android.webkit.CookieManager
import android.webkit.SafeBrowsingResponse
import android.webkit.SslErrorHandler
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.view.View
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.udaytank.browse.browser.ExternalLinks
import com.udaytank.browse.browser.HttpsUpgrade
import com.udaytank.browse.browser.IntentHardening
import com.udaytank.browse.browser.ReaderMode
import com.udaytank.browse.browser.SiteSettingsResolver
import com.udaytank.browse.browser.UrlHosts
import com.udaytank.browse.data.SiteSettingsEntity
import android.widget.Toast
import com.udaytank.browse.browser.AdBlockEngine
import com.udaytank.browse.browser.adblock.RequestTyping
import com.udaytank.browse.browser.adblock.Scriptlets
import java.io.ByteArrayInputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * Owns one live WebView per tab, outside the compose tree, so instances
 * (and their page state) survive navigation and recomposition.
 */
class WebViewHolder(
    private val context: Context,
    private val listener: Listener,
    private val adBlock: AdBlockEngine,
    private val annoyance: AdBlockEngine,
) {
    interface Listener {
        fun onPageStarted(tabId: Long, url: String)
        fun onProgressChanged(tabId: Long, percent: Int)
        fun onPageFinished(tabId: Long, url: String, title: String?)
        fun onHistoryChanged(tabId: Long, canGoBack: Boolean, canGoForward: Boolean)
        fun onSslError(tabId: Long, url: String)
        /** A Safe Browsing main-frame hit is waiting on the user's decision (see [resolveSafeBrowsing]). */
        fun onSafeBrowsingHit(tabId: Long, url: String, threatLabel: String)
        fun onRequestBlocked(tabId: Long)
        fun onLongPress(tabId: Long, url: String, isImage: Boolean)
        fun onDownloadStarted(downloadId: Long, fileName: String, url: String)
        fun onDownloadRequested(url: String, fileName: String, mimeType: String?, userAgent: String?)
        fun onPageError(tabId: Long, description: String)
        fun onFindResult(tabId: Long, ordinal: Int, total: Int)
        fun onPermissionRequest(request: PermissionRequestInfo)
        fun onTitleUpdated(tabId: Long, url: String, title: String)
        /** [view] on entering fullscreen (e.g. HTML5 video), null when it's dismissed. */
        fun onFullscreenVideo(view: View?)
        /**
         * The page scrolled ([dy] = scrollY - oldScrollY, +down / -up). High-frequency —
         * implementations must stay cheap (drives the auto-hiding command bar).
         */
        fun onPageScrolled(tabId: Long, scrollY: Int, dy: Int)

        /** The Element Zapper picker chose an element (v4.0): host + CSS selector for persistence. */
        fun onZapPicked(tabId: Long, host: String, selector: String, label: String)

        /** A login form was submitted (v4.7): host + best-guess username + password, to offer saving. Never fired for incognito. */
        fun onLoginSubmitted(tabId: Long, host: String, username: String, password: String)

        /** The page declared a high-res apple-touch-icon (v4.1). Source-direct; never fired for incognito. */
        fun onTouchIconUrl(host: String, url: String)

        /** WebView decoded the site's favicon (v4.1) — the bitmap fallback. Never fired for incognito. */
        fun onFaviconBitmap(host: String, bitmap: Bitmap)

        /**
         * A gesture-backed popup (target="_blank" / window.open) captured its first URL (v5.0);
         * open it as a new tab inheriting [parentTabId]'s context (incognito/Orbit/island).
         */
        fun onCreateWindow(parentTabId: Long, url: String)

        /**
         * The page requested a file picker for an `<input type="file">` (v4.8). Implementations
         * MUST invoke [filePathCallback] exactly once — with the picked URIs or null on
         * cancel/failure — and return true; returning false without touching the callback lets
         * the (nonexistent) default handling run, i.e. the input stays inert.
         */
        fun onShowFileChooser(
            filePathCallback: ValueCallback<Array<Uri>>,
            params: WebChromeClient.FileChooserParams,
        ): Boolean
    }

    private val webViews = mutableMapOf<Long, WebView>()
    private var jsEnabled = true
    private var safeBrowsingEnabled = true

    /**
     * Pending popup interceptors keyed by parent tab (v5.0) — throwaway WebViews handed to the
     * engine's WebViewTransport whose only job is to capture the popup's first URL. Removed on
     * capture; destroyed with the parent tab so an about:blank popup that never navigates
     * can't leak a WebView. UI-thread only.
     */
    private val popupInterceptors = mutableMapOf<Long, WebView>()

    /** For deferred destruction of unattached WebViews (View.post never runs pre-attach). */
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    /**
     * Pending Safe Browsing decisions, keyed by tab (D1). Written/read on the UI thread only
     * (onSafeBrowsingHit and the interstitial's buttons both run there). An entry is removed
     * the moment it's resolved or superseded — never invoked twice (the engine would throw).
     */
    private val safeBrowsingResponses = mutableMapOf<Long, SafeBrowsingResponse>()

    /**
     * Resolves the tab's pending Safe Browsing interstitial: proceed into the flagged page or
     * navigate back to safety. No-op when nothing is pending (e.g. the user already navigated
     * away, which dropped the callback via [onPageStarted][WebViewClient] / [close]).
     */
    fun resolveSafeBrowsing(tabId: Long, proceed: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) return
        val callback = safeBrowsingResponses.remove(tabId) ?: return
        if (proceed) callback.proceed(false) else callback.backToSafety(false)
    }

    /**
     * Tabs exempted from being paused when the app backgrounds (the background-media-playback
     * opt-in). [pauseAllExceptKeptAlive] pauses every OTHER tab's WebView on background so their
     * media/JS stops (battery + correctness), while a kept-alive tab keeps running so its audio
     * continues over the lock screen. We never call the process-global pauseTimers() — that would
     * freeze the kept-alive tab too.
     */
    // Read from a WebView binder thread (MediaJsBridge) as well as the UI thread, so keep it concurrent.
    private val keepAliveTabs = java.util.concurrent.ConcurrentHashMap.newKeySet<Long>()

    fun setKeepAlive(tabId: Long, keep: Boolean) {
        if (keep) keepAliveTabs.add(tabId) else keepAliveTabs.remove(tabId)
        // Also tell the WebView itself to lie about window visibility while backgrounded, so
        // Chromium doesn't pause its media on screen lock (the actual cause of "stops on lock").
        (webViews[tabId] as? KeepAliveWebView)?.keepAliveInBackground = keep
    }

    /**
     * The one tab allowed to keep playing while the app is backgrounded/locked, or null.
     *
     * This MUST be set proactively while the app is still in the foreground (from a Compose
     * effect reacting to the background-media toggle + active tab) — NOT reactively in onStop.
     * On screen lock the framework delivers `onWindowVisibilityChanged(GONE)` during the stop
     * transition, *before* onStop runs; if the keep-alive flag isn't already armed by then,
     * Chromium has already paused the media. Stored so a WebView created later (obtain) still
     * gets armed if it is the designated tab.
     */
    @Volatile
    private var backgroundPlaybackTabId: Long? = null

    fun setBackgroundPlaybackTab(tabId: Long?) {
        backgroundPlaybackTabId = tabId
        webViews.forEach { (id, webView) ->
            val keep = tabId != null && id == tabId
            if (keep) keepAliveTabs.add(id) else keepAliveTabs.remove(id)
            (webView as? KeepAliveWebView)?.keepAliveInBackground = keep
        }
    }

    fun isKeptAlive(tabId: Long): Boolean = tabId in keepAliveTabs

    /**
     * App backgrounding / screen lock: pause every tab except those the user opted into
     * background playback. A non-opted tab therefore stops on lock (matching the pre-feature
     * expectation), while the opted-in tab plays on. Per-view onPause only — never the global
     * pauseTimers().
     */
    fun pauseAllExceptKeptAlive() {
        webViews.forEach { (tabId, webView) -> if (tabId !in keepAliveTabs) webView.onPause() }
    }

    /** Foreground again: resume every tab (onResume is a no-op on ones that were never paused). */
    fun resumeAll() {
        webViews.values.forEach { it.onResume() }
    }

    /**
     * Bridge for the lock-screen media feature: the page's injected [MediaControl.MONITOR] calls
     * these (on a WebView binder thread) to report playback state and track-ended. Set by
     * MainActivity while a foreground media tab is active, nulled when it stops — so the JS
     * interface (attached to every non-incognito tab) is inert unless the feature is running.
     */
    @Volatile
    var mediaStateListener: ((title: String, playing: Boolean, positionMs: Int, durationMs: Int) -> Unit)? = null

    @Volatile
    var mediaEndedListener: (() -> Unit)? = null

    /** Injects the one-time media monitor into a tab (opted-in, non-incognito; UI thread). */
    fun startMediaMonitor(tabId: Long) {
        webViews[tabId]?.evaluateJavascript(com.udaytank.browse.browser.MediaControl.MONITOR, null)
    }

    /** Runs a [MediaControl] transport snippet (play/pause/next/prev) on a tab (UI thread). */
    fun runMediaCommand(tabId: Long, js: String) {
        webViews[tabId]?.evaluateJavascript(js, null)
    }

    /** v4.0 Element Zapper: enter the in-page picker on a tab (UI thread). */
    fun enterZapMode(tabId: Long) {
        webViews[tabId]?.evaluateJavascript(com.udaytank.browse.browser.zap.ZapScripts.PICKER_JS, null)
    }

    /** Tear down an active zap picker (e.g. tab switch / navigation). */
    fun exitZapMode(tabId: Long) {
        webViews[tabId]?.evaluateJavascript(com.udaytank.browse.browser.zap.ZapScripts.TEARDOWN_JS, null)
    }

    /** Hide the saved [selectors] on a tab (re-applied on every load; no-op if empty). */
    fun applyZaps(tabId: Long, selectors: List<String>) {
        if (selectors.isEmpty()) return
        webViews[tabId]?.evaluateJavascript(
            com.udaytank.browse.browser.zap.ZapScripts.applyHiddenJs(selectors),
            null,
        )
    }

    /** v4.7 Passwords: fill a saved login into the tab's page (user-initiated; JSON-escaped JS). */
    fun fillCredentials(tabId: Long, username: String, password: String) {
        webViews[tabId]?.evaluateJavascript(
            com.udaytank.browse.browser.PasswordScripts.fillJs(username, password),
            null,
        )
    }

    /**
     * The JavascriptInterface the monitor reports through. Only our own methods, both
     * @JavascriptInterface-annotated, exposing nothing sensitive; minSdk 26 makes the pre-17
     * addJavascriptInterface vuln irrelevant. Never attached to incognito tabs.
     */
    private inner class MediaJsBridge(private val tabId: Long) {
        @android.webkit.JavascriptInterface
        fun onMediaState(title: String?, playing: Boolean, positionMs: Int, durationMs: Int) {
            if (tabId in keepAliveTabs) mediaStateListener?.invoke(title ?: "", playing, positionMs, durationMs)
        }

        @android.webkit.JavascriptInterface
        fun onEnded() {
            if (tabId in keepAliveTabs) mediaEndedListener?.invoke()
        }
    }

    /**
     * v4.7 Passwords: a submitted login crosses here. Attached ONLY to non-incognito tabs (like
     * [MediaJsBridge]), so incognito logins have no channel. Resolves the host from the tab's
     * committed page (not JS-supplied) so a cross-origin frame can't spoof which site to save under.
     */
    private inner class PasswordJsBridge(private val tabId: Long) {
        @android.webkit.JavascriptInterface
        fun onSubmit(username: String?, password: String?) {
            if (password.isNullOrEmpty()) return
            val host = pageHosts[tabId] ?: return
            listener.onLoginSubmitted(tabId, host, username ?: "", password)
        }
    }

    /** Bridge the Element Zapper picker posts a chosen selector through (v4.0). Persistence is
     *  gated downstream (never persisted in incognito); only a CSS selector + host cross here. */
    private inner class ZapJsBridge(private val tabId: Long) {
        @android.webkit.JavascriptInterface
        fun picked(host: String?, selector: String?, label: String?) {
            if (host.isNullOrBlank() || selector.isNullOrBlank()) return
            listener.onZapPicked(tabId, host, selector, label ?: "")
        }
    }

    /** The live WebView for [tabId], if one has been created via [obtain]. */
    fun activeWebView(tabId: Long): WebView? = webViews[tabId]

    val thumbnails = ThumbnailStore(context)

    @Volatile
    var forceDark: Boolean = false

    @Volatile
    var httpsOnly: Boolean = false

    @Volatile
    var useSystemDownloader: Boolean = false

    /**
     * Cookie-banner auto-dismiss (D2): gates the [annoyance] engine for both network blocking
     * and cosmetic hiding. Global on/off — deliberately not subject to the per-site ad
     * allowlist, which only exempts a site from the ad engine.
     */
    @Volatile
    var dismissCookieBanners: Boolean = true

    /**
     * Global Privacy Control (D5). Two surfaces, both with honest limits:
     *  - a `navigator.globalPrivacyControl` JS shim, registered per WebView at [obtain] as a
     *    document-start script (so toggling takes effect on NEW tabs; existing tabs keep their
     *    creation-time state — the settings subtitle says so). Fallback for WebViews without
     *    DOCUMENT_START_SCRIPT: injected at page start, which is later than ideal.
     *  - a `Sec-GPC: 1` header on navigations WE issue via [loadUrl] (address bar, bookmarks,
     *    https-upgrade redispatch). In-page link clicks stay engine-driven and don't carry it;
     *    the JS shim is what sites see there.
     */
    @Volatile
    var gpcEnabled: Boolean = false

    /**
     * Per-site display override lookup (H6). Supplied by the UI layer with a lambda backed by
     * the ViewModel's in-memory host map, so calling it from [onPageStarted][WebViewClient]
     * never blocks. Null until the UI wires it (fresh WebViews then just use the globals).
     */
    @Volatile
    var siteSettingsProvider: ((String) -> SiteSettingsEntity?)? = null

    /** Hosts the user has granted a given permission for this session. */
    private val grantedPermissions = HashSet<String>()

    // Fullscreen video (HTML5 <video> fullscreen / WebChromeClient custom view). Only one tab
    // can be in fullscreen at a time, so this lives on the holder rather than per-WebView.
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null

    /** Which tab's WebView owns the current fullscreen [customView]; null when none is showing. */
    private var customViewTabId: Long? = null

    /**
     * The tab the UI is currently showing. Written from the compose layer (MainActivity keeps
     * it in sync with the ViewModel's activeTabId), read on the UI thread in WebChromeClient
     * callbacks — @Volatile only for the cross-thread write visibility, like the other globals.
     * Gates [onShowCustomView][WebChromeClient]: a BACKGROUND tab that requests fullscreen
     * (autoplaying video, scripted abuse) must not hijack the screen.
     */
    @Volatile
    var activeTabId: Long? = null

    /**
     * App-initiated exit from fullscreen video (e.g. the user pressed back, or PiP was
     * dismissed). Tells the WebView engine the custom view is gone and clears our state.
     * Safe to call when nothing is in fullscreen (no-op).
     */
    fun exitFullscreen() {
        val callback = customViewCallback
        if (customView == null && callback == null) return
        customView = null
        customViewCallback = null
        customViewTabId = null
        listener.onFullscreenVideo(null)
        callback?.onCustomViewHidden()
    }

    fun rememberPermissionGrant(host: String, resource: String) {
        grantedPermissions.add("$host|$resource")
    }

    private fun isGranted(host: String, resource: String) =
        grantedPermissions.contains("$host|$resource")

    fun extractReaderContent(tabId: Long, onResult: (String) -> Unit) {
        val webView = webViews[tabId] ?: run { onResult("{\"ok\":false}"); return }
        webView.evaluateJavascript(ReaderMode.EXTRACT_SCRIPT) { json -> onResult(json) }
    }

    fun captureThumbnail(tabId: Long) {
        webViews[tabId]?.let { thumbnails.capture(tabId, it) }
    }

    fun findInPage(tabId: Long, query: String) {
        webViews[tabId]?.findAllAsync(query)
    }

    fun findNext(tabId: Long, forward: Boolean) {
        webViews[tabId]?.findNext(forward)
    }

    fun clearFind(tabId: Long) {
        webViews[tabId]?.clearMatches()
    }

    /**
     * Tabs whose desktop toggle the user flipped on via the menu — the per-tab baseline that a
     * site's tri-state desktop override resolves against. UI-thread only, like [webViews].
     */
    private val desktopTabs = mutableSetOf<Long>()

    /**
     * The UA state last written to each tab's settings. Guards every desktop-UA write so
     * re-applying an unchanged value is a no-op — critical for [applySiteSettings], which runs
     * inside onPageStarted where a reload would start the page (and this callback) over again.
     */
    private val appliedDesktop = mutableMapOf<Long, Boolean>()

    /** The user's explicit desktop-site toggle for a tab: records the baseline and reloads. */
    fun setDesktopMode(tabId: Long, desktop: Boolean) {
        if (desktop) desktopTabs.add(tabId) else desktopTabs.remove(tabId)
        if (applyDesktopUa(tabId, desktop)) webViews[tabId]?.reload()
    }

    /**
     * Writes the desktop UA settings only when they differ from what's already applied.
     * Returns true when a change was made (callers outside onPageStarted may then reload).
     */
    private fun applyDesktopUa(tabId: Long, desktop: Boolean): Boolean {
        val webView = webViews[tabId] ?: return false
        if ((appliedDesktop[tabId] ?: false) == desktop) return false
        appliedDesktop[tabId] = desktop
        webView.settings.userAgentString = if (desktop) DESKTOP_UA else null
        webView.settings.loadWithOverviewMode = desktop
        webView.settings.useWideViewPort = desktop
        return true
    }

    /**
     * Applies the site's effective display settings when a page starts (H6). textZoom and
     * force-dark affect the in-flight load directly; a desktop-UA difference only retunes the
     * settings so the NEXT request for this host goes out with the right UA — deliberately no
     * reload() here, which from inside onPageStarted would loop forever.
     */
    private fun applySiteSettings(tabId: Long, webView: WebView, url: String) {
        val host = UrlHosts.of(url) ?: return
        val effective = SiteSettingsResolver.resolve(
            globalForceDark = forceDark,
            globalDesktop = tabId in desktopTabs,
            globalTextZoom = globalTextScale,
            override = siteSettingsProvider?.invoke(host),
        )
        webView.settings.textZoom = effective.textZoom
        applyForceDark(webView, effective.forceDark)
        applyDesktopUa(tabId, effective.desktopMode)
    }

    /**
     * The user's global text scale in percent (I3). @Volatile like the other globals: written
     * from the UI via [applyGlobalTextScale], read in obtain() and onPageStarted paths.
     */
    @Volatile
    private var globalTextScale: Int = 100

    /**
     * Live re-apply of a changed global text scale (I3) to every open tab, re-resolving each
     * against its site override — a positive per-site textZoom still wins, exactly as at page
     * start. New WebViews pick the value up in [obtain]; page starts keep re-resolving.
     */
    fun applyGlobalTextScale(scale: Int) {
        globalTextScale = scale
        webViews.forEach { (tabId, webView) ->
            val override = UrlHosts.of(webView.url)?.let { siteSettingsProvider?.invoke(it) }
            webView.settings.textZoom = SiteSettingsResolver.resolve(
                globalForceDark = forceDark,
                globalDesktop = tabId in desktopTabs,
                globalTextZoom = scale,
                override = override,
            ).textZoom
        }
    }

    /** Algorithmic darkening (same mechanism the global force-dark setting uses). */
    private fun applyForceDark(webView: WebView, enabled: Boolean) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(webView.settings, enabled)
        }
    }

    // Live-apply accessors for the site-settings sheet: changes take effect immediately on the
    // visible page, independent of whether they were persisted (incognito never persists).

    fun applyTextZoom(tabId: Long, zoom: Int) {
        webViews[tabId]?.settings?.textZoom = zoom
    }

    fun applyForceDark(tabId: Long, enabled: Boolean) {
        webViews[tabId]?.let { applyForceDark(it, enabled) }
    }

    /** Sheet-driven desktop change: reloads only when the UA actually changed. */
    fun applyDesktopMode(tabId: Long, desktop: Boolean) {
        if (applyDesktopUa(tabId, desktop)) webViews[tabId]?.reload()
    }

    /**
     * Print adapter for the tab's current page (H5), fed to the system PrintManager whose
     * dialog includes Save-as-PDF. Null when the tab has no live WebView (e.g. the home page).
     */
    fun printAdapter(tabId: Long): android.print.PrintDocumentAdapter? =
        webViews[tabId]?.createPrintDocumentAdapter("Andromeda")

    // shouldInterceptRequest runs on WebView's background threads;
    // page hosts are written on the UI thread in onPageStarted.
    private val pageHosts = ConcurrentHashMap<Long, String>()

    /**
     * The app's navigation entry point for a tab (address bar / bookmarks / pending commands):
     * adds the `Sec-GPC: 1` header when GPC is on. No-op when the tab has no live WebView.
     */
    fun loadUrl(tabId: Long, url: String) {
        webViews[tabId]?.loadUrl(url, gpcHeaders())
    }

    private fun gpcHeaders(): Map<String, String> =
        if (gpcEnabled) mapOf("Sec-GPC" to "1") else emptyMap()

    /** Applies global browsing policy to all live WebViews and future ones. */
    fun applyPolicy(javaScriptEnabled: Boolean, cookiesEnabled: Boolean, safeBrowsing: Boolean) {
        jsEnabled = javaScriptEnabled
        safeBrowsingEnabled = safeBrowsing
        webViews.values.forEach {
            it.settings.javaScriptEnabled = javaScriptEnabled
            applySafeBrowsing(it)
        }
        CookieManager.getInstance().setAcceptCookie(cookiesEnabled)
    }

    /** Google Safe Browsing (D1) — only where the installed WebView supports the toggle. */
    private fun applySafeBrowsing(webView: WebView) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.SAFE_BROWSING_ENABLE)) {
            WebSettingsCompat.setSafeBrowsingEnabled(webView.settings, safeBrowsingEnabled)
        }
    }

    /**
     * Removes a deleted Orbit's WebView profile (v4.2) so its cookies/storage are actually
     * purged, not just orphaned. ProfileStore only allows deleting a profile once no live
     * WebView is using it — callers must close the Orbit's tabs first (see
     * BrowserViewModel.onDeleteOrbit, which emits [BrowserViewModel.orbitProfileToDelete] only
     * after that close-out completes).
     */
    fun deleteProfile(profileKey: String) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) return
        runCatching { androidx.webkit.ProfileStore.getInstance().deleteProfile(profileKey) }
            .onFailure { android.util.Log.w("WebViewHolder", "profile delete failed", it) }
    }

    /** Clears cache, cookies, and web storage. History/tabs are the data layer's job. */
    fun clearBrowsingData() {
        webViews.values.forEach { it.clearCache(true) }
        CookieManager.getInstance().removeAllCookies(null)
        WebStorage.getInstance().deleteAllData()
    }

    /** Drops every page thumbnail (memory + disk) — Black Hole panic-wipe. */
    fun clearThumbnails() {
        thumbnails.clearAll()
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun obtain(tabId: Long, incognito: Boolean = false, profileKey: String? = null): WebView = webViews.getOrPut(tabId) {
        KeepAliveWebView(context).apply {
            settings.javaScriptEnabled = jsEnabled
            applySafeBrowsing(this)
            // Pinch zoom always available (I3); the legacy on-screen +/- controls stay hidden.
            settings.setSupportZoom(true)
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            // Global text scale baseline; onPageStarted re-resolves per site (override wins).
            settings.textZoom = globalTextScale
            // v5.0 Popups: route target="_blank" / gesture-backed window.open through
            // onCreateWindow (→ a real new tab) instead of replacing this page.
            // javaScriptCanOpenWindowsAutomatically stays false — the engine itself suppresses
            // gesture-less scripted window.open, our first popup-blocker layer.
            settings.setSupportMultipleWindows(true)
            if (forceDark) applyForceDark(this, true)
            if (incognito) {
                // Leave no local traces: no DOM storage, no cache writes.
                settings.domStorageEnabled = false
                settings.cacheMode = WebSettings.LOAD_NO_CACHE
                // True cookie/storage isolation via a dedicated profile when the
                // installed WebView supports it (androidx.webkit ProfileStore).
                if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
                    runCatching {
                        val store = androidx.webkit.ProfileStore.getInstance()
                        val profile = store.getOrCreateProfile("incognito")
                        androidx.webkit.WebViewCompat.setProfile(this, profile.name)
                    }
                }
            } else {
                settings.domStorageEnabled = true
                // Lock-screen media bridge (see MediaJsBridge). Only our own annotated methods are
                // exposed and it stays inert until MainActivity sets the listeners for an opted-in
                // foreground media tab. NEVER attached to incognito tabs — a private page must not
                // report titles/state to the media session/notification.
                addJavascriptInterface(MediaJsBridge(tabId), com.udaytank.browse.browser.MediaControl.BRIDGE_NAME)
                // v4.7 Passwords: the login-capture bridge, also non-incognito-only — an incognito
                // login must never have a channel to report a credential through.
                addJavascriptInterface(PasswordJsBridge(tabId), com.udaytank.browse.browser.PasswordScripts.BRIDGE_NAME)
                // Per-Orbit cookie/storage isolation (v4.2): same ProfileStore mechanism as
                // incognito above, keyed by the tab's Orbit instead of the fixed "incognito" name.
                if (profileKey != null && WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
                    runCatching {
                        val store = androidx.webkit.ProfileStore.getInstance()
                        val profile = store.getOrCreateProfile(profileKey)
                        androidx.webkit.WebViewCompat.setProfile(this, profile.name)
                    }
                }
            }

            // Element Zapper bridge (v4.0): attached to ALL tabs (incognito can pick in-session);
            // persistence is refused for incognito downstream, so nothing private is written.
            addJavascriptInterface(ZapJsBridge(tabId), com.udaytank.browse.browser.zap.ZapScripts.BRIDGE_NAME)

            // GPC JS shim (D5): registered once per WebView at creation, before any page loads.
            val gpcShimAtDocumentStart = gpcEnabled &&
                WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
            if (gpcShimAtDocumentStart) {
                runCatching {
                    androidx.webkit.WebViewCompat.addDocumentStartJavaScript(this, GPC_SHIM, setOf("*"))
                }
            }

            // YouTube ad-block scriptlet: MUST run at document start (it prunes ad data out of
            // player responses before the player script reads them), so it follows the GPC
            // shim's registration pattern. Registered only while the ad-block master toggle is
            // on at tab creation; the per-site allowlist and later toggle flips are handled by
            // the __andromedaAdblockOff kill switch set at every page start (the script also
            // self-gates on *.youtube.com hosts, belt and braces to the origin rules here).
            val ytScriptletAtDocumentStart = adBlock.isActiveFor(null) &&
                WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
            if (ytScriptletAtDocumentStart) {
                runCatching {
                    androidx.webkit.WebViewCompat.addDocumentStartJavaScript(
                        this,
                        Scriptlets.YOUTUBE_SCRIPT,
                        setOf("https://*.youtube.com", "https://youtube.com", "https://music.youtube.com"),
                    )
                }
            }

            // If this tab was already designated the background-playback tab before its WebView
            // existed, arm keep-alive now so a later lock transition can't pause its media.
            if (tabId == backgroundPlaybackTabId) {
                keepAliveTabs.add(tabId)
                keepAliveInBackground = true
            }

            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                    Uri.parse(url).host?.let { pageHosts[tabId] = it }
                    // A new navigation supersedes any pending Safe Browsing decision for this
                    // tab; drop the callback so a stale interstitial can never act on it.
                    safeBrowsingResponses.remove(tabId)
                    // GPC fallback for WebViews without document-start scripts: later than
                    // ideal (scripts that read the flag before this ran miss it), best effort.
                    if (gpcEnabled && !gpcShimAtDocumentStart) {
                        view.evaluateJavascript(GPC_SHIM, null)
                    }
                    applySiteSettings(tabId, view, url)
                    // Early cosmetic pass: hide ad/banner elements before first paint when
                    // possible. The scripts guard against a still-null documentElement, and
                    // onPageFinished repeats the injection as the reliable late pass.
                    val earlyCss = adBlock.cosmeticInjectionScript(pageHosts[tabId])
                    if (earlyCss.isNotEmpty()) view.evaluateJavascript(earlyCss, null)
                    if (dismissCookieBanners) {
                        val earlyAnnoyCss = annoyance.cosmeticInjectionScript(pageHosts[tabId])
                        if (earlyAnnoyCss.isNotEmpty()) view.evaluateJavascript(earlyAnnoyCss, null)
                    }
                    // Scriptlet gating: on hosts that have one, publish the kill switch the
                    // (already document-start-registered) script re-checks on every hook call
                    // and watcher tick — the master toggle and per-site ad allowlist thereby
                    // keep working even though registration happened at tab creation. When
                    // document-start scripts are unsupported (or ad-block was off at creation),
                    // fall back to injecting here — later than ideal, best effort.
                    val scriptlet = Scriptlets.scriptFor(pageHosts[tabId])
                    if (scriptlet.isNotEmpty()) {
                        val off = !adBlock.isActiveFor(pageHosts[tabId])
                        view.evaluateJavascript("window.__andromedaAdblockOff=$off;", null)
                        if (!off && !ytScriptletAtDocumentStart) {
                            view.evaluateJavascript(scriptlet, null)
                        }
                    }
                    listener.onPageStarted(tabId, url)
                }

                override fun onSafeBrowsingHit(
                    view: WebView,
                    request: WebResourceRequest,
                    threatType: Int,
                    callback: SafeBrowsingResponse,
                ) {
                    // Framework callback exists since API 27; on 26 it simply never fires.
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) return
                    if (!request.isForMainFrame) {
                        // A flagged sub-resource isn't worth an interstitial — drop it silently.
                        callback.backToSafety(false)
                        return
                    }
                    safeBrowsingResponses[tabId] = callback
                    listener.onSafeBrowsingHit(tabId, request.url.toString(), threatLabel(threatType))
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest,
                ): Boolean {
                    val target = request.url.toString()
                    // v4.9: non-http(s) schemes dispatch to other apps (mailto → mail, upi →
                    // payment app, intent:// → declared app) instead of dying in the engine
                    // with ERR_UNKNOWN_URL_SCHEME. Gesture-less or incognito navigations
                    // confirm first — never auto-launch. See ExternalLinks / IntentHardening.
                    val confirm = ExternalLinks.needsConfirm(request.hasGesture(), incognito)
                    when (ExternalLinks.classify(target)) {
                        ExternalLinks.LinkAction.LoadInPage -> {
                            if (HttpsUpgrade.shouldUpgrade(target, httpsOnly)) {
                                HttpsUpgrade.upgrade(target)?.let { view.loadUrl(it, gpcHeaders()); return true }
                            }
                            return false
                        }
                        ExternalLinks.LinkAction.Ignore -> return true
                        is ExternalLinks.LinkAction.OpenApp -> {
                            launchExternalApp(tabId, confirm, incognito, Intent(Intent.ACTION_VIEW, request.url)) {
                                toastNoApp()
                            }
                            return true
                        }
                        is ExternalLinks.LinkAction.IntentUri -> {
                            openIntentUri(tabId, confirm, incognito, target, view)
                            return true
                        }
                    }
                }

                override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest,
                ): WebResourceResponse? {
                    // Main-frame documents are never blocked (deliberate UX choice — the
                    // engine would refuse too, but skipping here avoids all the work).
                    if (request.isForMainFrame) return null
                    val url = request.url.toString()
                    val headers = request.requestHeaders ?: emptyMap()
                    val type = RequestTyping.infer(request.url.path, headers, mainFrame = false)
                    val pageHost = pageHosts[tabId]
                    // Ads first (respects the per-site ad allowlist), then cookie-consent
                    // scripts (global toggle only — the ad allowlist doesn't exempt these).
                    val blocked = adBlock.shouldBlock(url, request.url.host, pageHost, type, mainFrame = false) ||
                        (
                            dismissCookieBanners &&
                                annoyance.shouldBlock(url, request.url.host, pageHost, type, mainFrame = false)
                            )
                    return if (blocked) {
                        listener.onRequestBlocked(tabId)
                        WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                    } else {
                        null // null = let the request through normally
                    }
                }

                override fun onPageFinished(view: WebView, url: String) {
                    // Inject cosmetic ad-hiding CSS once the DOM exists.
                    val css = adBlock.cosmeticInjectionScript(pageHosts[tabId])
                    if (css.isNotEmpty()) view.evaluateJavascript(css, null)
                    // Cookie-banner hiding rides the same mechanism from the annoyance list.
                    if (dismissCookieBanners) {
                        val annoyCss = annoyance.cosmeticInjectionScript(pageHosts[tabId])
                        if (annoyCss.isNotEmpty()) view.evaluateJavascript(annoyCss, null)
                    }
                    // Desktop site: a desktop UA alone doesn't defeat a site's
                    // `<meta viewport width=device-width>`, which forces a mobile layout. Override
                    // the viewport to a fixed desktop width so the page actually lays out wide.
                    if (tabId in desktopTabs) view.evaluateJavascript(DESKTOP_VIEWPORT_JS, null)
                    // Favicon (v4.1): read the site's OWN best declared icon and cache it high-res.
                    // Never for incognito. Result comes back JSON-encoded ("\"https://...\"" or null).
                    if (!incognito) {
                        view.evaluateJavascript(BEST_ICON_JS) { result ->
                            val iconUrl = result
                                ?.trim()
                                ?.removeSurrounding("\"")
                                ?.replace("\\/", "/")
                                ?.takeIf { it.startsWith("http") }
                            val host = pageHosts[tabId]
                            if (iconUrl != null && !host.isNullOrBlank()) {
                                listener.onTouchIconUrl(host, iconUrl)
                            }
                        }
                        // v4.7 Passwords: hook login-form submits so we can offer to save. The
                        // bridge it reports through is only attached to non-incognito tabs.
                        view.evaluateJavascript(com.udaytank.browse.browser.PasswordScripts.HOOK_SUBMIT_JS, null)
                    }
                    listener.onPageFinished(tabId, url, view.title)
                }

                override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
                    listener.onHistoryChanged(tabId, view.canGoBack(), view.canGoForward())
                }

                override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                    // Spec rule: never silently proceed past a bad certificate.
                    handler.cancel()
                    listener.onSslError(tabId, error.url)
                }

                override fun onReceivedError(
                    view: WebView,
                    request: WebResourceRequest,
                    error: WebResourceError,
                ) {
                    // Sub-resource failures (a broken image, a blocked ad)
                    // are normal; only whole-page failures get the error UI.
                    if (request.isForMainFrame) {
                        listener.onPageError(tabId, error.description?.toString() ?: "Unknown error")
                    }
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView, newProgress: Int) {
                    listener.onProgressChanged(tabId, newProgress)
                }

                override fun onReceivedTitle(view: WebView, title: String?) {
                    // Titles often arrive after onPageFinished (SPAs, search
                    // pages) — update the tab and history when the real one lands.
                    if (!title.isNullOrBlank()) {
                        listener.onTitleUpdated(tabId, view.url ?: return, title)
                    }
                }

                // Site-icon capture (v4.1). WebView parses the page's <link rel> tags for us and
                // reports the site's own declared icon — so this is source-direct, never a proxy.
                // NEVER captured for incognito tabs (`incognito` is this tab's flag, in closure
                // scope): a private tab must leave no record of the hosts it visited.
                override fun onReceivedTouchIconUrl(view: WebView, url: String?, precomposed: Boolean) {
                    if (incognito || url.isNullOrBlank()) return
                    pageHosts[tabId]?.takeIf { it.isNotBlank() }?.let { listener.onTouchIconUrl(it, url) }
                }

                override fun onReceivedIcon(view: WebView, icon: Bitmap?) {
                    if (incognito || icon == null) return
                    pageHosts[tabId]?.takeIf { it.isNotBlank() }?.let { listener.onFaviconBitmap(it, icon) }
                }

                override fun onPermissionRequest(request: android.webkit.PermissionRequest) {
                    val host = request.origin.host ?: ""
                    val resources = request.resources
                    val label = when {
                        resources.any { it.contains("VideoCapture") } -> "use your camera"
                        resources.any { it.contains("AudioCapture") } -> "use your microphone"
                        else -> "access a device feature"
                    }
                    val resourceKey = resources.joinToString()
                    if (isGranted(host, resourceKey)) {
                        request.grant(resources)
                        return
                    }
                    listener.onPermissionRequest(
                        PermissionRequestInfo(
                            host = host,
                            label = label,
                            grant = {
                                rememberPermissionGrant(host, resourceKey)
                                request.grant(resources)
                            },
                            deny = { request.deny() },
                        )
                    )
                }

                override fun onGeolocationPermissionsShowPrompt(
                    origin: String,
                    callback: android.webkit.GeolocationPermissions.Callback,
                ) {
                    val host = Uri.parse(origin).host ?: origin
                    if (isGranted(host, "geolocation")) {
                        callback.invoke(origin, true, false)
                        return
                    }
                    listener.onPermissionRequest(
                        PermissionRequestInfo(
                            host = host,
                            label = "access your location",
                            grant = {
                                rememberPermissionGrant(host, "geolocation")
                                callback.invoke(origin, true, false)
                            },
                            deny = { callback.invoke(origin, false, false) },
                        )
                    )
                }

                override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                    // Only the tab the user is looking at may take over the screen. A
                    // background tab's request (autoplay video, scripted abuse) is refused
                    // outright; null activeTabId (startup race) errs on the side of allowing.
                    val active = activeTabId
                    if (active != null && active != tabId) {
                        callback.onCustomViewHidden()
                        return
                    }
                    // Chrome-style: if a custom view is already showing (rare - e.g. the page
                    // swapped fullscreen elements without hiding the first one), hide it first.
                    if (customView != null) {
                        customViewCallback?.onCustomViewHidden()
                    }
                    customView = view
                    customViewCallback = callback
                    customViewTabId = tabId
                    listener.onFullscreenVideo(view)
                }

                // v5.0 Popups: target="_blank" / gesture-backed window.open. The engine demands
                // a WebView synchronously via the transport, but our tabs are created async —
                // so a throwaway interceptor WebView captures the popup's first URL, then a
                // real tab opens it through the normal pipeline (HTTPS-Only, ad-block, v4.9
                // external schemes all apply). window.opener is severed — documented limit.
                override fun onCreateWindow(
                    view: WebView,
                    isDialog: Boolean,
                    isUserGesture: Boolean,
                    resultMsg: android.os.Message,
                ): Boolean {
                    if (!isUserGesture) return false // popup blocked, Chrome-style
                    val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
                    // One pending interceptor per tab: a second popup supersedes the first
                    // (safe to destroy synchronously — we're not inside ITS callback).
                    popupInterceptors.remove(tabId)?.destroy()
                    val interceptor = WebView(context)
                    var captured = false
                    val capture = capture@{ url: String? ->
                        if (captured || url.isNullOrBlank() || url == "about:blank") return@capture
                        captured = true
                        if (popupInterceptors[tabId] === interceptor) popupInterceptors.remove(tabId)
                        // Never destroy a WebView synchronously from inside its own engine
                        // callback; an unattached View's post() never runs, so use the handler.
                        mainHandler.post { interceptor.destroy() }
                        listener.onCreateWindow(tabId, url)
                    }
                    interceptor.webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(v: WebView, request: WebResourceRequest): Boolean {
                            capture(request.url.toString())
                            return true // never actually load in the interceptor
                        }

                        // Belt-and-braces: some engine paths start the child load without
                        // routing through shouldOverrideUrlLoading first.
                        override fun onPageStarted(v: WebView, url: String?, favicon: Bitmap?) {
                            capture(url)
                        }
                    }
                    popupInterceptors[tabId] = interceptor
                    transport.webView = interceptor
                    resultMsg.sendToTarget()
                    return true
                }

                // v4.8 File uploads: route <input type="file"> to the Activity's system picker.
                // Without this override no callback path exists and upload buttons on every
                // site silently do nothing.
                override fun onShowFileChooser(
                    view: WebView,
                    filePathCallback: ValueCallback<Array<Uri>>,
                    fileChooserParams: FileChooserParams,
                ): Boolean = listener.onShowFileChooser(filePathCallback, fileChooserParams)

                override fun onHideCustomView() {
                    // Engine-initiated hide (e.g. the page itself exited fullscreen, or
                    // navigated away). Mirrors exitFullscreen()'s bookkeeping but without
                    // re-invoking the callback, which the engine has already consumed.
                    if (customView == null) return
                    customView = null
                    customViewCallback = null
                    customViewTabId = null
                    listener.onFullscreenVideo(null)
                }
            }

            setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
                downloadFile(url, userAgent, contentDisposition, mimetype)
            }

            setFindListener { ordinal, total, done ->
                if (done) listener.onFindResult(tabId, if (total == 0) 0 else ordinal + 1, total)
            }

            // Auto-hiding command bar: forward page scrolls (delta-form) to the UI layer.
            setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
                listener.onPageScrolled(tabId, scrollY, scrollY - oldScrollY)
            }

            setOnLongClickListener { view ->
                val hit = (view as WebView).hitTestResult
                val url = hit.extra
                when (hit.type) {
                    WebView.HitTestResult.SRC_ANCHOR_TYPE -> {
                        if (url != null) listener.onLongPress(tabId, url, isImage = false)
                        url != null
                    }
                    WebView.HitTestResult.IMAGE_TYPE,
                    WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
                        if (url != null) listener.onLongPress(tabId, url, isImage = true)
                        url != null
                    }
                    // false = don't consume; text selection etc. keep working
                    else -> false
                }
            }
        }
    }

    /**
     * Launches an external app for a page link (v4.9). [confirm] routes the launch through the
     * permission prompt first (gesture-less navigations and everything in incognito — see
     * [ExternalLinks.needsConfirm]); a gesture-backed tap in a normal tab launches directly,
     * Chrome-style. [onNotFound] runs when no installed app can handle the intent (intent://
     * uses it for the page's fallback URL).
     */
    private fun launchExternalApp(
        tabId: Long,
        confirm: Boolean,
        incognito: Boolean,
        intent: Intent,
        onNotFound: () -> Unit,
    ) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val launch = {
            try {
                context.startActivity(intent)
            } catch (_: RuntimeException) {
                // ActivityNotFoundException, but also SecurityException / FileUriExposedException
                // etc. — a page-controlled intent must degrade to the fallback, never crash us.
                onNotFound()
            }
        }
        if (confirm) {
            listener.onPermissionRequest(
                PermissionRequestInfo(
                    host = pageHosts[tabId].orEmpty().ifBlank { "This page" },
                    label = if (incognito) "open another app (this leaves incognito)" else "open another app",
                    grant = launch,
                    deny = {},
                )
            )
        } else {
            launch()
        }
    }

    /**
     * intent:// dispatch (v4.9): the page-supplied URI is parsed, then every field is
     * constrained by [IntentHardening] (data scheme re-validated, action forced to VIEW,
     * component nulled, BROWSABLE, flags replaced, extras cleared, self-package de-targeted).
     * When no app matches, the page's declared browser_fallback_url loads in-page — validated
     * http(s)-only AND still subject to HTTPS-Only mode (loadUrl bypasses
     * shouldOverrideUrlLoading, so the upgrade must be applied here).
     */
    private fun openIntentUri(tabId: Long, confirm: Boolean, incognito: Boolean, url: String, view: WebView) {
        val parsed = runCatching { Intent.parseUri(url, Intent.URI_INTENT_SCHEME) }.getOrNull() ?: return
        // Read before hardening — harden() clears all extras.
        val fallback = ExternalLinks.safeFallbackUrl(parsed.getStringExtra("browser_fallback_url"))
        val hardened = IntentHardening.harden(parsed, context.packageName) ?: return
        launchExternalApp(tabId, confirm, incognito, hardened) {
            if (fallback != null) {
                val upgraded = if (HttpsUpgrade.shouldUpgrade(fallback, httpsOnly)) {
                    HttpsUpgrade.upgrade(fallback) ?: fallback
                } else {
                    fallback
                }
                view.loadUrl(upgraded, gpcHeaders())
            } else {
                toastNoApp()
            }
        }
    }

    private fun toastNoApp() {
        Toast.makeText(context, "No app can open this link", Toast.LENGTH_SHORT).show()
    }

    /** Hands a file to the system DownloadManager (notification + Downloads folder). */
    fun downloadFile(
        url: String,
        userAgent: String? = null,
        contentDisposition: String? = null,
        mimetype: String? = null,
    ) {
        // DownloadManager only accepts http/https; JS-generated
        // blob:/data: urls would throw and crash the app.
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            Toast.makeText(context, "This site uses a download type Browse can't save yet", Toast.LENGTH_SHORT).show()
            return
        }
        val fileName = URLUtil.guessFileName(url, contentDisposition, mimetype)
        if (!useSystemDownloader) {
            listener.onDownloadRequested(url, fileName, mimetype, userAgent)
            return
        }
        val request = DownloadManager.Request(Uri.parse(url)).apply {
            setMimeType(mimetype)
            addRequestHeader("Cookie", CookieManager.getInstance().getCookie(url))
            userAgent?.let { addRequestHeader("User-Agent", it) }
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            setTitle(fileName)
        }
        val downloadId = (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
        listener.onDownloadStarted(downloadId, fileName, url)
        Toast.makeText(context, "Downloading $fileName", Toast.LENGTH_SHORT).show()
    }

    fun close(tabId: Long) {
        // If this tab owns the current fullscreen custom view, tear that down first —
        // destroying the WebView underneath it would leave the UI stuck fullscreen with a
        // leaked view whose engine callback can never be resolved.
        if (customViewTabId == tabId) exitFullscreen()
        pageHosts.remove(tabId)
        thumbnails.remove(tabId)
        desktopTabs.remove(tabId)
        appliedDesktop.remove(tabId)
        // The WebView is about to be destroyed — its pending Safe Browsing callback (if any)
        // must never be invoked afterwards, so just drop it.
        safeBrowsingResponses.remove(tabId)
        popupInterceptors.remove(tabId)?.destroy()
        webViews.remove(tabId)?.destroy()
    }

    fun destroyAll() {
        popupInterceptors.values.forEach { it.destroy() }
        popupInterceptors.clear()
        webViews.values.forEach { it.destroy() }
        webViews.clear()
    }

    private companion object {
        const val DESKTOP_UA =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

        /** Forces a desktop-width viewport so sites can't pin us to a mobile layout via their
         *  own `width=device-width` meta. Paired with the desktop UA + wide-viewport settings. */
        const val DESKTOP_VIEWPORT_JS =
            "(function(){try{var m=document.querySelector('meta[name=viewport]');" +
                "if(!m){m=document.createElement('meta');m.setAttribute('name','viewport');" +
                "(document.head||document.documentElement).appendChild(m);}" +
                "m.setAttribute('content','width=1024');}catch(e){}})();"

        /**
         * Reads the page's OWN declared icons and returns the highest-quality one's absolute URL
         * (v4.1 favicon fix). Order: an SVG icon (infinitely scalable) wins outright; otherwise the
         * largest `sizes=` wins (apple-touch-icon is scored 180 when unsized). Falls back to
         * `/favicon.ico`. This is source-direct — it only reads what the site itself linked, never a
         * third-party proxy — and gives crisp tiles instead of the 16px favicon WebView hands back.
         */
        const val BEST_ICON_JS =
            "(function(){try{" +
                "function abs(u){try{return new URL(u,document.baseURI).href}catch(e){return null}}" +
                "var best=null,score=-1;" +
                "var links=document.querySelectorAll('link[rel~=\"icon\"],link[rel=\"apple-touch-icon\"],link[rel=\"apple-touch-icon-precomposed\"]');" +
                "for(var i=0;i<links.length;i++){var l=links[i];var href=l.getAttribute('href');if(!href)continue;" +
                "var type=(l.getAttribute('type')||'').toLowerCase();var rel=(l.getAttribute('rel')||'').toLowerCase();" +
                "var sizes=(l.getAttribute('sizes')||'').toLowerCase();var s=0;" +
                "if(type.indexOf('svg')>=0||href.toLowerCase().indexOf('.svg')>=0){s=100000}" +
                "else{var m=sizes.match(/(\\d+)x(\\d+)/);if(m){s=parseInt(m[1],10)}" +
                "else if(rel.indexOf('apple-touch')>=0){s=180}else{s=32}}" +
                "if(s>score){score=s;best=abs(href)}}" +
                "return best||abs('/favicon.ico')" +
                "}catch(e){return null}})();"

        /** Exposes `navigator.globalPrivacyControl === true` to page scripts (D5). */
        const val GPC_SHIM =
            "try{Object.defineProperty(Navigator.prototype,'globalPrivacyControl'," +
                "{get:function(){return true},configurable:true})}catch(e){}"

        /** User-facing label for a [WebViewClient] SAFE_BROWSING_THREAT_* code. */
        fun threatLabel(threatType: Int): String = when (threatType) {
            WebViewClient.SAFE_BROWSING_THREAT_MALWARE -> "Malware"
            WebViewClient.SAFE_BROWSING_THREAT_PHISHING -> "Phishing"
            WebViewClient.SAFE_BROWSING_THREAT_UNWANTED_SOFTWARE -> "Unwanted software"
            WebViewClient.SAFE_BROWSING_THREAT_BILLING -> "Billing fraud"
            else -> "Dangerous site"
        }
    }
}
