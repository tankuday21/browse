package com.udaytank.browse.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.udaytank.browse.R
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.udaytank.browse.browser.PrivacyStatsFormat
import com.udaytank.browse.browser.feed.FeedItem
import com.udaytank.browse.browser.feed.QuickDial
import com.udaytank.browse.browser.feed.Weather
import com.udaytank.browse.data.HomeShortcutEntity
import com.udaytank.browse.data.ShortcutDensity
import com.udaytank.browse.ui.components.FaviconOrLetter
import com.udaytank.browse.ui.components.FeedItemCard
import com.udaytank.browse.ui.components.HomeSectionLabel
import com.udaytank.browse.ui.components.QuickDialsRow
import com.udaytank.browse.ui.components.WeatherCard
import com.udaytank.browse.ui.theme.OrbitRadii
import com.udaytank.browse.ui.theme.OrbitScheme
import com.udaytank.browse.ui.theme.OrbitSpacing
import com.udaytank.browse.ui.theme.orbit
import com.udaytank.browse.ui.theme.orbitBody
import com.udaytank.browse.ui.theme.orbitCaption
import com.udaytank.browse.ui.theme.orbitDisplay
import java.time.LocalTime

private fun greeting(): String = when (LocalTime.now().hour) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    in 17..21 -> "Good evening"
    else -> "Up late, explorer?"
}

/** Does the clipboard text look like something we can navigate to? (Same cases as UrlInput 1+2.) */
private fun looksLikeUrl(text: String): Boolean {
    val t = text.trim()
    return t.isNotEmpty() && !t.contains(' ') && !t.contains('\n') &&
        (t.startsWith("http://") || t.startsWith("https://") || t.contains('.'))
}

/**
 * The two built-in home backdrops (v3.1 wallpaper picker). "" (none) renders no brush at all.
 * Both are built purely from Orbit tokens — no new color literals — so they stay in tune with
 * whichever scheme (dark/light) is active.
 */
