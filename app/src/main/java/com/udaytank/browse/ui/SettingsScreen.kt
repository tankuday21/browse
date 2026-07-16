package com.udaytank.browse.ui

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.text.format.DateUtils
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.udaytank.browse.BrowserViewModel
import com.udaytank.browse.browser.adblock.FilterListUpdater
import com.udaytank.browse.browser.adblock.FilterLists
import com.udaytank.browse.data.SearchEngine
import com.udaytank.browse.data.ShortcutDensity
import com.udaytank.browse.data.ThemeMode
import com.udaytank.browse.ui.components.OrbitListRow
import com.udaytank.browse.ui.components.OrbitTextField
import com.udaytank.browse.ui.components.OrbitTopBar
import com.udaytank.browse.ui.theme.OrbitRadii
import com.udaytank.browse.ui.theme.OrbitSpacing
import com.udaytank.browse.ui.theme.orbit
import com.udaytank.browse.ui.theme.orbitBody
import com.udaytank.browse.ui.theme.orbitCaption
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** The settings categories, in landing order. [key] is the persisted open-category token. */
private enum class SettingsCategory(
    val key: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
) {
    GENERAL("general", "General", "Default browser & search engine", Icons.Filled.Tune),
    APPEARANCE("appearance", "Appearance", "Theme & website dark mode", Icons.Filled.Palette),
    HOME_FEED("home", "Home & feed", "Greeting, shortcuts, wallpaper, feed", Icons.Filled.Home),
    PRIVACY("privacy", "Privacy & security", "Ads, cookies, JavaScript, data", Icons.Filled.Shield),
    DOWNLOADS("downloads", "Downloads", "How files are saved", Icons.Filled.Download),
    ADVANCED("advanced", "Advanced & about", "Text size, backup, version", Icons.Filled.Info),
}

/**
 * The root Settings surface. Presents a category landing page of tappable cards; tapping one
 * swaps in that category's own sub-screen (tracked by [openCategory], which survives config
 * changes and is dismissed by both the sub-screen's back arrow and the system Back gesture).
 * No Navigation-Compose dependency — a single hoisted state does the routing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: BrowserViewModel,
    onClearBrowsingData: () -> Unit,
    onBack: () -> Unit,
) {
    val scheme = orbit()
    var openCategory by rememberSaveable { mutableStateOf<String?>(null) }

    // Intercept system Back while inside a sub-screen so it returns to the landing, not the browser.
    BackHandler(enabled = openCategory != null) { openCategory = null }

    val dismiss: () -> Unit = { openCategory = null }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.surfaces.base),
    ) {
        when (openCategory) {
            SettingsCategory.GENERAL.key ->
                GeneralSettings(viewModel, dismiss)
            SettingsCategory.APPEARANCE.key ->
                AppearanceSettings(viewModel, dismiss)
            SettingsCategory.HOME_FEED.key ->
                HomeFeedSettings(viewModel, dismiss)
            SettingsCategory.PRIVACY.key ->
                PrivacySecuritySettings(viewModel, onClearBrowsingData, dismiss)
            SettingsCategory.DOWNLOADS.key ->
                DownloadsSettings(viewModel, dismiss)
            SettingsCategory.ADVANCED.key ->
                AdvancedAboutSettings(viewModel, dismiss)
            else -> SettingsLanding(onBack = onBack, onOpen = { openCategory = it.key })
        }
    }
}

/** The category landing: an OrbitTopBar plus one flat, tonal card per [SettingsCategory]. */
@Composable
private fun SettingsLanding(
    onBack: () -> Unit,
    onOpen: (SettingsCategory) -> Unit,
) {
    OrbitTopBar(title = "Settings", onBack = onBack)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = OrbitSpacing.lg, vertical = OrbitSpacing.sm),
    ) {
        SettingsCategory.entries.forEach { category ->
            SettingsCategoryCard(category = category, onClick = { onOpen(category) })
            Spacer(Modifier.height(OrbitSpacing.md))
        }
        Spacer(Modifier.height(OrbitSpacing.xl))
    }
}

