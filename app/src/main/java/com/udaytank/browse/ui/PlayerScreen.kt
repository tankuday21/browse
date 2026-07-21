package com.udaytank.browse.ui

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.PlayerView
import com.udaytank.browse.BrowserViewModel
import com.udaytank.browse.media.AndromedaPlayerService
import com.udaytank.browse.media.PlayerItem
import com.udaytank.browse.media.PlayerQueue
import kotlinx.coroutines.delay
import java.io.File
import java.util.Locale

private val SPEED_PRESETS = listOf(0.5f, 1f, 1.25f, 1.5f, 2f)

/** mm:ss (or h:mm:ss) for the scrubber. */
private fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%d:%02d", m, s)
}

/**
 * The Andromeda Player (v6.0): a custom Orbit-themed UI over a MediaController bound to
 * [AndromedaPlayerService]. The service owns the single ExoPlayer (so audio survives
 * backgrounding); this screen only drives and reflects it.
 */
@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    viewModel: BrowserViewModel,
    downloadId: Long,
    onBack: () -> Unit,
    onPipAspect: (android.util.Rational?) -> Unit = {},
) {
    val context = LocalContext.current

    var controller by remember { mutableStateOf<MediaController?>(null) }
    var queue by remember { mutableStateOf<PlayerQueue?>(null) }
    var unavailable by remember { mutableStateOf(false) }
    var started by remember { mutableStateOf(false) }

    // Resolve the queue (off the main thread in the VM) once for this download.
    LaunchedEffect(downloadId) {
        val resolved = viewModel.resolvePlayerQueue(downloadId)
        if (resolved == null) unavailable = true else queue = resolved
    }

    // Bind a MediaController to the player service; released on leave.
    DisposableEffect(Unit) {
        val token = SessionToken(context, ComponentName(context, AndromedaPlayerService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            controller = runCatching { future.get() }.getOrNull()
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            MediaController.releaseFuture(future)
            controller = null
        }
    }

    // Load the queue into the player exactly once, once both controller and queue are ready.
    LaunchedEffect(controller, queue) {
        val c = controller ?: return@LaunchedEffect
        val q = queue ?: return@LaunchedEffect
        if (started) return@LaunchedEffect
        started = true
        val items = q.items.map { it.toMediaItem() }
        c.setMediaItems(items, q.startIndex, q.startPositionMs)
        c.prepare()
        c.play()
    }

    if (unavailable) {
        PlayerUnavailable(onBack)
        return
    }

    PlayerContent(
        controller = controller,
        currentTitle = queue?.items?.getOrNull(controller?.currentMediaItemIndex ?: 0)?.title.orEmpty(),
        onBack = onBack,
        onOpenExternal = {
            queue?.items?.getOrNull(controller?.currentMediaItemIndex ?: 0)
                ?.let { openExternally(context, it) }
        },
        onPipAspect = onPipAspect,
    )
}

private fun PlayerItem.toMediaItem(): MediaItem =
    MediaItem.Builder()
        .setUri(Uri.fromFile(File(filePath)))
        .setMediaId(filePath)
        .setMediaMetadata(MediaMetadata.Builder().setTitle(title).build())
        .build()

@OptIn(UnstableApi::class)
@Composable
private fun PlayerContent(
    controller: MediaController?,
    currentTitle: String,
    onBack: () -> Unit,
    onOpenExternal: () -> Unit,
    onPipAspect: (android.util.Rational?) -> Unit,
) {
    val context = LocalContext.current

    var positionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }
    var isPlaying by remember { mutableStateOf(false) }
    var speed by remember { mutableStateOf(1f) }
    var muted by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }
    var scrubbing by remember { mutableStateOf(false) }
    var scrubPos by remember { mutableStateOf(0L) }
    var showTracks by remember { mutableStateOf(false) }
    var hasVideo by remember { mutableStateOf(false) }

    // Poll the controller for the scrubber/state (cheap; only while the screen is composed).
    LaunchedEffect(controller) {
        val c = controller ?: return@LaunchedEffect
        while (true) {
            if (!scrubbing) positionMs = c.currentPosition
            durationMs = c.duration.takeIf { it != C.TIME_UNSET } ?: 0L
            isPlaying = c.isPlaying
            hasVideo = c.currentTracks.groups.any { it.type == C.TRACK_TYPE_VIDEO }
            // PiP is offered only for a playing video with a real size; a bad size would make
            // enterPictureInPictureMode throw, so guard it here (the Activity also runCatch-es).
            val vs = c.videoSize
            onPipAspect(
                if (hasVideo && isPlaying && vs.width > 0 && vs.height > 0)
                    android.util.Rational(vs.width, vs.height)
                else null
            )
            delay(500)
        }
    }
    // The player screen is gone — never enter PiP for it after leaving.
    DisposableEffect(Unit) { onDispose { onPipAspect(null) } }

    // Auto-hide controls a few seconds after they appear while playing.
    LaunchedEffect(controlsVisible, isPlaying) {
        if (controlsVisible && isPlaying) {
            delay(3500)
            controlsVisible = false
        }
    }

    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val activity = remember(context) { context.findActivity() }

    // Restore the window brightness to system-default when leaving the player.
    DisposableEffect(Unit) {
        onDispose {
            activity?.window?.let { w ->
                val lp = w.attributes
                lp.screenBrightness = -1f // WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                w.attributes = lp
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            // Tap toggles the overlay; double-tap left/right seeks ∓10s.
            .pointerInput(controller) {
                detectTapGestures(
                    onTap = { controlsVisible = !controlsVisible },
                    onDoubleTap = { offset ->
                        controller?.let { c ->
                            if (offset.x < size.width / 2f) c.seekTo((c.currentPosition - 10_000).coerceAtLeast(0))
                            else c.seekTo(c.currentPosition + 10_000)
                        }
                    },
                )
            }
            // Vertical drag: left half = brightness, right half = media volume.
            .pointerInput(Unit) {
                var onLeft = true
                detectVerticalDragGestures(
                    onDragStart = { offset -> onLeft = offset.x < size.width / 2f },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        val delta = -dragAmount / size.height // up = increase
                        if (onLeft) {
                            activity?.window?.let { w ->
                                val lp = w.attributes
                                val current = if (lp.screenBrightness < 0f) 0.5f else lp.screenBrightness
                                lp.screenBrightness = (current + delta).coerceIn(0.01f, 1f)
                                w.attributes = lp
                            }
                        } else {
                            val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                            val cur = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                            val next = (cur + (delta * max * 1.5f)).toInt().coerceIn(0, max)
                            if (next != cur) audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, next, 0)
                        }
                    },
                )
            },
    ) {
        // Video surface (also fine for audio — shown behind a music glyph).
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    setBackgroundColor(android.graphics.Color.BLACK)
                }
            },
            update = { it.player = controller },
            modifier = Modifier.fillMaxSize(),
        )
        if (!hasVideo) {
            Icon(
                Icons.Filled.MusicNote,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.35f),
                modifier = Modifier.align(Alignment.Center).size(96.dp),
            )
        }

        if (controlsVisible) {
            PlayerControls(
                title = currentTitle,
                positionMs = if (scrubbing) scrubPos else positionMs,
                durationMs = durationMs,
                isPlaying = isPlaying,
                speed = speed,
                muted = muted,
                onBack = onBack,
                onOpenExternal = onOpenExternal,
                onShowTracks = { showTracks = true },
                onPlayPause = { controller?.let { if (it.isPlaying) it.pause() else it.play() } },
                onPrev = { controller?.seekToPreviousMediaItem() },
                onNext = { controller?.seekToNextMediaItem() },
                onSeekBack = { controller?.let { it.seekTo((it.currentPosition - 10_000).coerceAtLeast(0)) } },
                onSeekForward = { controller?.let { it.seekTo(it.currentPosition + 10_000) } },
                onScrubStart = { scrubbing = true; scrubPos = positionMs },
                onScrub = { scrubPos = it },
                onScrubEnd = { controller?.seekTo(it); scrubbing = false },
                onCycleSpeed = {
                    val next = SPEED_PRESETS[(SPEED_PRESETS.indexOf(speed) + 1) % SPEED_PRESETS.size]
                    speed = next
                    controller?.setPlaybackSpeed(next)
                },
                onToggleMute = {
                    muted = !muted
                    controller?.volume = if (muted) 0f else 1f
                },
            )
        }
    }

    if (showTracks) {
        TrackSelectionDialog(controller = controller, onDismiss = { showTracks = false })
    }
}

