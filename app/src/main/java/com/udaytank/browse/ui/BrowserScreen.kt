package com.udaytank.browse.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import android.content.Intent
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.udaytank.browse.BrowserViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    viewModel: BrowserViewModel,
    holder: WebViewHolder,
    onOpenHistory: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenTabs: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isBookmarked by viewModel.isBookmarked.collectAsStateWithLifecycle()
    val bookmarksList by viewModel.bookmarks.collectAsStateWithLifecycle()
    val tabs by viewModel.tabs.collectAsStateWithLifecycle()
    val activeTabId by viewModel.activeTabId.collectAsStateWithLifecycle()
    val keyboard = LocalSoftwareKeyboardController.current
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    val activeTab = tabs.find { it.id == activeTabId }
    val isIncognito = activeTab?.isIncognito == true
    val blockedCounts by viewModel.blockedCounts.collectAsStateWithLifecycle()
    val adAllowedSites by viewModel.adAllowedSites.collectAsStateWithLifecycle()
    val blockedOnPage = blockedCounts[activeTabId] ?: 0
    val currentHost = viewModel.currentHost()

    // System back button navigates page history before exiting the app.
    BackHandler(enabled = state.canGoBack) { viewModel.onBackPressed() }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.statusBarsPadding()) {
                OutlinedTextField(
                    value = state.addressBarText,
                    onValueChange = viewModel::onAddressBarTextChanged,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    singleLine = true,
                    placeholder = { Text(if (isIncognito) "Search privately" else "Search or type URL") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = {
                        viewModel.onGoPressed()
                        keyboard?.hide()
                    }),
                )
                if (state.isLoading) {
                    LinearProgressIndicator(
                        progress = { state.progress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
            ) {
                IconButton(onClick = viewModel::onBackPressed, enabled = state.canGoBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                IconButton(onClick = viewModel::onForwardPressed, enabled = state.canGoForward) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Forward")
                }
                IconButton(onClick = onOpenTabs) {
                    Text(
                        text = "${tabs.size}",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier
                            .border(1.5.dp, LocalContentColor.current, RoundedCornerShape(4.dp))
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                    )
                }
                IconButton(onClick = viewModel::onToggleBookmark, enabled = state.currentUrl != null) {
                    Icon(
                        if (isBookmarked) Icons.Filled.Star else Icons.Filled.StarBorder,
                        contentDescription = if (isBookmarked) "Remove bookmark" else "Add bookmark",
                    )
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Menu")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Reload") },
                            onClick = { viewModel.onReloadPressed(); menuOpen = false },
                        )
                        DropdownMenuItem(
                            text = { Text("New incognito tab") },
                            onClick = { viewModel.onNewIncognitoTab(); menuOpen = false },
                        )
                        DropdownMenuItem(
                            text = { Text("Bookmarks") },
                            onClick = { onOpenBookmarks(); menuOpen = false },
                        )
                        DropdownMenuItem(
                            text = { Text("History") },
                            onClick = { onOpenHistory(); menuOpen = false },
                        )
                        DropdownMenuItem(
                            text = { Text("Settings") },
                            onClick = { onOpenSettings(); menuOpen = false },
                        )
                        if (currentHost != null) {
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "$blockedOnPage ads blocked on this page",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                },
                                onClick = { menuOpen = false },
                                enabled = false,
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (currentHost in adAllowedSites) "Block ads on this site"
                                        else "Allow ads on this site"
                                    )
                                },
                                onClick = {
                                    viewModel.onToggleAllowAdsOnCurrentSite()
                                    viewModel.onReloadPressed()
                                    menuOpen = false
                                },
                            )
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        val currentTabId = activeTabId
        if (currentTabId != null) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (activeTab == null || activeTab.url == BrowserViewModel.HOME_URL) {
                    HomePage(
                        bookmarks = bookmarksList,
                        isIncognito = isIncognito,
                        onOpenUrl = viewModel::onOpenUrl,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                    )
                } else {
                    TabWebView(
                        holder = holder,
                        tabId = currentTabId,
                        tabUrl = activeTab.url,
                        incognito = isIncognito,
                        pendingCommand = state.pendingCommand,
                        onCommandConsumed = viewModel::onCommandConsumed,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                    )
                }
                state.contextMenu?.let { menu ->
                    ModalBottomSheet(onDismissRequest = viewModel::onContextMenuDismissed) {
                        Text(
                            menu.url,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                        ListItem(
                            headlineContent = { Text("Open in new tab") },
                            modifier = Modifier.clickable { viewModel.onOpenInNewTab(menu.url) },
                        )
                        if (menu.isImage) {
                            ListItem(
                                headlineContent = { Text("Download image") },
                                modifier = Modifier.clickable {
                                    holder.downloadFile(menu.url)
                                    viewModel.onContextMenuDismissed()
                                },
                            )
                        }
                        ListItem(
                            headlineContent = { Text("Copy link") },
                            modifier = Modifier.clickable {
                                clipboard.setText(AnnotatedString(menu.url))
                                viewModel.onContextMenuDismissed()
                            },
                        )
                        ListItem(
                            headlineContent = { Text("Share") },
                            modifier = Modifier.clickable {
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, menu.url)
                                }
                                context.startActivity(Intent.createChooser(send, "Share link"))
                                viewModel.onContextMenuDismissed()
                            },
                        )
                    }
                }
                state.sslWarningUrl?.let { blockedUrl ->
                    Card(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(
                                "Connection not secure",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                            Text(
                                "Browse blocked $blockedUrl because its security certificate is not trustworthy.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                            TextButton(onClick = viewModel::onSslWarningDismissed) {
                                Text("OK")
                            }
                        }
                    }
                }
            }
        }
    }
}
