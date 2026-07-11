package com.udaytank.browse.ui

import android.webkit.WebView
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import com.udaytank.browse.browser.ReaderMode
import org.json.JSONObject

/**
 * A distraction-free reader: extracts the active tab's article and renders
 * it in its own WebView from a data URL. Falls back to a friendly message
 * when a page has no readable article.
 */
@Composable
fun ReaderOverlay(
    holder: WebViewHolder,
    tabId: Long,
    dark: Boolean = isSystemInDarkTheme(),
    background: Color,
    onText: Color,
) {
    var html by remember(tabId) { mutableStateOf<String?>(null) }

    if (html == null) {
        holder.extractReaderContent(tabId) { json ->
            html = runCatching {
                // evaluateJavascript hands back a JSON *string literal*; unwrap it.
                val unwrapped = JSONObject("{\"v\":$json}").getString("v")
                val obj = JSONObject(unwrapped)
                if (obj.optBoolean("ok")) {
                    ReaderMode.buildReaderHtml(obj.optString("title"), obj.optString("content"), dark)
                } else {
                    ReaderMode.buildReaderHtml(
                        "No article found",
                        "<p>This page doesn't have a readable article. Tap reader again to return.</p>",
                        dark,
                    )
                }
            }.getOrElse {
                ReaderMode.buildReaderHtml("Reader unavailable", "<p>Could not read this page.</p>", dark)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        val content = html
        if (content != null) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        setBackgroundColor(background.toArgb())
                        settings.javaScriptEnabled = false
                        loadDataWithBaseURL(null, content, "text/html", "utf-8", null)
                    }
                },
                update = { it.loadDataWithBaseURL(null, content, "text/html", "utf-8", null) },
            )
        }
    }
}
