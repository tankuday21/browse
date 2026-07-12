package com.udaytank.browse.ui.game

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.udaytank.browse.browser.AsteroidPhysics
import com.udaytank.browse.browser.Rock
import com.udaytank.browse.browser.Ship
import kotlin.math.max
import kotlin.random.Random

// Orbit-flavored space palette (deliberately theme-independent: space is dark).
private val SpaceBlack = Color(0xFF060B1A)
private val ShipCyan = Color(0xFF35C3F3)
private val RockBody = Color(0xFF7A8699)
private val RockShade = Color(0xFF5A6577)

/** A background star at a normalized position with a per-star size/brightness factor. */
private data class Star(val fx: Float, val fy: Float, val twinkle: Float)

/**
 * The "Lost in space" asteroid game (K1), reachable from the connectivity-error page. All game
 * MATH lives in [AsteroidPhysics] (pure, unit-tested); this composable owns rendering (Canvas),
 * input (horizontal drag steers the ship), and the frame clock.
 *
 * Frame loop: a [LaunchedEffect] keyed on run/pause state accumulates dt via [withFrameNanos].
 * It pauses two ways: leaving the composition cancels it outright, and a [LifecycleEventObserver]
 * flips [resumed] on ON_PAUSE which cancels the keyed effect until ON_RESUME — so the game burns
 * zero battery while the app is backgrounded. dt is capped so resuming never teleports rocks.
 *
 * @param highScore the persisted best (already collected by the caller).
 * @param onScore invoked once per run, at game over, with the final score.
 * @param onExit back to the error page (also wired to the system back button).
 */
@Composable
fun AsteroidGame(
    highScore: Int,
    onScore: (Int) -> Unit,
    onExit: () -> Unit,
) {
    BackHandler { onExit() }

    // Pause gate: track RESUMED so the loop stops the moment the app leaves the foreground.
    val lifecycleOwner = LocalLifecycleOwner.current
    var resumed by remember {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, _ ->
            resumed = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // gameKey bumps on "Play again": every remember(gameKey) below resets for the new run.
    var gameKey by remember { mutableStateOf(0) }
    var sizePx by remember { mutableStateOf(IntSize.Zero) }
    val rng = remember(gameKey) { Random(System.nanoTime()) }
    var rocks by remember(gameKey) { mutableStateOf(listOf<Rock>()) }
    var elapsed by remember(gameKey) { mutableStateOf(0f) }
    var shipX by remember(gameKey) { mutableStateOf(-1f) } // -1 = "center me once size is known"
    var gameOver by remember(gameKey) { mutableStateOf(false) }
    // Static starfield, seeded once per composition (not per run) so the sky doesn't reshuffle.
    val stars = remember {
        val starRng = Random(42)
        List(90) { Star(starRng.nextFloat(), starRng.nextFloat(), 0.3f + starRng.nextFloat() * 0.7f) }
    }

    val score = (elapsed * 10).toInt()

    LaunchedEffect(gameKey, resumed, gameOver, sizePx) {
        if (!resumed || gameOver || sizePx == IntSize.Zero) return@LaunchedEffect
        val width = sizePx.width.toFloat()
        val height = sizePx.height.toFloat()
        val shipW = width * 0.09f
        val shipY = height * 0.82f
        if (shipX < 0f) shipX = width / 2f
        var lastNanos = 0L
        var spawnAccumulator = 0f
        while (true) {
            withFrameNanos { now ->
                if (lastNanos != 0L) {
                    // Cap dt: a dropped frame (or the first frame after a pause) must never
                    // slam rocks across half the screen.
                    val dt = ((now - lastNanos) / 1_000_000_000f).coerceAtMost(0.05f)
                    elapsed += dt
                    val params = AsteroidPhysics.spawnParams(elapsed)
                    spawnAccumulator += dt
                    if (spawnAccumulator >= params.intervalSeconds) {
                        spawnAccumulator = 0f
                        val r = width * (0.03f + rng.nextFloat() * 0.035f)
                        rocks = rocks + Rock(
                            x = r + rng.nextFloat() * (width - 2 * r),
                            y = -r,
                            r = r,
                            vy = height * (0.22f + rng.nextFloat() * 0.18f),
                        )
                    }
                    rocks = AsteroidPhysics.advance(rocks, dt, params.speedFactor)
                        .filter { it.y - it.r <= height }
                    if (AsteroidPhysics.collides(Ship(shipX, shipW), shipY, rocks)) {
                        gameOver = true
                        onScore(score)
                    }
                }
                lastNanos = now
            }
            if (gameOver) break
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SpaceBlack)
            .onSizeChanged { sizePx = it }
            .pointerInput(gameKey) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    if (shipX >= 0f && sizePx != IntSize.Zero) {
                        val halfW = sizePx.width * 0.045f
                        shipX = (shipX + dragAmount.x).coerceIn(halfW, sizePx.width - halfW)
                    }
                }
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Starfield backdrop.
            stars.forEach { star ->
                drawCircle(
                    color = Color.White.copy(alpha = 0.25f + 0.5f * star.twinkle),
                    radius = 1f + 2f * star.twinkle,
                    center = Offset(star.fx * size.width, star.fy * size.height),
                )
            }
            // Asteroids: a body circle with two darker offset "craters" for irregularity.
            rocks.forEach { rock ->
                val center = Offset(rock.x, rock.y)
                drawCircle(RockBody, rock.r, center)
                drawCircle(RockShade, rock.r * 0.38f, center + Offset(-rock.r * 0.32f, -rock.r * 0.22f))
                drawCircle(RockShade, rock.r * 0.24f, center + Offset(rock.r * 0.38f, rock.r * 0.3f))
            }
            // Ship: glow halo + triangle, nose up.
            if (shipX >= 0f && !gameOver) {
                val w = size.width * 0.09f
                val topY = size.height * 0.82f
                val glowCenter = Offset(shipX, topY + w * 0.55f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(ShipCyan.copy(alpha = 0.45f), Color.Transparent),
                        center = glowCenter,
                        radius = w * 1.5f,
                    ),
                    radius = w * 1.5f,
                    center = glowCenter,
                )
                drawPath(
                    path = Path().apply {
                        moveTo(shipX, topY)
                        lineTo(shipX - w / 2f, topY + w)
                        lineTo(shipX + w / 2f, topY + w)
                        close()
                    },
                    color = ShipCyan,
                )
            }
        }

        Text(
            "$score",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 12.dp),
        )

        if (gameOver) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(SpaceBlack.copy(alpha = 0.72f)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
            ) {
                Text("💥", style = MaterialTheme.typography.displayMedium)
                Text(
                    "Game over",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    "Score $score",
                    style = MaterialTheme.typography.titleLarge,
                    color = ShipCyan,
                    modifier = Modifier.padding(top = 16.dp),
                )
                Text(
                    "Best ${max(highScore, score)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 4.dp),
                )
                Button(
                    onClick = { gameKey++ },
                    modifier = Modifier.padding(top = 24.dp),
                ) { Text("Play again") }
                TextButton(
                    onClick = onExit,
                    modifier = Modifier.padding(top = 4.dp),
                ) { Text("Back", color = Color.White.copy(alpha = 0.8f)) }
            }
        }
    }
}