@Composable
private fun PlayerControls(
    title: String,
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    speed: Float,
    muted: Boolean,
    onBack: () -> Unit,
    onOpenExternal: () -> Unit,
    onShowTracks: () -> Unit,
    onPlayPause: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onScrubStart: () -> Unit,
    onScrub: (Long) -> Unit,
    onScrubEnd: (Long) -> Unit,
    onCycleSpeed: () -> Unit,
    onToggleMute: () -> Unit,
) {
    val scrim = Color.Black.copy(alpha = 0.45f)
    Box(modifier = Modifier.fillMaxSize().background(scrim)) {
        // Top row: back, title, track selection, open externally.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().align(Alignment.TopStart).padding(8.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text(
                title,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            IconButton(onClick = onShowTracks) {
                Icon(Icons.Filled.Subtitles, contentDescription = "Audio & subtitle tracks", tint = Color.White)
            }
            IconButton(onClick = onOpenExternal) {
                Icon(Icons.Filled.OpenInNew, contentDescription = "Open with external player", tint = Color.White)
            }
        }

        // Center transport: prev, -10, play/pause, +10, next.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.align(Alignment.Center),
        ) {
            IconButton(onClick = onPrev) {
                Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous", tint = Color.White, modifier = Modifier.size(36.dp))
            }
            IconButton(onClick = onSeekBack) {
                Icon(Icons.Filled.Replay10, contentDescription = "Back 10 seconds", tint = Color.White, modifier = Modifier.size(36.dp))
            }
            IconButton(onClick = onPlayPause) {
                Icon(
                    if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(56.dp),
                )
            }
            IconButton(onClick = onSeekForward) {
                Icon(Icons.Filled.Forward10, contentDescription = "Forward 10 seconds", tint = Color.White, modifier = Modifier.size(36.dp))
            }
            IconButton(onClick = onNext) {
                Icon(Icons.Filled.SkipNext, contentDescription = "Next", tint = Color.White, modifier = Modifier.size(36.dp))
            }
        }

        // Bottom: scrubber + times + speed + mute.
        Column(modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(12.dp)) {
            val max = durationMs.coerceAtLeast(1L).toFloat()
            Slider(
                value = positionMs.coerceIn(0, durationMs).toFloat(),
                onValueChange = { onScrubStart(); onScrub(it.toLong()) },
                onValueChangeFinished = { onScrubEnd(positionMs) },
                valueRange = 0f..max,
                colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White),
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(formatTime(positionMs), color = Color.White)
                Text(" / ${formatTime(durationMs)}", color = Color.White.copy(alpha = 0.7f))
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onCycleSpeed) {
                    Text("${speed}x", color = Color.White)
                }
                IconButton(onClick = onToggleMute) {
                    Icon(
                        if (muted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                        contentDescription = if (muted) "Unmute" else "Mute",
                        tint = Color.White,
                    )
                }
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun TrackSelectionDialog(controller: MediaController?, onDismiss: () -> Unit) {
    val tracks = controller?.currentTracks ?: Tracks.EMPTY
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        title = { Text("Audio & subtitles") },
        text = {
            Column {
                TrackTypeSection("Audio", tracks, C.TRACK_TYPE_AUDIO, controller)
                Spacer(Modifier.size(8.dp))
                TrackTypeSection("Subtitles", tracks, C.TRACK_TYPE_TEXT, controller)
            }
        },
    )
}

