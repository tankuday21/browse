package com.udaytank.browse.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.udaytank.browse.BrowserViewModel
import com.udaytank.browse.data.Bookmark
import com.udaytank.browse.data.OrbitEntity
import com.udaytank.browse.ui.components.FaviconOrLetter
import com.udaytank.browse.ui.components.OrbitAvatar
import com.udaytank.browse.ui.components.OrbitScopeHeader
import com.udaytank.browse.ui.components.OrbitTopBar
import com.udaytank.browse.ui.theme.OrbitSpacing
import com.udaytank.browse.ui.theme.orbit
import com.udaytank.browse.ui.theme.orbitBody
import com.udaytank.browse.ui.theme.orbitCaption

/**
 * Bookmarks — Orbit "library" screen. Flat tonal scaffold: [OrbitTopBar] header on the base
 * surface, rows built to match [com.udaytank.browse.ui.components.OrbitListRow]'s shape but
 * with a [FaviconOrLetter] avatar leading each one (bookmarks are always site URLs), and a
 * tonal-circle empty state matching the rest of the app (see TabSwitcherScreen's TabsEmptyState).
 */
@Composable
fun BookmarksScreen(
    viewModel: BrowserViewModel,
    onOpenUrl: (String) -> Unit,
    onBack: () -> Unit,
) {
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    val orbits by viewModel.orbits.collectAsStateWithLifecycle()
    val activeOrbitId by viewModel.activeOrbitId.collectAsStateWithLifecycle()
    // v4.4: bookmarks are Orbit-scoped — show which container's library this is.
    val activeOrbit = remember(orbits, activeOrbitId) { orbits.firstOrNull { it.id == activeOrbitId } }
    val scheme = orbit()

    Scaffold(
        topBar = {
            Column {
                OrbitTopBar(title = "Bookmarks", onBack = onBack)
                if (activeOrbit != null) OrbitScopeHeader(activeOrbit, scope = "bookmarks")
            }
        },
        containerColor = scheme.surfaces.base,
    ) { innerPadding ->
        if (bookmarks.isEmpty()) {
            BookmarksEmptyState(
                activeOrbit = activeOrbit,
                modifier = Modifier.fillMaxSize().padding(innerPadding),
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                items(bookmarks, key = { it.id }) { bookmark ->
                    BookmarkRow(
                        bookmark = bookmark,
                        onClick = { onOpenUrl(bookmark.url) },
                        onDelete = { viewModel.onDeleteBookmark(bookmark.url) },
                    )
                }
            }
        }
    }
}

@Composable
private fun BookmarkRow(
    bookmark: Bookmark,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val scheme = orbit()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = OrbitSpacing.lg, vertical = OrbitSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OrbitSpacing.lg),
    ) {
        FaviconOrLetter(url = bookmark.url, label = bookmark.title, size = 36.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                bookmark.title,
                style = orbitBody,
                color = scheme.text.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                bookmark.url,
                style = orbitCaption,
                color = scheme.text.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = "Delete bookmark",
                tint = scheme.text.secondary,
            )
        }
    }
}

@Composable
private fun BookmarksEmptyState(activeOrbit: OrbitEntity?, modifier: Modifier = Modifier) {
    val scheme = orbit()
    Column(
        modifier = modifier.padding(OrbitSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (activeOrbit != null) {
            OrbitAvatar(colorArgb = activeOrbit.colorArgb, iconKey = activeOrbit.iconKey, size = 72.dp)
        } else {
            Surface(
                shape = CircleShape,
                color = scheme.surfaces.elevated,
                modifier = Modifier.size(88.dp),
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        Icons.Filled.Bookmarks,
                        contentDescription = null,
                        tint = scheme.text.secondary,
                        modifier = Modifier.size(36.dp),
                    )
                }
            }
        }
        Text(
            if (activeOrbit != null) "No bookmarks in ${activeOrbit.name} yet — tap the star on any page"
            else "No bookmarks yet — tap the star on any page",
            style = orbitBody,
            color = scheme.text.muted,
            modifier = Modifier.padding(top = OrbitSpacing.md),
        )
    }
}
