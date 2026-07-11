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
) {
    key(tabId) {
        AndroidView(
            modifier = modifier,
            factory = { context ->
                val webView = holder.obtain(tabId, incognito).also { wv ->
                    (wv.parent as? ViewGroup)?.removeView(wv)
                    if (wv.url == null) wv.loadUrl(tabUrl)
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
                val webView = holder.obtain(tabId, incognito)
                when (pendingCommand) {
                    is BrowserCommand.LoadUrl -> webView.loadUrl(pendingCommand.url)
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
