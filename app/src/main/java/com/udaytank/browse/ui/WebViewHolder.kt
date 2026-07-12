package com.udaytank.browse.ui

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Environment
import android.webkit.CookieManager
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
import android.widget.Toast
import com.udaytank.browse.browser.AdBlockEngine
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
) {
    interface Listener {
        fun onPageStarted(tabId: Long, url: String)
        fun onProgressChanged(tabId: Long, percent: Int)
        fun onPageFinished(tabId: Long, url: String, title: String?)
        fun onHistoryChanged(tabId: Long, canGoBack: Boolean, canGoForward: Boolean)
        fun onSslError(tabId: Long, url: String)
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

    /** Hosts the user has granted a given permission for this session. */
    private val grantedPermissions = HashSet<String>()

    // Fullscreen video (HTML5 <video> fullscreen / WebChromeClient custom view). Only one tab
    // can be in fullscreen at a time, so this lives on the holder rather than per-WebView.
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null

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

    fun setDesktopMode(tabId: Long, desktop: Boolean) {
        val webView = webViews[tabId] ?: return
        webView.settings.userAgentString = if (desktop) DESKTOP_UA else null
        webView.settings.loadWithOverviewMode = desktop
        webView.settings.useWideViewPort = desktop
        webView.reload()
    }

    // shouldInterceptRequest runs on WebView's background threads;
    // page hosts are written on the UI thread in onPageStarted.
    private val pageHosts = ConcurrentHashMap<Long, String>()

    /** Applies global browsing policy to all live WebViews and future ones. */
    fun applyPolicy(javaScriptEnabled: Boolean, cookiesEnabled: Boolean) {
        jsEnabled = javaScriptEnabled
        webViews.values.forEach { it.settings.javaScriptEnabled = javaScriptEnabled }
        CookieManager.getInstance().setAcceptCookie(cookiesEnabled)
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
            if (forceDark && WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, true)
            }
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

            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                    Uri.parse(url).host?.let { pageHosts[tabId] = it }
                    listener.onPageStarted(tabId, url)
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest,
                ): Boolean {
                    val target = request.url.toString()
                    if (HttpsUpgrade.shouldUpgrade(target, httpsOnly)) {
                        HttpsUpgrade.upgrade(target)?.let { view.loadUrl(it); return true }
                    }
                    return false
                }

                override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest,
                ): WebResourceResponse? {
                    return if (adBlock.shouldBlock(request.url.host, pageHosts[tabId])) {
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
                    // Chrome-style: if a custom view is already showing (rare - e.g. the page
                    // swapped fullscreen elements without hiding the first one), hide it first.
                    if (customView != null) {
                        customViewCallback?.onCustomViewHidden()
                    }
                    customView = view
                    customViewCallback = callback
                    listener.onFullscreenVideo(view)
                }

                override fun onHideCustomView() {
                    // Engine-initiated hide (e.g. the page itself exited fullscreen, or
                    // navigated away). Mirrors exitFullscreen()'s bookkeeping but without
                    // re-invoking the callback, which the engine has already consumed.
                    if (customView == null) return
                    customView = null
                    customViewCallback = null
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
        pageHosts.remove(tabId)
        thumbnails.remove(tabId)
        webViews.remove(tabId)?.destroy()
    }

    fun destroyAll() {
        webViews.values.forEach { it.destroy() }
        webViews.clear()
    }

    private companion object {
        const val DESKTOP_UA =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
    }
}