/**
 * One landing card: a flat tonal surface (no border, only a whisper of elevation), an
 * accent-tinted icon tile, title + subtitle, and a trailing chevron.
 */
@Composable
private fun SettingsCategoryCard(
    category: SettingsCategory,
    onClick: () -> Unit,
) {
    val scheme = orbit()
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(OrbitRadii.card),
        color = scheme.surfaces.surface,
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(OrbitSpacing.lg),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        scheme.accent.solid.copy(alpha = 0.15f),
                        RoundedCornerShape(OrbitRadii.chip),
                    ),
            ) {
                Icon(
                    category.icon,
                    contentDescription = null,
                    tint = scheme.accent.solid,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(modifier = Modifier
                .weight(1f)
                .padding(start = OrbitSpacing.lg)) {
                Text(category.title, style = orbitBody, color = scheme.text.primary)
                Text(category.subtitle, style = orbitCaption, color = scheme.text.muted)
            }
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = scheme.text.muted,
            )
        }
    }
}

// ─────────────────────────── shared sub-screen building blocks ───────────────────────────

/** Wraps a category sub-screen: fixed [OrbitTopBar] over a vertically scrolling content column. */
@Composable
private fun CategoryScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    OrbitTopBar(title = title, onBack = onBack)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(OrbitSpacing.md))
        content()
        Spacer(Modifier.height(OrbitSpacing.xxl))
    }
}

/**
 * A flat, tonal, rounded container grouping one set of related setting rows — the "modern
 * card" for this screen. Deliberately border-less (no [androidx.compose.foundation.BorderStroke]):
 * separation between groups comes from surrounding whitespace, and separation between rows
 * *inside* a group comes only from [GroupDivider], never from an outline around every item.
 */
@Composable
private fun SettingsGroup(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(OrbitRadii.card),
        color = orbit().surfaces.elevated,
        shadowElevation = 0.dp,
        modifier = Modifier
            .padding(horizontal = OrbitSpacing.lg)
            .fillMaxWidth(),
    ) {
        Column { content() }
    }
}

/** A subtle, low-alpha divider used only BETWEEN rows inside a [SettingsGroup]. */
@Composable
private fun GroupDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = OrbitSpacing.lg),
        color = orbit().text.muted.copy(alpha = 0.12f),
    )
}

/** A small, uppercase, muted section label sitting above a [SettingsGroup] — never boxed. */
@Composable
private fun SectionHeader(text: String) {
    Text(
        text.uppercase(),
        style = orbitCaption.copy(letterSpacing = 0.8.sp, fontWeight = FontWeight.Medium),
        color = orbit().text.muted,
        modifier = Modifier.padding(
            start = OrbitSpacing.xl,
            end = OrbitSpacing.xl,
            top = OrbitSpacing.lg,
            bottom = OrbitSpacing.sm,
        ),
    )
}

/** A muted, full-width, multi-line explanatory caption — sits below or inside a [SettingsGroup]. */
@Composable
private fun Caption(text: String) {
    Text(
        text,
        style = orbitCaption,
        color = orbit().text.muted,
        modifier = Modifier.padding(horizontal = OrbitSpacing.lg, vertical = OrbitSpacing.xs),
    )
}

/** A whole-row-tappable preference toggle rendered through the shared [OrbitListRow]. */
@Composable
private fun PrefSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null,
    enabled: Boolean = true,
) {
    OrbitListRow(
        leadingIcon = null,
        title = title,
        subtitle = subtitle,
        trailing = { Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled) },
        onClick = if (enabled) ({ onCheckedChange(!checked) }) else null,
    )
}

/** A full-width, single-select radio row (search engine / theme / wallpaper pickers). */
@Composable
private fun RadioOptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val scheme = orbit()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(selected = selected, onClick = onClick)
            .padding(horizontal = OrbitSpacing.lg, vertical = OrbitSpacing.xs),
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(
            label,
            style = orbitBody,
            color = scheme.text.primary,
            modifier = Modifier.padding(start = OrbitSpacing.sm),
        )
    }
}

// ─────────────────────────────────── General ───────────────────────────────────