@OptIn(UnstableApi::class)
@Composable
private fun TrackTypeSection(label: String, tracks: Tracks, type: Int, controller: MediaController?) {
    val groups = tracks.groups.filter { it.type == type }
    Text(label)
    if (groups.isEmpty()) {
        Text("None", color = Color.Gray)
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        groups.forEachIndexed { gi, group ->
            for (ti in 0 until group.length) {
                val selected = group.isTrackSelected(ti)
                val name = group.getTrackFormat(ti).label ?: group.getTrackFormat(ti).language ?: "Track ${gi + 1}.${ti + 1}"
                FilterChip(
                    selected = selected,
                    onClick = {
                        controller?.let { c ->
                            c.trackSelectionParameters = c.trackSelectionParameters
                                .buildUpon()
                                .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, ti))
                                .build()
                        }
                    },
                    label = { Text(name) },
                )
            }
        }
    }
}

@Composable
private fun PlayerUnavailable(onBack: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("This file can't be played", color = Color.White)
            TextButton(onClick = onBack) { Text("Back") }
        }
    }
}

/** Hands the current item off to another app's player (ACTION_VIEW chooser). */
private fun openExternally(context: Context, item: PlayerItem) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", File(item.filePath))
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, item.mimeType ?: "*/*")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(Intent.createChooser(intent, "Open with")) }
}

private fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is android.content.ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
