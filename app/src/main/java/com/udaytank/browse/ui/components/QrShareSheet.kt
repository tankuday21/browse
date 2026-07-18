package com.udaytank.browse.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.google.zxing.common.BitMatrix
import com.udaytank.browse.browser.QrGenerate
import com.udaytank.browse.ui.theme.OrbitSpacing
import com.udaytank.browse.ui.theme.orbit
import com.udaytank.browse.ui.theme.orbitBody
import com.udaytank.browse.ui.theme.orbitCaption
import com.udaytank.browse.ui.theme.orbitTitle
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * "Share page as QR" (v5.4): renders the page URL as a QR another phone scans off the screen.
 * The code sits on a WHITE card regardless of theme — scanners need dark-on-light + quiet zone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrShareSheet(url: String, title: String?, onDismiss: () -> Unit) {
    val scheme = orbit()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // size = 1 yields the MINIMAL matrix (one entry per module) — the Canvas scales it up, and
    // drawing ~33x33 module rects beats iterating a pre-scaled 512x512 matrix by ~250x.
    val matrix = remember(url) { QrGenerate.encode(url, size = 1) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = scheme.surfaces.elevated) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = OrbitSpacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(OrbitSpacing.md),
        ) {
            Text("Share page as QR", style = orbitTitle, color = scheme.text.primary)
            if (matrix != null) {
                Surface(color = Color.White, shape = RoundedCornerShape(OrbitSpacing.lg)) {
                    Canvas(modifier = Modifier.padding(OrbitSpacing.lg).size(232.dp)) {
                        val cell = size.width / matrix.width
                        // Cells are ceil-oversized: fractional cell edges anti-alias against
                        // the white ground independently, leaving hairline seams through black
                        // runs — overlap is safe (all modules the same color), gaps are not.
                        val cellSize = Size(kotlin.math.ceil(cell), kotlin.math.ceil(cell))
                        for (y in 0 until matrix.height) {
                            for (x in 0 until matrix.width) {
                                if (matrix.get(x, y)) {
                                    drawRect(
                                        Color.Black,
                                        topLeft = Offset(x * cell, y * cell),
                                        size = cellSize,
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                Text(
                    "This address is too long for a QR code",
                    style = orbitBody,
                    color = scheme.text.muted,
                    textAlign = TextAlign.Center,
                )
            }
            title?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = orbitBody, color = scheme.text.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(url, style = orbitCaption, color = scheme.text.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(OrbitSpacing.sm)) {
                TextButton(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("url", url))
                }) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Copy link", modifier = Modifier.padding(start = OrbitSpacing.xs))
                }
                TextButton(
                    onClick = { scope.launch { shareQrPng(context, url) } },
                    enabled = matrix != null,
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Share image", modifier = Modifier.padding(start = OrbitSpacing.xs))
                }
            }
            Spacer(Modifier.height(OrbitSpacing.xl))
        }
    }
}

/** Scales the module matrix to ~[targetSize] px, black on white. */
private fun renderBitmap(matrix: BitMatrix, targetSize: Int = 512): android.graphics.Bitmap {
    val scale = maxOf(1, targetSize / matrix.width)
    val w = matrix.width * scale
    val h = matrix.height * scale
    val pixels = IntArray(w * h)
    for (y in 0 until h) {
        for (x in 0 until w) {
            pixels[y * w + x] = if (matrix.get(x / scale, y / scale)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        }
    }
    return android.graphics.Bitmap.createBitmap(pixels, w, h, android.graphics.Bitmap.Config.ARGB_8888)
}

/**
 * Writes the QR as a PNG into cacheDir/qr/ (one fixed name, overwritten per share — nothing
 * accumulates; Black Hole deletes the directory) and hands it to the system share sheet via
 * the existing FileProvider. Render + compress run off the main thread; failures toast rather
 * than silently doing nothing. The exported image is re-encoded with the spec's 4-module quiet
 * zone — unlike the on-screen code, it has no white card around it.
 */
private suspend fun shareQrPng(context: Context, url: String) {
    val send = withContext(Dispatchers.Default) {
        runCatching {
            val matrix = QrGenerate.encode(url, size = 1, margin = 4) ?: error("encode failed")
            val dir = File(context.cacheDir, "qr").apply { mkdirs() }
            val file = File(dir, "share-qr.png")
            file.outputStream().use {
                renderBitmap(matrix).compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
            Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, url)
                // Explicit ClipData, not just grant flags — the platform's EXTRA_STREAM migration
                // is what usually saves ACTION_SEND, but being explicit costs nothing (v5.3 lesson).
                clipData = ClipData.newRawUri(null, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }.getOrNull()
    }
    if (send == null) {
        android.widget.Toast.makeText(context, "Couldn't create the QR image", android.widget.Toast.LENGTH_SHORT).show()
        return
    }
    runCatching { context.startActivity(Intent.createChooser(send, "Share QR code")) }
}
