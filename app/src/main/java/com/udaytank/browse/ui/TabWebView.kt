package com.udaytank.browse.ui

import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.udaytank.browse.browser.BrowserCommand

@Composable
fun TabWebView(
    holder: WebViewHolder,
    tabId: Long,
    tabUrl: String,
    pendingCommand: BrowserCommand?,
    onCommandConsumed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    key(tabId) {
        AndroidView(
            modifier = modifier,
            factory = {
                holder.obtain(tabId).also { webView ->
                    (webView.parent as? ViewGroup)?.removeView(webView)
                    if (webView.url == null) webView.loadUrl(tabUrl)
                }
            },
            update = { webView ->
                when (pendingCommand) {
                    is BrowserCommand.LoadUrl -> webView.loadUrl(pendingCommand.url)
                    BrowserCommand.GoBack -> if (webView.canGoBack()) webView.goBack()
                    BrowserCommand.GoForward -> if (webView.canGoForward()) webView.goForward()
                    BrowserCommand.Reload -> webView.reload()
                    null -> Unit
                }
                if (pendingCommand != null) onCommandConsumed()
            },
        )
    }
}
