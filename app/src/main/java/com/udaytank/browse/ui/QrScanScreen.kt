package com.udaytank.browse.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.udaytank.browse.browser.QrPayload
import com.udaytank.browse.ui.theme.OrbitRadii
import com.udaytank.browse.ui.theme.OrbitSpacing
import com.udaytank.browse.ui.theme.orbit
import com.udaytank.browse.ui.theme.orbitBody
import com.udaytank.browse.ui.theme.orbitCaption
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * QR scanner (v5.2). CameraX preview + ZXing decoding, fully on-device — no network, no Play
 * Services. First decode wins: Web/App payloads leave the screen via [onOpenWeb]/[onOpenApp];
 * plain text stays here in a result card (copy / search / scan again).
 */
@Composable
fun QrScanScreen(
    onOpenWeb: (String) -> Unit,
    onOpenApp: (String) -> Unit,
    onSearch: (String) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var permissionAsked by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted; permissionAsked = true }
    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (hasPermission) {
            var textResult by remember { mutableStateOf<String?>(null) }
            val decoded = remember { AtomicBoolean(false) }
            val haptics = LocalHapticFeedback.current

            ScannerPreview(
                decoded = decoded,
                onDecoded = { raw ->
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    when (val payload = QrPayload.classify(raw)) {
                        is QrPayload.Payload.Web -> onOpenWeb(payload.url)
                        is QrPayload.Payload.App -> onOpenApp(payload.url)
                        is QrPayload.Payload.Text -> textResult = payload.raw
                    }
                },
            )

            // Scan-area frame: a soft rounded outline centered on screen.
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(240.dp)
                    .border(2.dp, Color.White.copy(alpha = 0.9f), RoundedCornerShape(OrbitSpacing.lg)),
            )

            textResult?.let { text ->
                QrTextResultCard(
                    text = text,
                    onCopy = {
                        val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                        clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("qr", text))
                    },
                    onSearch = { onSearch(text) },
                    onScanAgain = { textResult = null; decoded.set(false) },
                    modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding(),
                )
            }
        } else {
            // Denied (or not yet granted): explain + retry — never a dead black screen.
            Column(
                modifier = Modifier.fillMaxSize().padding(OrbitSpacing.xxl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    "Camera access is needed to scan QR codes",
                    style = orbitBody,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )
                if (permissionAsked) {
                    Button(
                        onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                        shape = RoundedCornerShape(OrbitRadii.pill),
                        modifier = Modifier.padding(top = OrbitSpacing.lg),
                    ) { Text("Grant camera access", style = orbitBody) }
                }
            }
        }

        IconButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(OrbitSpacing.sm),
        ) {
            Icon(Icons.Filled.Close, contentDescription = "Close scanner", tint = Color.White)
        }
    }
}

/** The camera preview + single-shot ZXing analyzer, isolated so torch state lives with it. */
@Composable
private fun ScannerPreview(decoded: AtomicBoolean, onDecoded: (String) -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var camera by remember { mutableStateOf<Camera?>(null) }
    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var torchOn by remember { mutableStateOf(false) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        onDispose {
            provider?.unbindAll()
            analysisExecutor.shutdown()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val future = ProcessCameraProvider.getInstance(ctx)
                future.addListener({
                    val cameraProvider = future.get()
                    provider = cameraProvider
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                    val reader = MultiFormatReader().apply {
                        setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)))
                    }
                    analysis.setAnalyzer(analysisExecutor) { proxy ->
                        proxy.use { image ->
                            if (decoded.get()) return@use
                            // ZXing wants a luminance plane; the Y plane of YUV_420_888 IS one.
                            // dataWidth must be the row stride (rows can be padded past width).
                            val yPlane = image.planes[0]
                            val bytes = ByteArray(yPlane.buffer.remaining()).also { yPlane.buffer.get(it) }
                            val source = PlanarYUVLuminanceSource(
                                bytes, yPlane.rowStride, image.height,
                                0, 0, image.width, image.height, false,
                            )
                            val text = try {
                                reader.decodeWithState(BinaryBitmap(HybridBinarizer(source))).text
                            } catch (_: NotFoundException) {
                                null
                            } finally {
                                reader.reset()
                            }
                            // First decode wins — compareAndSet guards the analyzer thread racing
                            // a re-arm from the UI ("Scan again").
                            if (text != null && decoded.compareAndSet(false, true)) {
                                previewView.post { onDecoded(text) }
                            }
                        }
                    }
                    cameraProvider.unbindAll()
                    camera = cameraProvider.bindToLifecycle(
                        lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis,
                    )
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            },
        )

        val hasTorch = camera?.cameraInfo?.hasFlashUnit() == true
        if (hasTorch) {
            IconButton(
                onClick = {
                    torchOn = !torchOn
                    camera?.cameraControl?.enableTorch(torchOn)
                },
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(OrbitSpacing.sm),
            ) {
                Icon(
                    if (torchOn) Icons.Filled.FlashOff else Icons.Filled.FlashOn,
                    contentDescription = if (torchOn) "Turn torch off" else "Turn torch on",
                    tint = Color.White,
                )
            }
        }
    }
}

@Composable
private fun QrTextResultCard(
    text: String,
    onCopy: () -> Unit,
    onSearch: () -> Unit,
    onScanAgain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = orbit()
    Surface(
        modifier = modifier.fillMaxWidth().padding(OrbitSpacing.lg),
        color = scheme.surfaces.elevated,
        shape = RoundedCornerShape(OrbitSpacing.lg),
        tonalElevation = 3.dp,
    ) {
        Column(modifier = Modifier.padding(OrbitSpacing.lg)) {
            Text("Scanned text", style = orbitCaption, color = scheme.text.muted)
            Text(
                text,
                style = orbitBody,
                color = scheme.text.primary,
                maxLines = 4,
                modifier = Modifier.padding(top = OrbitSpacing.xs),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = OrbitSpacing.md),
                horizontalArrangement = Arrangement.spacedBy(OrbitSpacing.sm),
            ) {
                TextButton(onClick = onCopy) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Copy", modifier = Modifier.padding(start = OrbitSpacing.xs))
                }
                TextButton(onClick = onSearch) {
                    Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Search", modifier = Modifier.padding(start = OrbitSpacing.xs))
                }
                Box(Modifier.weight(1f))
                TextButton(onClick = onScanAgain) { Text("Scan again") }
            }
        }
    }
}
