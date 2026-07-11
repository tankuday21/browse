package com.udaytank.browse.ui

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast

/**
 * Owns one live WebView per tab, outside the compose tree, so instances
 * (and their page state) survive navigation and recomposition.
 */
class WebViewHolder(
    private val context: Context,
    private val listener: Listener,
) {
    interface Listener {
        fun onPageStarted(tabId: Long, url: String)
        fun onProgressChanged(tabId: Long, percent: Int)
        fun onPageFinished(tabId: Long, url: String, title: String?)
        fun onHistoryChanged(tabId: Long, canGoBack: Boolean, canGoForward: Boolean)
    }

    private val webViews = mutableMapOf<Long, WebView>()

    @SuppressLint("SetJavaScriptEnabled")
    fun obtain(tabId: Long): WebView = webViews.getOrPut(tabId) {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true

            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                    listener.onPageStarted(tabId, url)
                }

                override fun onPageFinished(view: WebView, url: String) {
                    listener.onPageFinished(tabId, url, view.title)
                }

                override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
                    listener.onHistoryChanged(tabId, view.canGoBack(), view.canGoForward())
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView, newProgress: Int) {
                    listener.onProgressChanged(tabId, newProgress)
                }
            }

            setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
                // DownloadManager only accepts http/https; JS-generated
                // blob:/data: urls would throw and crash the app.
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    Toast.makeText(context, "This site uses a download type Browse can't save yet", Toast.LENGTH_SHORT).show()
                    return@setDownloadListener
                }
                val fileName = URLUtil.guessFileName(url, contentDisposition, mimetype)
                val request = DownloadManager.Request(Uri.parse(url)).apply {
                    setMimeType(mimetype)
                    addRequestHeader("Cookie", CookieManager.getInstance().getCookie(url))
                    addRequestHeader("User-Agent", userAgent)
                    setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                    setTitle(fileName)
                }
                (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
                Toast.makeText(context, "Downloading $fileName", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun close(tabId: Long) {
        webViews.remove(tabId)?.destroy()
    }

    fun destroyAll() {
        webViews.values.forEach { it.destroy() }
        webViews.clear()
    }
}