/** General category: set-as-default-browser (#3) plus the search-engine picker. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GeneralSettings(
    viewModel: BrowserViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val engine by viewModel.searchEngine.collectAsStateWithLifecycle()

    // RoleManager reflects the current default-browser holder; re-checked when the request returns.
    fun roleHeld(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            context.getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_BROWSER) == true

    var isDefaultBrowser by remember { mutableStateOf(roleHeld()) }
    val defaultBrowserLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { isDefaultBrowser = roleHeld() }

    CategoryScaffold(title = "General", onBack = onBack) {
        SectionHeader("Default browser")
        SettingsGroup {
            if (isDefaultBrowser) {
                OrbitListRow(
                    leadingIcon = Icons.Filled.Check,
                    title = "Andromeda is your default browser",
                    subtitle = "Links from other apps open here",
                )
            } else {
                OrbitListRow(
                    leadingIcon = null,
                    title = "Set as default browser",
                    subtitle = "Open links from other apps in Andromeda",
                    onClick = {
                        val rm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            context.getSystemService(RoleManager::class.java)
                        } else {
                            null
                        }
                        if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_BROWSER) &&
                            !rm.isRoleHeld(RoleManager.ROLE_BROWSER)
                        ) {
                            defaultBrowserLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_BROWSER))
                        } else {
                            runCatching {
                                context.startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
                            }
                        }
                    },
                )
            }
        }

        Spacer(Modifier.height(OrbitSpacing.xl))
        SectionHeader("Search engine")
        SettingsGroup {
            SearchEngine.entries.forEachIndexed { index, option ->
                RadioOptionRow(
                    label = option.label,
                    selected = engine == option,
                    onClick = { viewModel.onSearchEngineSelected(option) },
                )
                if (index != SearchEngine.entries.lastIndex) GroupDivider()
            }
        }
    }
}

// ────────────────────────────────── Appearance ──────────────────────────────────

/** Appearance category: the app theme mode (#8) and the force-dark-websites toggle. */
@Composable
private fun AppearanceSettings(
    viewModel: BrowserViewModel,
    onBack: () -> Unit,
) {
    val theme by viewModel.themeMode.collectAsStateWithLifecycle()
    val forceDark by viewModel.forceDark.collectAsStateWithLifecycle()

    CategoryScaffold(title = "Appearance", onBack = onBack) {
        SectionHeader("Theme")
        SettingsGroup {
            ThemeMode.entries.forEachIndexed { index, option ->
                RadioOptionRow(
                    label = option.name.lowercase().replaceFirstChar { it.uppercase() },
                    selected = theme == option,
                    onClick = { viewModel.onThemeSelected(option) },
                )
                if (index != ThemeMode.entries.lastIndex) GroupDivider()
            }
        }
        Caption("System follows your device's light/dark setting.")

        Spacer(Modifier.height(OrbitSpacing.xl))
        SectionHeader("Websites")
        SettingsGroup {
            PrefSwitchRow(
                title = "Dark mode for websites",
                subtitle = "Render light pages with a dark background where supported",
                checked = forceDark,
                onCheckedChange = viewModel::onForceDarkToggled,
            )
        }
    }
}

// ────────────────────────────────── Home & feed ──────────────────────────────────

