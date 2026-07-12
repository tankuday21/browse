package com.udaytank.browse.ui

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.os.Environment
import android.webkit.CookieManager
import android.webkit.SafeBrowsingResponse
import android.webkit.SslErrorHandler
import android.webkit.URLUtil
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
import com.udaytank.browse.browser.HttpsUpgrade
import com.udaytank.browse.browser.ReaderMode
import com.udaytank.browse.browser.SiteSettingsResolver
import com.udaytank.browse.browser.UrlHosts
import com.udaytank.browse.data.SiteSettingsEntity
import android.widget.Toast
import com.udaytank.browse.browser.AdBlockEngine
import com.udaytank.browse.browser.adblock.RequestTyping
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
    }

    private val webViews = mutableMapOf<Long, WebView>()
    private var jsEnabled = true
    private var safeBrowsingEnabled = true

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
     * Tabs exempted from the background-media-playback opt-in from being paused when the app
     * backgrounds. Nothing in this holder currently pauses WebViews on its own (Activity lifecycle
     * changes don't call WebView.onPause/onResume anywhere in this codebase) - this set exists so
     * MainActivity's background-media wiring (see MainActivity.onStop) has a place to record which
     * tab must keep running, and any future pause-all path added here can consult it.
     */
    private val keepAliveTabs = mutableSetOf<Long>()

    fun setKeepAlive(tabId: Long, keep: Boolean) {
        if (keep) keepAliveTabs.add(tabId) else keepAliveTabs.remove(tabId)
    }

    fun isKeptAlive(tabId: Long): Boolean = tabId in keepAliveTabs

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

    /** Clears cache, cookies, and web storage. History/tabs are the data layer's job. */
    fun clearBrowsingData() {
        webViews.values.forEach { it.clearCache(true) }
        CookieManager.getInstance().removeAllCookies(null)
        WebStorage.getInstance().deleteAllData()
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun obtain(tabId: Long, incognito: Boolean = false): WebView = webViews.getOrPut(tabId) {
        WebView(context).apply {
            settings.javaScriptEnabled = jsEnabled
            applySafeBrowsing(this)
            // Pinch zoom always available (I3); the legacy on-screen +/- controls stay hidden.
            settings.setSupportZoom(true)
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            // Global text scale baseline; onPageStarted re-resolves per site (override wins).
            settings.textZoom = globalTextScale
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
            }

            // GPC JS shim (D5): registered once per WebView at creation, before any page loads.
            val gpcShimAtDocumentStart = gpcEnabled &&
                WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
            if (gpcShimAtDocumentStart) {
                runCatching {
                    androidx.webkit.WebViewCompat.addDocumentStartJavaScript(this, GPC_SHIM, setOf("*"))
                }
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
                    if (HttpsUpgrade.shouldUpgrade(target, httpsOnly)) {
                        HttpsUpgrade.upgrade(target)?.let { view.loadUrl(it, gpcHeaders()); return true }
                    }
                    return false
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
        webViews.remove(tabId)?.destroy()
    }

    fun destroyAll() {
        webViews.values.forEach { it.destroy() }
        webViews.clear()
    }

    private companion object {
        const val DESKTOP_UA =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

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
