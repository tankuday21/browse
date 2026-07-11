package com.udaytank.browse.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.udaytank.browse.BrowserViewModel

@Composable
fun BrowserScreen(
    viewModel: BrowserViewModel,
    onOpenHistory: () -> Unit,
    onOpenBookmarks: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isBookmarked by viewModel.isBookmarked.collectAsStateWithLifecycle()
    val keyboard = LocalSoftwareKeyboardController.current

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
                IconButton(onClick = viewModel::onReloadPressed) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Reload")
                }
                IconButton(onClick = viewModel::onToggleBookmark, enabled = state.currentUrl != null) {
                    Icon(
                        if (isBookmarked) Icons.Filled.Star else Icons.Filled.StarBorder,
                        contentDescription = if (isBookmarked) "Remove bookmark" else "Add bookmark",
                    )
                }
                IconButton(onClick = onOpenBookmarks) {
                    Icon(Icons.Filled.Bookmarks, contentDescription = "Bookmarks")
                }
                IconButton(onClick = onOpenHistory) {
                    Icon(Icons.Filled.History, contentDescription = "History")
                }
            }
        },
    ) { innerPadding ->
        WebViewContainer(
            pendingCommand = state.pendingCommand,
            currentUrl = state.currentUrl,
            onCommandConsumed = viewModel::onCommandConsumed,
            onPageStarted = viewModel::onPageStarted,
            onProgressChanged = viewModel::onProgressChanged,
            onPageFinished = viewModel::onPageFinished,
            onHistoryChanged = viewModel::onHistoryChanged,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        )
    }
}
