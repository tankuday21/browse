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
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.udaytank.browse.BrowserViewModel
import com.udaytank.browse.browser.BookmarkFolders
import com.udaytank.browse.browser.BookmarkSearch
import com.udaytank.browse.data.Bookmark
import com.udaytank.browse.data.OrbitEntity
import com.udaytank.browse.ui.components.FaviconOrLetter
import com.udaytank.browse.ui.components.OrbitAvatar
import com.udaytank.browse.ui.components.OrbitScopeHeader
import com.udaytank.browse.ui.components.OrbitTextField
import com.udaytank.browse.ui.components.OrbitTopBar
import com.udaytank.browse.ui.theme.OrbitSpacing
import com.udaytank.browse.ui.theme.orbit
import com.udaytank.browse.ui.theme.orbitBody
import com.udaytank.browse.ui.theme.orbitCaption

/**
 * Bookmarks — Orbit "library" screen. Since v6.10 the list is grouped into collapsible folders
 * (via [BookmarkFolders.sections]); each row can be moved between folders (or to a new one) from a
 * dropdown. A folder exists exactly while a bookmark references it — clearing the last one's folder
 * makes the section disappear.
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
    val activeOrbit = remember(orbits, activeOrbitId) { orbits.firstOrNull { it.id == activeOrbitId } }
    val scheme = orbit()

    val sections = remember(bookmarks) { BookmarkFolders.sections(bookmarks) }
    val allFolders = remember(bookmarks) { BookmarkFolders.folders(bookmarks) }
    val hasNamedFolders = sections.any { it.first != null }
    // Collapse state per folder, defaulting to expanded (absent = expanded). Pruned to the folders
    // that currently exist so a removed-then-recreated folder doesn't inherit a stale collapse.
    val collapsed = remember { mutableStateMapOf<String, Boolean>() }
    LaunchedEffect(allFolders) { collapsed.keys.retainAll(allFolders.toSet()) }
    // When non-null, the new-folder dialog is open for this bookmark URL.
    var newFolderForUrl by remember { mutableStateOf<String?>(null) }
    // v6.12 search: blank = grouped folder view; non-blank = flat filtered list.
    var query by remember { mutableStateOf("") }

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
            Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                OrbitTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = "Search bookmarks",
                    leadingIcon = Icons.Filled.Search,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = OrbitSpacing.lg, vertical = OrbitSpacing.sm),
                )
                val q = query.trim()
                if (q.isEmpty()) {
                    LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        sections.forEach { (folder, rows) ->
                            // Header keys ("hdr:") and the ungrouped key are in a disjoint namespace
                            // from both named-folder keys and the Long row keys, so no user folder
                            // name (even literally "__ungrouped__") can collide.
                            if (folder != null) {
                                item(key = "hdr:$folder") {
                                    FolderHeader(
                                        name = folder,
                                        count = rows.size,
                                        collapsed = collapsed[folder] == true,
                                        onToggle = { collapsed[folder] = !(collapsed[folder] ?: false) },
                                    )
                                }
                            } else if (hasNamedFolders) {
                                item(key = "hdr-ungrouped") { UngroupedHeader() }
                            }
                            if (folder == null || collapsed[folder] != true) {
                                items(rows, key = { it.id }) { bookmark ->
                                    BookmarkRow(
                                        bookmark = bookmark,
                                        folders = allFolders,
                                        onClick = { onOpenUrl(bookmark.url) },
                                        onMoveToFolder = { viewModel.onSetBookmarkFolder(bookmark.url, it) },
                                        onNewFolder = { newFolderForUrl = bookmark.url },
                                        onDelete = { viewModel.onDeleteBookmark(bookmark.url) },
                                    )
                                }
                            }
                        }
                    }
                } else {
                    val filtered = BookmarkSearch.filter(bookmarks, q)
                    if (filtered.isEmpty()) {
                        Text(
                            "No bookmarks match “$q”",
                            style = orbitBody,
                            color = scheme.text.muted,
                            modifier = Modifier.fillMaxWidth().padding(OrbitSpacing.lg),
                        )
                    } else {
                        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            items(filtered, key = { it.id }) { bookmark ->
                                BookmarkRow(
                                    bookmark = bookmark,
                                    folders = allFolders,
                                    onClick = { onOpenUrl(bookmark.url) },
                                    onMoveToFolder = { viewModel.onSetBookmarkFolder(bookmark.url, it) },
                                    onNewFolder = { newFolderForUrl = bookmark.url },
                                    onDelete = { viewModel.onDeleteBookmark(bookmark.url) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    newFolderForUrl?.let { url ->
        NewFolderDialog(
            onConfirm = { name ->
                viewModel.onSetBookmarkFolder(url, name)
                newFolderForUrl = null
            },
            onDismiss = { newFolderForUrl = null },
        )
    }
}

@Composable
private fun FolderHeader(name: String, count: Int, collapsed: Boolean, onToggle: () -> Unit) {
    val scheme = orbit()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onToggle)
            .padding(horizontal = OrbitSpacing.lg, vertical = OrbitSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OrbitSpacing.md),
    ) {
        Icon(Icons.Filled.Folder, contentDescription = null, tint = scheme.accent.solid)
        Text(name, style = orbitBody, color = scheme.text.primary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text("$count", style = orbitCaption, color = scheme.text.muted)
        Icon(
            if (collapsed) Icons.Filled.ExpandMore else Icons.Filled.ExpandLess,
            contentDescription = if (collapsed) "Expand $name" else "Collapse $name",
            tint = scheme.text.secondary,
        )
    }
}

@Composable
private fun UngroupedHeader() {
    val scheme = orbit()
    Text(
        "Ungrouped",
        style = orbitCaption,
        color = scheme.text.muted,
        modifier = Modifier.fillMaxWidth().padding(start = OrbitSpacing.lg, end = OrbitSpacing.lg, top = OrbitSpacing.md, bottom = OrbitSpacing.xs),
    )
}

@Composable
private fun BookmarkRow(
    bookmark: Bookmark,
    folders: List<String>,
    onClick: () -> Unit,
    onMoveToFolder: (String?) -> Unit,
    onNewFolder: () -> Unit,
    onDelete: () -> Unit,
) {
    val scheme = orbit()
    var menuOpen by remember { mutableStateOf(false) }
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
            Text(bookmark.title, style = orbitBody, color = scheme.text.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(bookmark.url, style = orbitCaption, color = scheme.text.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Filled.Folder, contentDescription = "Move to folder", tint = scheme.text.secondary)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                val current = BookmarkFolders.normalize(bookmark.folder)
                if (current != null) {
                    DropdownMenuItem(
                        text = { Text("Remove from folder") },
                        leadingIcon = { Icon(Icons.Filled.FolderOff, contentDescription = null) },
                        onClick = { onMoveToFolder(null); menuOpen = false },
                    )
                }
                folders.filter { it != current }.forEach { folder ->
                    DropdownMenuItem(
                        text = { Text(folder) },
                        leadingIcon = { Icon(Icons.Filled.Folder, contentDescription = null) },
                        onClick = { onMoveToFolder(folder); menuOpen = false },
                    )
                }
                DropdownMenuItem(
                    text = { Text("New folder…") },
                    leadingIcon = { Icon(Icons.Filled.CreateNewFolder, contentDescription = null) },
                    onClick = { menuOpen = false; onNewFolder() },
                )
            }
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = "Delete bookmark", tint = scheme.text.secondary)
        }
    }
}

@Composable
private fun NewFolderDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New folder") },
        text = {
            OrbitTextField(
                value = name,
                onValueChange = { name = it },
                label = "Folder name",
                placeholder = "Reading, Work, …",
                onImeAction = { if (name.isNotBlank()) onConfirm(name) },
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text("Move here") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
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
            Surface(shape = CircleShape, color = scheme.surfaces.elevated, modifier = Modifier.size(88.dp)) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(Icons.Filled.Bookmarks, contentDescription = null, tint = scheme.text.secondary, modifier = Modifier.size(36.dp))
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
