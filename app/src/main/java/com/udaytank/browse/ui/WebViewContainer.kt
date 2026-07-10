package com.udaytank.browse.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.udaytank.browse.browser.BrowserCommand

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewContainer(
    pendingCommand: BrowserCommand?,
    onCommandConsumed: () -> Unit,
    onPageStarted: (String) -> Unit,
    onProgressChanged: (Int) -> Unit,
    onPageFinished: () -> Unit,
    onHistoryChanged: (canGoBack: Boolean, canGoForward: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true

                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                        onPageStarted(url)
                    }

                    override fun onPageFinished(view: WebView, url: String) {
                        onPageFinished()
                    }

                    override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
                        onHistoryChanged(view.canGoBack(), view.canGoForward())
                    }
                }

                webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView, newProgress: Int) {
                        onProgressChanged(newProgress)
                    }
                }
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
