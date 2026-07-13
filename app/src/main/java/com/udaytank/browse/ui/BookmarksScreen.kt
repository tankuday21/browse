package com.udaytank.browse.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.udaytank.browse.BrowserViewModel
import com.udaytank.browse.ui.components.OrbitListRow
import com.udaytank.browse.ui.components.OrbitTopBar
import com.udaytank.browse.ui.theme.OrbitSpacing
import com.udaytank.browse.ui.theme.orbit
import com.udaytank.browse.ui.theme.orbitBody

@Composable
fun BookmarksScreen(
    viewModel: BrowserViewModel,
    onOpenUrl: (String) -> Unit,
    onBack: () -> Unit,
) {
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    val scheme = orbit()

    Scaffold(
        topBar = { OrbitTopBar(title = "Bookmarks", onBack = onBack) },
        containerColor = scheme.surfaces.base,
    ) { innerPadding ->
        if (bookmarks.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(innerPadding).padding(OrbitSpacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    Icons.Filled.Bookmarks,
                    contentDescription = null,
                    tint = scheme.text.muted,
                    modifier = Modifier.size(48.dp).padding(bottom = OrbitSpacing.md),
                )
                Text(
                    "No bookmarks yet — tap the star on any page",
                    style = orbitBody,
                    color = scheme.text.muted,
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                items(bookmarks, key = { it.id }) { bookmark ->
                    OrbitListRow(
                        leadingIcon = null,
                        title = bookmark.title,
                        subtitle = bookmark.url,
                        onClick = { onOpenUrl(bookmark.url) },
                        trailing = {
                            IconButton(onClick = { viewModel.onDeleteBookmark(bookmark.url) }) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = "Delete bookmark",
                                    tint = scheme.text.secondary,
                                )
                            }
                        },
                    )
                }
            }
        }
    }
}
