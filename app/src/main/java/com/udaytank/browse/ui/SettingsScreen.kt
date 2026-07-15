package com.udaytank.browse.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.udaytank.browse.BrowserViewModel
import com.udaytank.browse.browser.adblock.FilterListUpdater
import com.udaytank.browse.browser.adblock.FilterLists
import com.udaytank.browse.data.SearchEngine
import com.udaytank.browse.data.ShortcutDensity
import com.udaytank.browse.data.ThemeMode
import com.udaytank.browse.ui.theme.OrbitSpacing
import com.udaytank.browse.ui.theme.orbit
import com.udaytank.browse.ui.theme.orbitBody
import com.udaytank.browse.ui.theme.orbitCaption
import com.udaytank.browse.ui.theme.orbitTitle
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: BrowserViewModel,
    onClearBrowsingData: () -> Unit,
    onBack: () -> Unit,
) {
    val engine by viewModel.searchEngine.collectAsStateWithLifecycle()
    val theme by viewModel.themeMode.collectAsStateWithLifecycle()
    val jsEnabled by viewModel.javaScriptEnabled.collectAsStateWithLifecycle()
    val cookiesEnabled by viewModel.cookiesEnabled.collectAsStateWithLifecycle()
    val adBlockEnabled by viewModel.adBlockEnabled.collectAsStateWithLifecycle()
    val adBlockLists by viewModel.adBlockLists.collectAsStateWithLifecycle()
    val adBlockLastUpdated by viewModel.adBlockLastUpdated.collectAsStateWithLifecycle()
    val safeBrowsing by viewModel.safeBrowsing.collectAsStateWithLifecycle()
    val dismissCookieBanners by viewModel.dismissCookieBanners.collectAsStateWithLifecycle()
    val gpcEnabled by viewModel.gpcEnabled.collectAsStateWithLifecycle()
    val forceDark by viewModel.forceDark.collectAsStateWithLifecycle()
    val httpsOnly by viewModel.httpsOnly.collectAsStateWithLifecycle()
    val lockIncognito by viewModel.lockIncognito.collectAsStateWithLifecycle()
    val autoIslands by viewModel.autoIslands.collectAsStateWithLifecycle()
    val backgroundMedia by viewModel.backgroundMedia.collectAsStateWithLifecycle()
    val textScale by viewModel.textScale.collectAsStateWithLifecycle()
    val showGreeting by viewModel.showGreeting.collectAsStateWithLifecycle()
    val showHomeStats by viewModel.showHomeStats.collectAsStateWithLifecycle()
    val shortcutDensity by viewModel.shortcutDensity.collectAsStateWithLifecycle()
    val homeWallpaper by viewModel.homeWallpaper.collectAsStateWithLifecycle()
    val showFeed by viewModel.showFeed.collectAsStateWithLifecycle()
    val showWeather by viewModel.showWeather.collectAsStateWithLifecycle()
    val weatherCity by viewModel.weatherCity.collectAsStateWithLifecycle()
    val weatherUseLocation by viewModel.weatherUseLocation.collectAsStateWithLifecycle()
    // Enabling location-based weather requests the coarse permission; the grant decides the pref.
    val locationPermLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { granted -> viewModel.onWeatherUseLocationToggled(granted) }
    var draftTextScale by remember { mutableStateOf<Int?>(null) }
    var showClearDialog by remember { mutableStateOf(false) }
    var pendingRestore by remember { mutableStateOf<com.udaytank.browse.browser.Backup?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scheme = orbit()

    val exportLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("text/html")
    ) { uri ->
        if (uri != null) scope.launch {
            val html = viewModel.exportBookmarksHtml()
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(html.toByteArray()) }
                Toast.makeText(context, "Bookmarks exported", Toast.LENGTH_SHORT).show()
            }
        }
    }
    val importLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
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

    val backupLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json")
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
    val restoreLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        containerColor = scheme.surfaces.base,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                "General",
                style = orbitTitle,
                color = scheme.accent.solid,
                modifier = Modifier.padding(OrbitSpacing.lg),
            )
            TextButton(
                onClick = {
                    val intent = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        val roleManager = context.getSystemService(android.app.role.RoleManager::class.java)
                        if (roleManager != null && roleManager.isRoleAvailable(android.app.role.RoleManager.ROLE_BROWSER) &&
                            !roleManager.isRoleHeld(android.app.role.RoleManager.ROLE_BROWSER)
                        ) {
                            roleManager.createRequestRoleIntent(android.app.role.RoleManager.ROLE_BROWSER)
                        } else {
                            android.content.Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
                        }
                    } else {
                        android.content.Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
                    }
                    runCatching { context.startActivity(intent) }
                },
                modifier = Modifier.padding(horizontal = OrbitSpacing.sm),
            ) {
                Text("Set Andromeda as default browser")
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = OrbitSpacing.sm))
            Text(
                "Search engine",
                style = orbitTitle,
                color = scheme.accent.solid,
                modifier = Modifier.padding(OrbitSpacing.lg),
            )
            SearchEngine.entries.forEach { option ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = engine == option,
                            onClick = { viewModel.onSearchEngineSelected(option) },
                        )
                        .padding(horizontal = OrbitSpacing.lg, vertical = OrbitSpacing.xs),
                ) {
                    RadioButton(selected = engine == option, onClick = null)
                    Text(
                        option.label,
                        style = orbitBody,
                        color = scheme.text.primary,
                        modifier = Modifier.padding(start = OrbitSpacing.sm),
                    )
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = OrbitSpacing.sm))
            Text(
                "Theme",
                style = orbitTitle,
                color = scheme.accent.solid,
                modifier = Modifier.padding(OrbitSpacing.lg),
            )
            ThemeMode.entries.forEach { option ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = theme == option,
                            onClick = { viewModel.onThemeSelected(option) },
                        )
                        .padding(horizontal = OrbitSpacing.lg, vertical = OrbitSpacing.xs),
                ) {
                    RadioButton(selected = theme == option, onClick = null)
                    Text(
                        option.name.lowercase().replaceFirstChar { it.uppercase() },
                        style = orbitBody,
                        color = scheme.text.primary,
                        modifier = Modifier.padding(start = OrbitSpacing.sm),
                    )
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = OrbitSpacing.sm))
            Text(
                "Accessibility",
                style = orbitTitle,
                color = scheme.accent.solid,
                modifier = Modifier.padding(OrbitSpacing.lg),
            )
            // Draft holds the value while dragging (live % label); DataStore is written once
            // on release — MainActivity live-applies the persisted value to all open tabs.
            val shownScale = draftTextScale ?: textScale
            Text(
                "Text size — ${if (shownScale == 100) "Default" else "$shownScale%"}",
                style = orbitBody,
                color = scheme.text.primary,
                modifier = Modifier.padding(horizontal = OrbitSpacing.lg),
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
            Text(
                "Applies to every website. A per-site text size from Site settings wins.",
                style = orbitCaption,
                color = scheme.text.muted,
                modifier = Modifier.padding(horizontal = OrbitSpacing.lg),
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = OrbitSpacing.sm))
            Text(
                "Home",
                style = orbitTitle,
                color = scheme.accent.solid,
                modifier = Modifier.padding(OrbitSpacing.lg),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = OrbitSpacing.lg, vertical = OrbitSpacing.xs),
            ) {
                Text("Show greeting", style = orbitBody, color = scheme.text.primary, modifier = Modifier.weight(1f))
                Switch(checked = showGreeting, onCheckedChange = viewModel::onShowGreetingToggled)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = OrbitSpacing.lg, vertical = OrbitSpacing.xs),
            ) {
                Text(
                    "Show privacy stats on home",
                    style = orbitBody,
                    color = scheme.text.primary,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = showHomeStats, onCheckedChange = viewModel::onShowHomeStatsToggled)
            }
            Text(
                "Shortcut density",
                style = orbitBody,
                color = scheme.text.primary,
                modifier = Modifier.padding(horizontal = OrbitSpacing.lg, vertical = OrbitSpacing.xs),
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
            Text(
                "One calm row (Few) or your full shortcut grid (More) on the home canvas.",
                style = orbitCaption,
                color = scheme.text.muted,
                modifier = Modifier.padding(horizontal = OrbitSpacing.lg),
            )
            Text(
                "Wallpaper",
                style = orbitBody,
                color = scheme.text.primary,
                modifier = Modifier.padding(horizontal = OrbitSpacing.lg, vertical = OrbitSpacing.xs),
            )
            listOf("" to "None", "aurora" to "Aurora", "nebula" to "Nebula").forEach { (id, label) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = homeWallpaper == id,
                            onClick = { viewModel.onHomeWallpaperSelected(id) },
                        )
                        .padding(horizontal = OrbitSpacing.lg, vertical = OrbitSpacing.xs),
                ) {
                    RadioButton(selected = homeWallpaper == id, onClick = null)
                    Text(
                        label,
                        style = orbitBody,
                        color = scheme.text.primary,
                        modifier = Modifier.padding(start = OrbitSpacing.sm),
                    )
                }
            }
            // ── v3.2 feed controls ──
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = OrbitSpacing.lg, vertical = OrbitSpacing.xs),
            ) {
                Text("Show news, sports & weather feed", style = orbitBody, color = scheme.text.primary, modifier = Modifier.weight(1f))
                Switch(checked = showFeed, onCheckedChange = viewModel::onShowFeedToggled)
            }
            if (showFeed) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = OrbitSpacing.lg, vertical = OrbitSpacing.xs),
                ) {
                    Text("Weather card", style = orbitBody, color = scheme.text.primary, modifier = Modifier.weight(1f))
                    Switch(checked = showWeather, onCheckedChange = viewModel::onShowWeatherToggled)
                }
                if (showWeather) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = OrbitSpacing.lg, vertical = OrbitSpacing.xs),
                    ) {
                        Text("Use my location", style = orbitBody, color = scheme.text.primary, modifier = Modifier.weight(1f))
                        Switch(
                            checked = weatherUseLocation,
                            onCheckedChange = { on ->
                                if (on) {
                                    val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                                        context,
                                        android.Manifest.permission.ACCESS_COARSE_LOCATION,
                                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                    if (granted) viewModel.onWeatherUseLocationToggled(true)
                                    else locationPermLauncher.launch(android.Manifest.permission.ACCESS_COARSE_LOCATION)
                                } else {
                                    viewModel.onWeatherUseLocationToggled(false)
                                }
                            },
                        )
                    }
                    if (!weatherUseLocation) {
                        OutlinedTextField(
                            value = weatherCity,
                            onValueChange = viewModel::onWeatherCityChanged,
                            label = { Text("City for weather") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = OrbitSpacing.lg, vertical = OrbitSpacing.xs),
                        )
                    }
                }
                Text(
                    "News & sports come from public RSS feeds, fetched straight from the source. " +
                        "Weather uses Open-Meteo. No tracking, no accounts.",
                    style = orbitCaption,
                    color = scheme.text.muted,
                    modifier = Modifier.padding(horizontal = OrbitSpacing.lg, vertical = OrbitSpacing.xs),
                )
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = OrbitSpacing.sm))
            Text(
                "Tabs",
                style = orbitTitle,
                color = scheme.accent.solid,
                modifier = Modifier.padding(OrbitSpacing.lg),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = OrbitSpacing.lg, vertical = OrbitSpacing.xs),
            ) {
                Text(
                    "Group tabs opened from links",
                    style = orbitBody,
                    color = scheme.text.primary,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = autoIslands, onCheckedChange = viewModel::onAutoIslandsToggled)
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = OrbitSpacing.sm))
            Text(
                "Media",
                style = orbitTitle,
                color = scheme.accent.solid,
                modifier = Modifier.padding(OrbitSpacing.lg),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = OrbitSpacing.lg, vertical = OrbitSpacing.xs),
            ) {
                Text(
                    "Keep media playing in background",
                    style = orbitBody,
                    color = scheme.text.primary,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = backgroundMedia, onCheckedChange = viewModel::onBackgroundMediaToggled)
            }
            Text(
                "Keeps audio/video playing when you lock the phone or switch apps, with " +
                    "lock-screen play/pause/next controls and auto-play of the next track. " +
                    "Applies to every site (never incognito). May be stopped by aggressive " +
                    "battery savers on some phones.",
                style = orbitCaption,
                color = scheme.text.muted,
                modifier = Modifier.padding(horizontal = OrbitSpacing.lg),
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = OrbitSpacing.sm))
            Text(
                "Privacy",
                style = orbitTitle,
                color = scheme.accent.solid,
                modifier = Modifier.padding(OrbitSpacing.lg),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = OrbitSpacing.lg, vertical = OrbitSpacing.xs),
            ) {
                Text("Block ads", style = orbitBody, color = scheme.text.primary, modifier = Modifier.weight(1f))
                Switch(checked = adBlockEnabled, onCheckedChange = viewModel::onAdBlockToggled)
            }
            FilterLists.ADS.forEach { def ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
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
            TextButton(
                onClick = {
                    FilterListUpdater.updateNow(context)
                    Toast.makeText(context, "Updating filter lists…", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.padding(horizontal = OrbitSpacing.sm),
            ) {
                Text("Update filter lists")
            }
            Text(
                if (adBlockLastUpdated == 0L) {
                    "Using bundled lists — never updated"
                } else {
                    "Updated " + DateUtils.getRelativeTimeSpanString(
                        adBlockLastUpdated,
                        System.currentTimeMillis(),
                        DateUtils.MINUTE_IN_MILLIS,
                    )
                },
                style = orbitCaption,
                color = scheme.text.muted,
                modifier = Modifier.padding(horizontal = OrbitSpacing.lg),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = OrbitSpacing.lg, vertical = OrbitSpacing.xs),
            ) {
                Text("Safe Browsing", style = orbitBody, color = scheme.text.primary, modifier = Modifier.weight(1f))
                Switch(checked = safeBrowsing, onCheckedChange = viewModel::onSafeBrowsingToggled)
            }
            Text(
                "Warns you before phishing and malware sites (Google Safe Browsing)",
                style = orbitCaption,
                color = scheme.text.muted,
                modifier = Modifier.padding(horizontal = OrbitSpacing.lg),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = OrbitSpacing.lg, vertical = OrbitSpacing.xs),
            ) {
                Text(
                    "Auto-dismiss cookie banners",
                    style = orbitBody,
                    color = scheme.text.primary,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = dismissCookieBanners, onCheckedChange = viewModel::onDismissCookieBannersToggled)
            }
            Text(
                "Hides consent pop-ups (may affect some sites)",
                style = orbitCaption,
                color = scheme.text.muted,
                modifier = Modifier.padding(horizontal = OrbitSpacing.lg),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = OrbitSpacing.lg, vertical = OrbitSpacing.xs),
            ) {
                Text(
                    "Global Privacy Control",
                    style = orbitBody,
                    color = scheme.text.primary,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = gpcEnabled, onCheckedChange = viewModel::onGpcToggled)
            }
            Text(
                "Tells sites not to sell or share your data (takes effect on new tabs)",
                style = orbitCaption,
                color = scheme.text.muted,
                modifier = Modifier.padding(horizontal = OrbitSpacing.lg),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = OrbitSpacing.lg, vertical = OrbitSpacing.xs),
            ) {
                Text("JavaScript", style = orbitBody, color = scheme.text.primary, modifier = Modifier.weight(1f))
                Switch(checked = jsEnabled, onCheckedChange = viewModel::onJavaScriptToggled)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = OrbitSpacing.lg, vertical = OrbitSpacing.xs),
            ) {
                Text("Accept cookies", style = orbitBody, color = scheme.text.primary, modifier = Modifier.weight(1f))
                Switch(checked = cookiesEnabled, onCheckedChange = viewModel::onCookiesToggled)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = OrbitSpacing.lg, vertical = OrbitSpacing.xs),
            ) {
                Text(
                    "Dark mode for websites",
                    style = orbitBody,
                    color = scheme.text.primary,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = forceDark, onCheckedChange = viewModel::onForceDarkToggled)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = OrbitSpacing.lg, vertical = OrbitSpacing.xs),
            ) {
                Text("HTTPS-only mode", style = orbitBody, color = scheme.text.primary, modifier = Modifier.weight(1f))
                Switch(checked = httpsOnly, onCheckedChange = viewModel::onHttpsOnlyToggled)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = OrbitSpacing.lg, vertical = OrbitSpacing.xs),
            ) {
                Text(
                    "Lock incognito with biometrics",
                    style = orbitBody,
                    color = scheme.text.primary,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = lockIncognito, onCheckedChange = viewModel::onLockIncognitoToggled)
            }
            TextButton(
                onClick = { showClearDialog = true },
                modifier = Modifier.padding(horizontal = OrbitSpacing.sm),
            ) {
                Text("Clear browsing data", color = MaterialTheme.colorScheme.error)
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = OrbitSpacing.sm))
            Text(
                "Bookmarks",
                style = orbitTitle,
                color = scheme.accent.solid,
                modifier = Modifier.padding(OrbitSpacing.lg),
            )
            TextButton(
                onClick = { exportLauncher.launch("andromeda-bookmarks.html") },
                modifier = Modifier.padding(horizontal = OrbitSpacing.sm),
            ) {
                Text("Export bookmarks")
            }
            TextButton(
                onClick = { importLauncher.launch(arrayOf("text/html")) },
                modifier = Modifier.padding(horizontal = OrbitSpacing.sm),
            ) {
                Text("Import bookmarks")
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = OrbitSpacing.sm))
            Text(
                "Backup & restore",
                style = orbitTitle,
                color = scheme.accent.solid,
                modifier = Modifier.padding(OrbitSpacing.lg),
            )
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
            TextButton(
                onClick = { restoreLauncher.launch(arrayOf("application/json")) },
                modifier = Modifier.padding(horizontal = OrbitSpacing.sm),
            ) {
                Text("Restore")
            }
            Text(
                "Backs up settings, bookmarks, home shortcuts, reading list, and tab groups. " +
                    "Saved articles and browsing history stay on this device.",
                style = orbitCaption,
                color = scheme.text.muted,
                modifier = Modifier.padding(horizontal = OrbitSpacing.lg),
            )
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

        if (showClearDialog) {
            AlertDialog(
                onDismissRequest = { showClearDialog = false },
                title = { Text("Clear browsing data?") },
                text = { Text("Deletes your history, cookies, and cached files. Bookmarks are kept.") },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.onClearHistory()
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
}
