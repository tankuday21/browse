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
import android.widget.Toast
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.udaytank.browse.BrowserViewModel
import com.udaytank.browse.data.SearchEngine
import com.udaytank.browse.data.ThemeMode

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
    val safeBrowsing by viewModel.safeBrowsing.collectAsStateWithLifecycle()
    val dismissCookieBanners by viewModel.dismissCookieBanners.collectAsStateWithLifecycle()
    val forceDark by viewModel.forceDark.collectAsStateWithLifecycle()
    val httpsOnly by viewModel.httpsOnly.collectAsStateWithLifecycle()
    val lockIncognito by viewModel.lockIncognito.collectAsStateWithLifecycle()
    val autoIslands by viewModel.autoIslands.collectAsStateWithLifecycle()
    val backgroundMedia by viewModel.backgroundMedia.collectAsStateWithLifecycle()
    var showClearDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

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
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                "General",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(16.dp),
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
                modifier = Modifier.padding(horizontal = 8.dp),
            ) {
                Text("Set Andromeda as default browser")
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text(
                "Search engine",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(16.dp),
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
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    RadioButton(selected = engine == option, onClick = null)
                    Text(option.label, modifier = Modifier.padding(start = 8.dp))
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text(
                "Theme",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(16.dp),
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
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    RadioButton(selected = theme == option, onClick = null)
                    Text(
                        option.name.lowercase().replaceFirstChar { it.uppercase() },
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text(
                "Tabs",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(16.dp),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                Text("Group tabs opened from links", modifier = Modifier.weight(1f))
                Switch(checked = autoIslands, onCheckedChange = viewModel::onAutoIslandsToggled)
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text(
                "Media",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(16.dp),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                Text("Background playback (experimental)", modifier = Modifier.weight(1f))
                Switch(checked = backgroundMedia, onCheckedChange = viewModel::onBackgroundMediaToggled)
            }
            Text(
                "Allow per site from the page menu. May be stopped by battery savers.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text(
                "Privacy",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(16.dp),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                Text("Block ads", modifier = Modifier.weight(1f))
                Switch(checked = adBlockEnabled, onCheckedChange = viewModel::onAdBlockToggled)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                Text("Safe Browsing", modifier = Modifier.weight(1f))
                Switch(checked = safeBrowsing, onCheckedChange = viewModel::onSafeBrowsingToggled)
            }
            Text(
                "Warns you before phishing and malware sites (Google Safe Browsing)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                Text("Auto-dismiss cookie banners", modifier = Modifier.weight(1f))
                Switch(checked = dismissCookieBanners, onCheckedChange = viewModel::onDismissCookieBannersToggled)
            }
            Text(
                "Hides consent pop-ups (may affect some sites)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                Text("JavaScript", modifier = Modifier.weight(1f))
                Switch(checked = jsEnabled, onCheckedChange = viewModel::onJavaScriptToggled)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                Text("Accept cookies", modifier = Modifier.weight(1f))
                Switch(checked = cookiesEnabled, onCheckedChange = viewModel::onCookiesToggled)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                Text("Dark mode for websites", modifier = Modifier.weight(1f))
                Switch(checked = forceDark, onCheckedChange = viewModel::onForceDarkToggled)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                Text("HTTPS-only mode", modifier = Modifier.weight(1f))
                Switch(checked = httpsOnly, onCheckedChange = viewModel::onHttpsOnlyToggled)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                Text("Lock incognito with biometrics", modifier = Modifier.weight(1f))
                Switch(checked = lockIncognito, onCheckedChange = viewModel::onLockIncognitoToggled)
            }
            TextButton(
                onClick = { showClearDialog = true },
                modifier = Modifier.padding(horizontal = 8.dp),
            ) {
                Text("Clear browsing data", color = MaterialTheme.colorScheme.error)
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text(
                "Bookmarks",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(16.dp),
            )
            TextButton(
                onClick = { exportLauncher.launch("andromeda-bookmarks.html") },
                modifier = Modifier.padding(horizontal = 8.dp),
            ) {
                Text("Export bookmarks")
            }
            TextButton(
                onClick = { importLauncher.launch(arrayOf("text/html")) },
                modifier = Modifier.padding(horizontal = 8.dp),
            ) {
                Text("Import bookmarks")
            }
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