/** Home & feed category: the home-canvas look controls and the news/weather feed controls. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeFeedSettings(
    viewModel: BrowserViewModel,
    onBack: () -> Unit,
) {
    val scheme = orbit()
    val context = LocalContext.current
    val showGreeting by viewModel.showGreeting.collectAsStateWithLifecycle()
    val showHomeStats by viewModel.showHomeStats.collectAsStateWithLifecycle()
    val shortcutDensity by viewModel.shortcutDensity.collectAsStateWithLifecycle()
    val homeWallpaper by viewModel.homeWallpaper.collectAsStateWithLifecycle()
    val showFeed by viewModel.showFeed.collectAsStateWithLifecycle()
    val showWeather by viewModel.showWeather.collectAsStateWithLifecycle()
    val showNews by viewModel.showNews.collectAsStateWithLifecycle()
    val weatherCity by viewModel.weatherCity.collectAsStateWithLifecycle()
    val weatherUseLocation by viewModel.weatherUseLocation.collectAsStateWithLifecycle()

    // Enabling location-based weather requests the coarse permission; the grant decides the pref.
    val locationPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> viewModel.onWeatherUseLocationToggled(granted) }

    CategoryScaffold(title = "Home & feed", onBack = onBack) {
        SectionHeader("Home canvas")
        SettingsGroup {
            PrefSwitchRow(
                title = "Show greeting",
                checked = showGreeting,
                onCheckedChange = viewModel::onShowGreetingToggled,
            )
            GroupDivider()
            PrefSwitchRow(
                title = "Show privacy stats on home",
                checked = showHomeStats,
                onCheckedChange = viewModel::onShowHomeStatsToggled,
            )
            GroupDivider()
            Text(
                "Shortcut density",
                style = orbitBody,
                color = scheme.text.primary,
                modifier = Modifier.padding(horizontal = OrbitSpacing.lg, vertical = OrbitSpacing.sm),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = OrbitSpacing.sm),
            ) {
                listOf(ShortcutDensity.FEW to "Few", ShortcutDensity.MORE to "More").forEach { (option, label) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .selectable(
                                selected = shortcutDensity == option,
                                onClick = { viewModel.onShortcutDensitySelected(option) },
                            )
                            .padding(horizontal = OrbitSpacing.sm, vertical = OrbitSpacing.xs),
                    ) {
                        RadioButton(selected = shortcutDensity == option, onClick = null)
                        Text(
                            label,
                            style = orbitBody,
                            color = scheme.text.primary,
                            modifier = Modifier.padding(start = OrbitSpacing.xs),
                        )
                    }
                }
            }
        }
        Caption("One calm row (Few) or your full shortcut grid (More) on the home canvas.")

        Spacer(Modifier.height(OrbitSpacing.xl))
        SectionHeader("Wallpaper")
        SettingsGroup {
            val wallpapers = listOf("" to "None", "aurora" to "Aurora", "nebula" to "Nebula")
            wallpapers.forEachIndexed { index, (id, label) ->
                RadioOptionRow(
                    label = label,
                    selected = homeWallpaper == id,
                    onClick = { viewModel.onHomeWallpaperSelected(id) },
                )
                if (index != wallpapers.lastIndex) GroupDivider()
            }
        }

        Spacer(Modifier.height(OrbitSpacing.xl))
        SectionHeader("Feed")
        SettingsGroup {
            PrefSwitchRow(
                title = "Show news, sports & weather feed",
                checked = showFeed,
                onCheckedChange = viewModel::onShowFeedToggled,
            )
            if (showFeed) {
                GroupDivider()
                PrefSwitchRow(
                    title = "News",
                    subtitle = "Show the news headlines section on home",
                    checked = showNews,
                    onCheckedChange = viewModel::onShowNewsToggled,
                )
                GroupDivider()
                PrefSwitchRow(
                    title = "Weather card",
                    checked = showWeather,
                    onCheckedChange = viewModel::onShowWeatherToggled,
                )
                if (showWeather) {
                    GroupDivider()
                    PrefSwitchRow(
                        title = "Use my location",
                        checked = weatherUseLocation,
                        onCheckedChange = { on ->
                            if (on) {
                                val granted = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.ACCESS_COARSE_LOCATION,
                                ) == PackageManager.PERMISSION_GRANTED
                                if (granted) viewModel.onWeatherUseLocationToggled(true)
                                else locationPermLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                            } else {
                                viewModel.onWeatherUseLocationToggled(false)
                            }
                        },
                    )
                    if (!weatherUseLocation) {
                        OrbitTextField(
                            value = weatherCity,
                            onValueChange = viewModel::onWeatherCityChanged,
                            label = "City for weather",
                            placeholder = "e.g. Mumbai",
                            leadingIcon = Icons.Filled.LocationOn,
                            modifier = Modifier
                                .padding(horizontal = OrbitSpacing.lg, vertical = OrbitSpacing.sm),
                        )
                    }
                }
            }
        }
        if (showFeed) {
            Caption(
                "News & sports come from public RSS feeds, fetched straight from the source. " +
                    "Weather uses Open-Meteo. No tracking, no accounts.",
            )
        }
    }
}

// ───────────────────────────────── Privacy & security ─────────────────────────────────

/** Privacy & security category: ad-blocking, safe browsing, tracking, content, and clear-data. */
@Composable
private fun PrivacySecuritySettings(
    viewModel: BrowserViewModel,
    onClearBrowsingData: () -> Unit,
    onBack: () -> Unit,
) {
    val scheme = orbit()
    val context = LocalContext.current
    val jsEnabled by viewModel.javaScriptEnabled.collectAsStateWithLifecycle()
    val cookiesEnabled by viewModel.cookiesEnabled.collectAsStateWithLifecycle()
    val adBlockEnabled by viewModel.adBlockEnabled.collectAsStateWithLifecycle()
    val adBlockLists by viewModel.adBlockLists.collectAsStateWithLifecycle()
    val adBlockLastUpdated by viewModel.adBlockLastUpdated.collectAsStateWithLifecycle()
    val safeBrowsing by viewModel.safeBrowsing.collectAsStateWithLifecycle()
    val dismissCookieBanners by viewModel.dismissCookieBanners.collectAsStateWithLifecycle()
    val gpcEnabled by viewModel.gpcEnabled.collectAsStateWithLifecycle()
    val httpsOnly by viewModel.httpsOnly.collectAsStateWithLifecycle()
    val lockIncognito by viewModel.lockIncognito.collectAsStateWithLifecycle()
    var showClearDialog by remember { mutableStateOf(false) }

    CategoryScaffold(title = "Privacy & security", onBack = onBack) {
        SectionHeader("Ad blocking")
        SettingsGroup {
            PrefSwitchRow(
                title = "Block ads",
                checked = adBlockEnabled,
                onCheckedChange = viewModel::onAdBlockToggled,
            )
            FilterLists.ADS.forEach { def ->
                GroupDivider()
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .padding(
                            start = OrbitSpacing.xxl,
                            end = OrbitSpacing.lg,
                            top = OrbitSpacing.xs,
                            bottom = OrbitSpacing.xs,
                        ),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(def.label, style = orbitBody, color = scheme.text.primary)
                        Text(def.description, style = orbitCaption, color = scheme.text.muted)
                    }
                    Switch(
                        checked = def.id in adBlockLists,
                        enabled = adBlockEnabled,
                        onCheckedChange = { viewModel.onAdBlockListToggled(def.id) },
                    )
                }
            }
            GroupDivider()
            TextButton(
                onClick = {
                    FilterListUpdater.updateNow(context)
                    Toast.makeText(context, "Updating filter lists…", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.padding(horizontal = OrbitSpacing.sm),
            ) {
                Text("Update filter lists")
            }
            Caption(
                if (adBlockLastUpdated == 0L) {
                    "Using bundled lists — never updated"
                } else {
                    "Updated " + DateUtils.getRelativeTimeSpanString(
                        adBlockLastUpdated,
                        System.currentTimeMillis(),
                        DateUtils.MINUTE_IN_MILLIS,
                    )
                },
            )
        }

        Spacer(Modifier.height(OrbitSpacing.xl))
        SectionHeader("Browsing protection")
        SettingsGroup {
            PrefSwitchRow(
                title = "Safe Browsing",
                checked = safeBrowsing,
                onCheckedChange = viewModel::onSafeBrowsingToggled,
            )
            Caption("Warns you before phishing and malware sites (Google Safe Browsing)")
            GroupDivider()
            PrefSwitchRow(
                title = "Auto-dismiss cookie banners",
                checked = dismissCookieBanners,
                onCheckedChange = viewModel::onDismissCookieBannersToggled,
            )
            Caption("Hides consent pop-ups (may affect some sites)")
            GroupDivider()
            PrefSwitchRow(
                title = "Global Privacy Control",
                checked = gpcEnabled,
                onCheckedChange = viewModel::onGpcToggled,
            )
            Caption("Tells sites not to sell or share your data (takes effect on new tabs)")
        }

        Spacer(Modifier.height(OrbitSpacing.xl))
        SectionHeader("Content & connection")
        SettingsGroup {
            PrefSwitchRow(
                title = "JavaScript",
                checked = jsEnabled,
                onCheckedChange = viewModel::onJavaScriptToggled,
            )
            GroupDivider()
            PrefSwitchRow(
                title = "Accept cookies",
                checked = cookiesEnabled,
                onCheckedChange = viewModel::onCookiesToggled,
            )
            GroupDivider()
            PrefSwitchRow(
                title = "HTTPS-only mode",
                checked = httpsOnly,
                onCheckedChange = viewModel::onHttpsOnlyToggled,
            )
            GroupDivider()
            PrefSwitchRow(
                title = "Lock incognito with biometrics",
                checked = lockIncognito,
                onCheckedChange = viewModel::onLockIncognitoToggled,
            )
        }

        Spacer(Modifier.height(OrbitSpacing.xl))
        TextButton(
            onClick = { showClearDialog = true },
            modifier = Modifier.padding(horizontal = OrbitSpacing.lg),
        ) {
            Text("Clear browsing data", color = MaterialTheme.colorScheme.error)
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear browsing data?") },
            text = { Text("Deletes your history, cookies, and cached files. Bookmarks are kept.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.onClearAllHistory()
                    onClearBrowsingData()
                    showClearDialog = false
                    Toast.makeText(context, "Browsing data cleared", Toast.LENGTH_SHORT).show()
                }) {
                    Text("Clear", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Cancel") }
            },
        )
    }
}

// ─────────────────────────────────── Downloads ───────────────────────────────────

/** Downloads category: choose between Andromeda's downloader and Android's system one. */
@Composable
private fun DownloadsSettings(
    viewModel: BrowserViewModel,
    onBack: () -> Unit,
) {
    val useSystemDownloader by viewModel.useSystemDownloader.collectAsStateWithLifecycle()

    CategoryScaffold(title = "Downloads", onBack = onBack) {
        SettingsGroup {
            PrefSwitchRow(
                title = "Use system download manager",
                checked = useSystemDownloader,
                onCheckedChange = viewModel::onUseSystemDownloaderToggled,
            )
            Caption(
                "When on, files are handed to Android's system download manager instead of " +
                    "Andromeda's built-in downloader.",
            )
        }
    }
}

// ────────────────────────────────── Advanced & about ──────────────────────────────────

/** Advanced & about category: text size, behavior toggles, bookmarks, backup/restore, version. */
@Composable
private fun AdvancedAboutSettings(
    viewModel: BrowserViewModel,
    onBack: () -> Unit,
) {
    val scheme = orbit()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val textScale by viewModel.textScale.collectAsStateWithLifecycle()
    val autoIslands by viewModel.autoIslands.collectAsStateWithLifecycle()
    val backgroundMedia by viewModel.backgroundMedia.collectAsStateWithLifecycle()
    var draftTextScale by remember { mutableStateOf<Int?>(null) }
    var pendingRestore by remember { mutableStateOf<com.udaytank.browse.browser.Backup?>(null) }

    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/html"),
    ) { uri ->
        if (uri != null) scope.launch {
            val html = viewModel.exportBookmarksHtml()
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(html.toByteArray()) }
                Toast.makeText(context, "Bookmarks exported", Toast.LENGTH_SHORT).show()
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            val html = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
            if (html != null) viewModel.importBookmarksHtml(html) { count ->
                Toast.makeText(context, "Imported $count bookmarks", Toast.LENGTH_SHORT).show()
            }
        }
    }
    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) scope.launch {
            val json = viewModel.buildBackupJson()
            val ok = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
            }.isSuccess
            Toast.makeText(
                context,
                if (ok) "Backup saved" else "Couldn't save backup",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }
    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            val json = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
            val backup = json?.let { com.udaytank.browse.browser.BackupCodec.decode(it) }
            if (backup == null) {
                Toast.makeText(context, "Not a valid Andromeda backup", Toast.LENGTH_SHORT).show()
            } else {
                pendingRestore = backup
            }
        }
    }

    CategoryScaffold(title = "Advanced & about", onBack = onBack) {
        SectionHeader("Text size")
        // Draft holds the value while dragging (live % label); DataStore is written once
        // on release — MainActivity live-applies the persisted value to all open tabs.
        val shownScale = draftTextScale ?: textScale
        SettingsGroup {
            Text(
                "Text size — ${if (shownScale == 100) "Default" else "$shownScale%"}",
                style = orbitBody,
                color = scheme.text.primary,
                modifier = Modifier.padding(horizontal = OrbitSpacing.lg, vertical = OrbitSpacing.sm),
            )
            Slider(
                value = shownScale.toFloat(),
                onValueChange = { raw -> draftTextScale = (raw / 10f).roundToInt() * 10 },
                onValueChangeFinished = {
                    draftTextScale?.let(viewModel::onTextScaleChanged)
                    draftTextScale = null
                },
                valueRange = 50f..200f,
                steps = 14, // (200 - 50) / 10 - 1: snap points every 10%
                modifier = Modifier.padding(horizontal = OrbitSpacing.lg),
            )
            Caption("Applies to every website. A per-site text size from Site settings wins.")
        }

        Spacer(Modifier.height(OrbitSpacing.xl))
        SectionHeader("Behavior")
        SettingsGroup {
            PrefSwitchRow(
                title = "Group tabs opened from links",
                checked = autoIslands,
                onCheckedChange = viewModel::onAutoIslandsToggled,
            )
            GroupDivider()
            PrefSwitchRow(
                title = "Keep media playing in background",
                checked = backgroundMedia,
                onCheckedChange = viewModel::onBackgroundMediaToggled,
            )
            Caption(
                "Keeps audio/video playing when you lock the phone or switch apps, with " +
                    "lock-screen play/pause/next controls and auto-play of the next track. " +
                    "Applies to every site (never incognito). May be stopped by aggressive " +
                    "battery savers on some phones.",
            )
        }

        Spacer(Modifier.height(OrbitSpacing.xl))
        SectionHeader("Bookmarks")
        SettingsGroup {
            TextButton(
                onClick = { exportLauncher.launch("andromeda-bookmarks.html") },
                modifier = Modifier.padding(horizontal = OrbitSpacing.sm),
            ) {
                Text("Export bookmarks")
            }
            GroupDivider()
            TextButton(
                onClick = { importLauncher.launch(arrayOf("text/html")) },
                modifier = Modifier.padding(horizontal = OrbitSpacing.sm),
            ) {
                Text("Import bookmarks")
            }
        }

        Spacer(Modifier.height(OrbitSpacing.xl))
        SectionHeader("Backup & restore")
        SettingsGroup {
            TextButton(
                onClick = {
                    val date = java.time.LocalDate.now()
                        .format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)
                    backupLauncher.launch("andromeda-backup-$date.json")
                },
                modifier = Modifier.padding(horizontal = OrbitSpacing.sm),
            ) {
                Text("Back up")
            }
            GroupDivider()
            TextButton(
                onClick = { restoreLauncher.launch(arrayOf("application/json")) },
                modifier = Modifier.padding(horizontal = OrbitSpacing.sm),
            ) {
                Text("Restore")
            }
            Caption(
                "Backs up settings, bookmarks, home shortcuts, reading list, and tab groups. " +
                    "Saved articles and browsing history stay on this device.",
            )
        }

        Spacer(Modifier.height(OrbitSpacing.xl))
        SectionHeader("About")
        SettingsGroup {
            OrbitListRow(
                leadingIcon = Icons.Filled.Info,
                title = "Andromeda",
                subtitle = if (versionName.isNotEmpty()) "Version $versionName" else "A calm, private browser",
            )
        }
    }

    pendingRestore?.let { backup ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text("Restore backup?") },
            text = { Text("Merges with your current data. Nothing is deleted; duplicates are skipped and settings take the backup's values.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.onRestoreBackup(backup) { message ->
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    }
                    pendingRestore = null
                }) {
                    Text("Restore")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestore = null }) { Text("Cancel") }
            },
        )
    }
}