private fun homeBackdropBrush(id: String, scheme: OrbitScheme): Brush? = when (id) {
    "aurora" -> Brush.verticalGradient(
        listOf(
            scheme.accent.gradient.first().copy(alpha = if (scheme.dark) 0.28f else 0.14f),
            scheme.surfaces.base,
        ),
    )
    "nebula" -> Brush.verticalGradient(
        listOf(
            scheme.accent.gradient.last().copy(alpha = if (scheme.dark) 0.24f else 0.12f),
            scheme.surfaces.elevated.copy(alpha = if (scheme.dark) 0.5f else 0.3f),
            scheme.surfaces.base,
        ),
    )
    else -> null
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShortcutTile(
    shortcut: HomeShortcutEntity,
    isMenuOpen: Boolean,
    onOpen: () -> Unit,
    onLongClick: () -> Unit,
    onDismissMenu: () -> Unit,
    onMoveToFront: () -> Unit,
    onRemove: () -> Unit,
) {
    val scheme = orbit()
    Box {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.combinedClickable(onClick = onOpen, onLongClick = onLongClick),
        ) {
            FaviconOrLetter(url = shortcut.url, label = shortcut.title, size = 56.dp)
            Text(
                shortcut.title,
                style = orbitCaption,
                color = scheme.text.secondary,
                maxLines = 1,
                modifier = Modifier.padding(top = OrbitSpacing.xs),
            )
        }
        DropdownMenu(expanded = isMenuOpen, onDismissRequest = onDismissMenu) {
            DropdownMenuItem(
                text = { Text("Move to front") },
                onClick = { onMoveToFront(); onDismissMenu() },
            )
            DropdownMenuItem(
                text = { Text("Remove") },
                onClick = { onRemove(); onDismissMenu() },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AddShortcutTile(onClick: () -> Unit) {
    val scheme = orbit()
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.combinedClickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .border(1.dp, scheme.text.muted, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Add shortcut", tint = scheme.text.muted)
        }
        Text(
            "Add",
            style = orbitCaption,
            color = scheme.text.muted,
            maxLines = 1,
            modifier = Modifier.padding(top = OrbitSpacing.xs),
        )
    }
}

/**
 * The Home search entry (v4.1): a prominent centered pill under the wordmark. Tapping anywhere on
 * it calls [onSearchClick] (which opens the shared address entry); the trailing mic calls
 * [onVoiceSearch]. Purely a launcher — it holds no text of its own.
 */
@Composable
private fun HomeSearchPill(onSearchClick: () -> Unit, onVoiceSearch: () -> Unit) {
    val scheme = orbit()
    Surface(
        shape = RoundedCornerShape(OrbitRadii.bar),
        color = scheme.surfaces.surface,
        border = BorderStroke(1.dp, scheme.text.muted.copy(alpha = 0.16f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSearchClick() },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = OrbitSpacing.lg, vertical = OrbitSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Search,
                contentDescription = null,
                tint = scheme.text.muted,
                modifier = Modifier.size(20.dp),
            )
            Text(
                "Search or type URL",
                style = orbitBody,
                color = scheme.text.muted,
                maxLines = 1,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = OrbitSpacing.md),
            )
            Icon(
                Icons.Filled.Mic,
                contentDescription = "Voice search",
                tint = scheme.accent.solid,
                modifier = Modifier
                    .size(20.dp)
                    .clickable { onVoiceSearch() },
            )
        }
    }
}

/**
 * A feed section's header: a subtle full-width hairline divider above the [HomeSectionLabel], so
 * the quick-dials / Weather / News / Sports sections read as clearly separate bands.
 */
@Composable
private fun FeedSectionHeader(label: String) {
    val scheme = orbit()
    HorizontalDivider(
        color = scheme.text.muted.copy(alpha = 0.15f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = OrbitSpacing.md),
    )
    HomeSectionLabel(label)
}

/**
 * Focused default: logo/wordmark + ONE calm shortcut row. [showGreeting]/[showHomeStats]/
 * [shortcutDensity]/[homeWallpaper] (v3.1 Home prefs, Task 5) opt back into a richer canvas —
 * greeting line, privacy stats card (never on incognito), the full shortcut grid, and a subtle
 * built-in backdrop, respectively. A prominent centered search pill (Task 3, v4.1) sits under the
 * wordmark and owns address-entry on Home via [onSearchClick] / [onVoiceSearch]; the caller hides
 * the bottom bar while Home is showing.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomePage(
    shortcuts: List<HomeShortcutEntity>,
    isIncognito: Boolean,
    onOpenUrl: (String) -> Unit,
    onAddShortcut: (url: String, title: String) -> Unit,
    onRemoveShortcut: (Long) -> Unit,
    onMoveShortcutToFront: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onSearchClick: () -> Unit = {},
    onVoiceSearch: () -> Unit = {},
    lifetimeBlocked: Long = 0L,
    showGreeting: Boolean = false,
    showHomeStats: Boolean = false,
    shortcutDensity: ShortcutDensity = ShortcutDensity.FEW,
    homeWallpaper: String = "",
    // v3.2 feed (non-incognito only; gated by showFeed).
    quickDials: List<QuickDial> = emptyList(),
    weather: Weather? = null,
    weatherPlace: String = "",
    newsItems: List<FeedItem> = emptyList(),
    sportsItems: List<FeedItem> = emptyList(),
    showFeed: Boolean = false,
    showWeather: Boolean = true,
) {
    val scheme = orbit()
    val clipboard = LocalClipboardManager.current
    var showAddDialog by remember { mutableStateOf(false) }
    var menuForId by remember { mutableStateOf<Long?>(null) }

    // Gentle entrance: the canvas fades in and rises a few dp when Home appears (and each time
    // you return to it), so it arrives rather than snapping in.
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val enterAlpha by animateFloatAsState(
        if (appeared) 1f else 0f,
        animationSpec = tween(400),
        label = "homeEnterAlpha",
    )
    val enterRise by animateDpAsState(
        if (appeared) 0.dp else OrbitSpacing.md,
        animationSpec = tween(400),
        label = "homeEnterRise",
    )

    Box(modifier = modifier) {
        // Cosmic backdrop (v3.2): the hero art as a soft, borderless wash across the top,
        // faded into the base by a vertical scrim so content below stays readable. Not in
        // incognito — the private home is bare by design.
        if (!isIncognito) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.62f)
                    .align(Alignment.TopCenter),
            ) {
                Image(
                    painter = painterResource(R.drawable.home_backdrop),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter,
                    modifier = Modifier.matchParentSize().alpha(0.42f),
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                1f to scheme.surfaces.base,
                            ),
                        ),
                )
            }
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .offset(y = enterRise)
                .alpha(enterAlpha)
                .padding(OrbitSpacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(OrbitSpacing.xxl))
            if (isIncognito) {
                Text("Incognito", style = orbitDisplay, color = scheme.text.primary)
            } else {
                // Wordmark painted with the brand gradient rather than a flat accent fill.
                Text(
                    "Andromeda",
                    style = orbitDisplay.merge(
                        TextStyle(brush = Brush.horizontalGradient(scheme.accent.gradient)),
                    ),
                )
            }
            if (!isIncognito && showGreeting) {
                Spacer(modifier = Modifier.height(OrbitSpacing.xs))
                Text(greeting(), style = orbitBody, color = scheme.text.secondary)
            }
            if (isIncognito) {
                Spacer(modifier = Modifier.height(OrbitSpacing.md))
                Text(
                    "Pages you view in this tab won't appear in your history,\nand the tab disappears when you close the app.",
                    style = orbitBody,
                    color = scheme.text.secondary,
                    textAlign = TextAlign.Center,
                )
            }
            if (!isIncognito) {
                Spacer(modifier = Modifier.height(OrbitSpacing.xl))
                HomeSearchPill(onSearchClick = onSearchClick, onVoiceSearch = onVoiceSearch)
            }
            Spacer(modifier = Modifier.height(OrbitSpacing.xl))

            // ── Shortcut row/grid (C1) — user-curated, so shown in incognito too. No
            // centered search pill anymore (v3.1): the shared OmniBar lives bottom-anchored
            // below this whole canvas — tapping it enters edit mode exactly like on a web
            // page. Density (Task 5/7) picks between one calm row and the full grid. ──
            if (shortcutDensity == ShortcutDensity.MORE) {
                // Manual 4-column grid — a LazyVerticalGrid can't nest inside this verticalScroll
                // Column (both scroll vertically). Shortcut counts are small, so non-lazy is fine.
                // Cell index maps: [0..shortcuts) = tiles, then one Add cell, then blanks to pad.
                val cells = shortcuts.size + 1
                val rowCount = (cells + 3) / 4
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(OrbitSpacing.lg),
                ) {
                    for (row in 0 until rowCount) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(OrbitSpacing.md),
                        ) {
                            for (col in 0 until 4) {
                                val i = row * 4 + col
                                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                                    when {
                                        i < shortcuts.size -> {
                                            val sc = shortcuts[i]
                                            ShortcutTile(
                                                shortcut = sc,
                                                isMenuOpen = menuForId == sc.id,
                                                onOpen = { onOpenUrl(sc.url) },
                                                onLongClick = { menuForId = sc.id },
                                                onDismissMenu = { menuForId = null },
                                                onMoveToFront = { onMoveShortcutToFront(sc.id) },
                                                onRemove = { onRemoveShortcut(sc.id) },
                                            )
                                        }
                                        i == shortcuts.size -> AddShortcutTile(onClick = { showAddDialog = true })
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(OrbitSpacing.md),
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                ) {
                    shortcuts.forEach { shortcut ->
                        ShortcutTile(
                            shortcut = shortcut,
                            isMenuOpen = menuForId == shortcut.id,
                            onOpen = { onOpenUrl(shortcut.url) },
                            onLongClick = { menuForId = shortcut.id },
                            onDismissMenu = { menuForId = null },
                            onMoveToFront = { onMoveShortcutToFront(shortcut.id) },
                            onRemove = { onRemoveShortcut(shortcut.id) },
                        )
                    }
                    AddShortcutTile(onClick = { showAddDialog = true })
                }
            }

            // ── v3.2 feed — non-incognito, opt-in (showFeed). Quick dials, weather, news, sports.
            // Each section renders only when it has content, so an offline/empty feed just
            // collapses back to the calm focused home. ──
            if (!isIncognito && showFeed) {
                if (quickDials.isNotEmpty()) {
                    FeedSectionHeader("Shortcuts you visit")
                    QuickDialsRow(dials = quickDials, onOpen = onOpenUrl)
                }
                if (showWeather && weather != null) {
                    FeedSectionHeader("Weather")
                    WeatherCard(weather = weather, place = weatherPlace)
                }
                if (newsItems.isNotEmpty()) {
                    FeedSectionHeader("News")
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(OrbitSpacing.sm),
                    ) { newsItems.forEach { FeedItemCard(item = it, onOpen = onOpenUrl) } }
                }
                if (sportsItems.isNotEmpty()) {
                    FeedSectionHeader("Sports")
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(OrbitSpacing.sm),
                    ) { sportsItems.forEach { FeedItemCard(item = it, onOpen = onOpenUrl) } }
                }
            }

            // ── Privacy stats (C3) — opt-in (Task 5/7), never on incognito, and still
            // hidden until anything's actually blocked (nothing to show otherwise). ──
            if (!isIncognito && showHomeStats && lifetimeBlocked > 0) {
                val (blockedLine, savedLine) = PrivacyStatsFormat.format(lifetimeBlocked)
                Spacer(modifier = Modifier.height(OrbitSpacing.xxl))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        Icons.Filled.Shield,
                        contentDescription = null,
                        tint = scheme.accent.solid,
                        modifier = Modifier.size(28.dp),
                    )
                    Column(modifier = Modifier.padding(start = OrbitSpacing.md)) {
                        Text(blockedLine, style = orbitBody, color = scheme.text.primary)
                        Text(savedLine, style = orbitCaption, color = scheme.text.muted)
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        // Clipboard is read once, at the moment the dialog opens - never passively.
        val clipboardUrl = remember {
            clipboard.getText()?.text?.trim().orEmpty().takeIf(::looksLikeUrl).orEmpty()
        }
        var url by remember { mutableStateOf(clipboardUrl) }
        var title by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add to home") },
            text = {
                Column {
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text("Link") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = url.isNotBlank(),
                    onClick = {
                        onAddShortcut(url, title)
                        showAddDialog = false
                    },
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("Cancel") }
            },
        )
    }
}
