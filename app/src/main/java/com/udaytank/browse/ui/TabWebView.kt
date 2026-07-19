package com.udaytank.browse.ui

import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.udaytank.browse.browser.BrowserCommand

@Composable
fun TabWebView(
    holder: WebViewHolder,
    tabId: Long,
    tabUrl: String,
    incognito: Boolean,
    isLoading: Boolean,
    pendingCommand: BrowserCommand?,
    onCommandConsumed: () -> Unit,
    modifier: Modifier = Modifier,
    profileKey: String? = null,
) {
    key(tabId) {
        AndroidView(
            modifier = modifier,
            factory = { context ->
                val webView = holder.obtain(tabId, incognito, profileKey).also { wv ->
                    (wv.parent as? ViewGroup)?.removeView(wv)
                    // Through the holder so app-issued loads carry the Sec-GPC header (D5).
                    // Blank tabUrl (v5.6 popup rows — the ENGINE drives their first load via
                    // the WebViewTransport) must not trigger a load that would clobber it.
                    if (wv.url == null && tabUrl.isNotBlank()) holder.loadUrl(tabId, tabUrl)
                }
                SwipeRefreshLayout(context).apply {
                    addView(
                        webView,
                        ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        ),
                    )
                    setOnRefreshListener { webView.reload() }
                    // Only trigger at the very top of the page.
                    setOnChildScrollUpCallback { _, _ -> webView.scrollY > 0 }
                }
            },
            update = { swipe ->
                val webView = holder.obtain(tabId, incognito, profileKey)
                when (pendingCommand) {
                    is BrowserCommand.LoadUrl -> holder.loadUrl(tabId, pendingCommand.url)
                    BrowserCommand.GoBack -> if (webView.canGoBack()) webView.goBack()
                    BrowserCommand.GoForward -> if (webView.canGoForward()) webView.goForward()
                    BrowserCommand.Reload -> webView.reload()
                    null -> Unit
                }
                if (pendingCommand != null) onCommandConsumed()
                if (!isLoading) swipe.isRefreshing = false
            },
        )
    }
}
