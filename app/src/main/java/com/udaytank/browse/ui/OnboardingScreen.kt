package com.udaytank.browse.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.udaytank.browse.R
import com.udaytank.browse.ui.theme.OrbitSpacing
import com.udaytank.browse.ui.theme.orbit
import com.udaytank.browse.ui.theme.orbitBody
import com.udaytank.browse.ui.theme.orbitDisplay
import kotlinx.coroutines.launch

/**
 * First-run onboarding (J2): three skippable pages rendered INSTEAD of the browser UI, so
 * nothing of the real app flashes underneath. Every exit path — Skip, Get started, Maybe
 * later — must call [onDone], which sets the persistent flag; there is no way back in.
 */
@Composable
fun OnboardingScreen(
    onImportBookmarks: (String, (Int) -> Unit) -> Unit,
    onSetDefaultBrowser: () -> Unit,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState { 3 }

    // Mirrors the Settings screen's bookmark-HTML import wiring (BookmarkIO SAF flow).
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val html = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
            if (html != null) onImportBookmarks(html) { count ->
                Toast.makeText(context, "Imported $count bookmarks", Toast.LENGTH_SHORT).show()
                // Import done — move the user along to the last page automatically.
                scope.launch { pagerState.animateScrollToPage(2) }
            }
        }
    }

    val scheme = orbit()
    Surface(modifier = Modifier.fillMaxSize(), color = scheme.surfaces.base) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth(),
            ) {
                TextButton(
                    onClick = onDone,
                    modifier = Modifier.padding(horizontal = OrbitSpacing.sm, vertical = OrbitSpacing.xs),
                ) { Text("Skip") }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) { page ->
                when (page) {
                    0 -> BrandingPage()
                    1 -> ImportPage(onImport = { importLauncher.launch(arrayOf("text/html")) })
                    else -> DefaultBrowserPage(
                        onSetDefault = onSetDefaultBrowser,
                        onMaybeLater = onDone,
                    )
                }
            }

            // Page indicator dots.
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = OrbitSpacing.md),
            ) {
                repeat(3) { index ->
                    Box(
                        modifier = Modifier
                            .padding(horizontal = OrbitSpacing.xs)
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                if (index == pagerState.currentPage) scheme.accent.solid
                                else scheme.surfaces.elevated
                            ),
                    )
                }
            }

            Button(
                onClick = {
                    if (pagerState.currentPage < 2) {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    } else {
                        onDone()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = OrbitSpacing.xl)
                    .padding(bottom = OrbitSpacing.lg),
            ) {
                Text(if (pagerState.currentPage < 2) "Next" else "Get started")
            }
        }
    }
}

@Composable
private fun BrandingPage() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = OrbitSpacing.xxl),
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_fg),
            contentDescription = null,
            modifier = Modifier.size(120.dp),
        )
        Text(
            "Your private power browser",
            style = orbitDisplay,
            color = orbit().text.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = OrbitSpacing.lg, bottom = OrbitSpacing.xl),
        )
        FeatureBullet(Icons.Filled.Block, "Ad blocking built in")
        FeatureBullet(Icons.Filled.Lock, "Your data stays on your device")
        FeatureBullet(Icons.AutoMirrored.Filled.MenuBook, "Downloads, reading, and tabs that work for you")
    }
}

@Composable
private fun FeatureBullet(icon: ImageVector, text: String) {
    val scheme = orbit()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = OrbitSpacing.sm),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = scheme.accent.solid,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text,
            style = orbitBody,
            color = scheme.text.primary,
            modifier = Modifier.padding(start = OrbitSpacing.lg),
        )
    }
}

@Composable
private fun ImportPage(onImport: () -> Unit) {
    val scheme = orbit()
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = OrbitSpacing.xxl),
    ) {
        Text(
            "Bring your bookmarks",
            style = orbitDisplay,
            color = scheme.text.primary,
            textAlign = TextAlign.Center,
        )
        Text(
            "Export your bookmarks from your old browser as an HTML file, then pick it here.",
            style = orbitBody,
            color = scheme.text.muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = OrbitSpacing.lg),
        )
        OutlinedButton(onClick = onImport) { Text("Import bookmarks") }
    }
}

@Composable
private fun DefaultBrowserPage(onSetDefault: () -> Unit, onMaybeLater: () -> Unit) {
    val scheme = orbit()
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = OrbitSpacing.xxl),
    ) {
        Text(
            "Make Andromeda your default",
            style = orbitDisplay,
            color = scheme.text.primary,
            textAlign = TextAlign.Center,
        )
        Text(
            "Links from other apps will open here, with ads blocked and your privacy protected.",
            style = orbitBody,
            color = scheme.text.muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = OrbitSpacing.lg),
        )
        OutlinedButton(onClick = onSetDefault) { Text("Set as default browser") }
        TextButton(
            onClick = onMaybeLater,
            modifier = Modifier.padding(top = OrbitSpacing.sm),
        ) { Text("Maybe later") }
    }
}
