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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.udaytank.browse.BrowserViewModel

@Composable
fun BrowserScreen(
    viewModel: BrowserViewModel,
    holder: WebViewHolder,
    onOpenHistory: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenTabs: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isBookmarked by viewModel.isBookmarked.collectAsStateWithLifecycle()
    val tabs by viewModel.tabs.collectAsStateWithLifecycle()
    val activeTabId by viewModel.activeTabId.collectAsStateWithLifecycle()
    val keyboard = LocalSoftwareKeyboardController.current
    var menuOpen by remember { mutableStateOf(false) }

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
                    placeholder = { Text("Search or type URL") },
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
                            text = { Text("Bookmarks") },
                            onClick = { onOpenBookmarks(); menuOpen = false },
                        )
                        DropdownMenuItem(
                            text = { Text("History") },
                            onClick = { onOpenHistory(); menuOpen = false },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        val currentTabId = activeTabId
        if (currentTabId != null) {
            val tabUrl = tabs.find { it.id == currentTabId }?.url ?: BrowserViewModel.HOME_URL
            TabWebView(
                holder = holder,
                tabId = currentTabId,
                tabUrl = tabUrl,
                pendingCommand = state.pendingCommand,
                onCommandConsumed = viewModel::onCommandConsumed,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
        }
    }
}
