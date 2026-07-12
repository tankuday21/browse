package com.udaytank.browse.ui

import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.udaytank.browse.BrowserViewModel
import com.udaytank.browse.browser.TabOrderPolicy
import com.udaytank.browse.browser.TabSearchFilter
import com.udaytank.browse.data.ClosedTabEntity
import com.udaytank.browse.data.TabEntity
import com.udaytank.browse.data.TabGroupEntity
import com.udaytank.browse.ui.theme.Orbit

/** Orbit's tab-group accent palette; index stored on [TabGroupEntity.color]. */
private val GroupColors = listOf(
    Color(0xFF35C3F3),
    Color(0xFF1E4FD8),
    Color(0xFF9C6BFF),
    Color(0xFF3DDC97),
    Color(0xFFFFB84D),
    Color(0xFFFF6B8A),
)

/** Flattened render order for the switcher grid: group headers interleaved with their tabs. */
private sealed class SwitcherItem {
    data class Header(val group: TabGroupEntity, val count: Int) : SwitcherItem()
    data class Tab(val tab: TabEntity) : SwitcherItem()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabSwitcherScreen(
    viewModel: BrowserViewModel,
    holder: WebViewHolder,
    onTabChosen: () -> Unit,
    onCloseTabView: (Long) -> Unit,
    onBack: () -> Unit,
) {
    val tabs by viewModel.tabs.collectAsStateWithLifecycle()
    val activeTabId by viewModel.activeTabId.collectAsStateWithLifecycle()
    val groups by viewModel.tabGroups.collectAsStateWithLifecycle()
    val recentlyClosed by viewModel.recentlyClosed.collectAsStateWithLifecycle()
    val listLayout by viewModel.switcherListLayout.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var searchQuery by remember { mutableStateOf<String?>(null) }
    var selection by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var showRecentlyClosed by remember { mutableStateOf(false) }
    var collapsedGroups by remember { mutableStateOf<Set<Long>>(emptySet()) }

    // Shared "name this group" prompt, used both for single-tab "New group…" and the
    // multi-select "Group" action.
    var groupPromptTabIds by remember { mutableStateOf<List<Long>?>(null) }
    var groupNameInput by remember { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<TabGroupEntity?>(null) }
    var renameInput by remember { mutableStateOf("") }

    val filteredTabs = TabSearchFilter.filter(tabs, searchQuery.orEmpty())
    val ordered = TabOrderPolicy.ordered(filteredTabs, groups)

    val groupById = groups.associateBy { it.id }
    val seenGroups = mutableSetOf<Long>()
    val renderItems = buildList {
        ordered.forEach { tab ->
            val gid = tab.groupId
            if (!tab.pinned && gid != null && gid in groupById && seenGroups.add(gid)) {
                val count = ordered.count { it.groupId == gid && !it.pinned }
                add(SwitcherItem.Header(groupById.getValue(gid), count))
            }
            add(SwitcherItem.Tab(tab))
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Tabs (${tabs.size})") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { searchQuery = if (searchQuery == null) "" else null }) {
                            Icon(Icons.Filled.Search, contentDescription = "Search tabs")
                        }
                        IconButton(onClick = { viewModel.onSwitcherLayoutToggled() }) {
                            Icon(
                                if (listLayout) Icons.Filled.GridView else Icons.AutoMirrored.Filled.List,
                                contentDescription = "Toggle layout",
                            )
                        }
                        IconButton(onClick = { showRecentlyClosed = true }) {
                            Icon(Icons.Filled.History, contentDescription = "Recently closed")
                        }
                    },
                )
                if (searchQuery != null) {
                    OutlinedTextField(
                        value = searchQuery.orEmpty(),
                        onValueChange = { searchQuery = it },
                        singleLine = true,
                        placeholder = { Text("Search tabs") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
        },
        floatingActionButton = {
            if (selection.isEmpty()) {
                FloatingActionButton(
                    onClick = {
                        viewModel.onNewTab()
                        onTabChosen()
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "New tab")
                }
            }
        },
        bottomBar = {
            if (selection.isNotEmpty()) {
                BottomAppBar {
                    TextButton(onClick = {
                        val closable = selection.filter { id -> tabs.find { it.id == id }?.locked != true }
                        closable.forEach { id -> onCloseTabView(id) }
                        viewModel.onCloseTabs(closable)
                        selection = emptySet()
                    }) { Text("Close (${selection.size})") }
                    TextButton(onClick = {
                        groupPromptTabIds = selection.toList()
                        groupNameInput = ""
                    }) { Text("Group") }
                    TextButton(onClick = {
                        val urls = tabs.filter { it.id in selection }.joinToString("\n") { it.url }
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, urls)
                        }
                        context.startActivity(Intent.createChooser(intent, "Share tabs"))
                    }) { Text("Share") }
                    Box(modifier = Modifier.fillMaxWidth()) {
                        IconButton(
                            onClick = { selection = emptySet() },
                            modifier = Modifier.align(Alignment.CenterEnd),
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear selection")
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        LazyVerticalGrid(
            columns = if (listLayout) GridCells.Fixed(1) else GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            renderItems.forEach { entry ->
                when (entry) {
                    is SwitcherItem.Header -> item(
                        key = "group-${entry.group.id}",
                        span = { GridItemSpan(maxLineSpan) },
                    ) {
                        GroupHeader(
                            group = entry.group,
                            count = entry.count,
                            collapsed = entry.group.id in collapsedGroups,
                            onToggleCollapse = {
                                collapsedGroups = if (entry.group.id in collapsedGroups) {
                                    collapsedGroups - entry.group.id
                                } else {
                                    collapsedGroups + entry.group.id
                                }
                            },
                            onRename = {
                                renameTarget = entry.group
                                renameInput = entry.group.name
                            },
                            onUngroup = { viewModel.onDeleteGroup(entry.group.id) },
                        )
                    }

                    is SwitcherItem.Tab -> {
                        val tab = entry.tab
                        val hiddenByCollapse = !tab.pinned && tab.groupId != null && tab.groupId in collapsedGroups
                        if (!hiddenByCollapse) {
                            item(key = tab.id) {
                                TabCard(
                                    tab = tab,
                                    isActive = tab.id == activeTabId,
                                    isSelected = tab.id in selection,
                                    selectionMode = selection.isNotEmpty(),
                                    groups = groups,
                                    holder = holder,
                                    onClick = {
                                        if (selection.isNotEmpty()) {
                                            selection = if (tab.id in selection) {
                                                selection - tab.id
                                            } else {
                                                selection + tab.id
                                            }
                                        } else {
                                            viewModel.onSwitchTab(tab.id)
                                            onTabChosen()
                                        }
                                    },
                                    onClose = {
                                        if (!tab.locked) onCloseTabView(tab.id)
                                        viewModel.onCloseTab(tab.id)
                                    },
                                    onStartSelection = { selection = setOf(tab.id) },
                                    onTogglePinned = { viewModel.onTogglePinned(tab.id) },
                                    onToggleLocked = { viewModel.onToggleLocked(tab.id) },
                                    onAssignToGroup = { groupId -> viewModel.onAssignTabToGroup(tab.id, groupId) },
                                    onRequestNewGroup = {
                                        groupPromptTabIds = listOf(tab.id)
                                        groupNameInput = ""
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showRecentlyClosed) {
        ModalBottomSheet(onDismissRequest = { showRecentlyClosed = false }) {
            if (recentlyClosed.isEmpty()) {
                Text(
                    "No recently closed tabs",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
            } else {
                Column(modifier = Modifier.padding(bottom = 16.dp)) {
                    recentlyClosed.sortedByDescending { it.closedAt }.forEach { entry: ClosedTabEntity ->
                        ListItem(
                            headlineContent = { Text(entry.title.ifBlank { entry.url }, maxLines = 1) },
                            supportingContent = { Text(entry.url, maxLines = 1) },
                            modifier = Modifier.clickable {
                                viewModel.onReopenClosed(entry)
                                showRecentlyClosed = false
                                onTabChosen()
                            },
                        )
                    }
                }
            }
        }
    }

    groupPromptTabIds?.let { ids ->
        AlertDialog(
            onDismissRequest = { groupPromptTabIds = null },
            title = { Text("New group") },
            text = {
                OutlinedTextField(
                    value = groupNameInput,
                    onValueChange = { groupNameInput = it },
                    singleLine = true,
                    label = { Text("Group name") },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (groupNameInput.isNotBlank()) {
                        viewModel.onCreateGroupWithTabs(groupNameInput.trim(), ids)
                    }
                    groupPromptTabIds = null
                }) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { groupPromptTabIds = null }) { Text("Cancel") }
            },
        )
    }

    renameTarget?.let { group ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename group") },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    singleLine = true,
                    label = { Text("Group name") },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (renameInput.isNotBlank()) viewModel.onRenameGroup(group.id, renameInput.trim())
                    renameTarget = null
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("Cancel") }
            },
        )
    }

    val pendingCloseId = uiState.confirmCloseTabId
    if (pendingCloseId != null) {
        AlertDialog(
            onDismissRequest = viewModel::onCloseCancelled,
            title = { Text("Close locked tab?") },
            text = { Text("This tab is locked. Are you sure you want to close it?") },
            confirmButton = {
                TextButton(onClick = {
                    onCloseTabView(pendingCloseId)
                    viewModel.onConfirmClose()
                }) { Text("Close") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::onCloseCancelled) { Text("Cancel") }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupHeader(
    group: TabGroupEntity,
    count: Int,
    collapsed: Boolean,
    onToggleCollapse: () -> Unit,
    onRename: () -> Unit,
    onUngroup: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onToggleCollapse() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(GroupColors[group.color % GroupColors.size]),
        )
        Text(
            "${group.name} ($count)",
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        Icon(
            if (collapsed) Icons.Filled.ExpandMore else Icons.Filled.ExpandLess,
            contentDescription = if (collapsed) "Expand group" else "Collapse group",
        )
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "Group options")
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Rename") },
                    onClick = { onRename(); menuOpen = false },
                )
                DropdownMenuItem(
                    text = { Text("Ungroup") },
                    onClick = { onUngroup(); menuOpen = false },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun TabCard(
    tab: TabEntity,
    isActive: Boolean,
    isSelected: Boolean,
    selectionMode: Boolean,
    groups: List<TabGroupEntity>,
    holder: WebViewHolder,
    onClick: () -> Unit,
    onClose: () -> Unit,
    onStartSelection: () -> Unit,
    onTogglePinned: () -> Unit,
    onToggleLocked: () -> Unit,
    onAssignToGroup: (Long?) -> Unit,
    onRequestNewGroup: () -> Unit,
) {
    val thumbnail = remember(tab.id, tab.url) {
        holder.thumbnails.load(tab.id)?.asImageBitmap()
    }
    var showMenu by remember { mutableStateOf(false) }
    var addToGroupSubmenu by remember { mutableStateOf(false) }

    Box {
        Surface(
            shape = MaterialTheme.shapes.medium,
            tonalElevation = 2.dp,
            modifier = Modifier
                .clip(MaterialTheme.shapes.medium)
                .then(
                    if (isActive) {
                        Modifier.border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium)
                    } else {
                        Modifier
                    }
                )
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { if (!selectionMode) showMenu = true },
                ),
        ) {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.9f),
                ) {
                    when {
                        tab.isIncognito -> Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "Incognito",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        thumbnail != null -> Image(
                            bitmap = thumbnail,
                            contentDescription = tab.title,
                            contentScale = ContentScale.Crop,
                            alignment = Alignment.TopCenter,
                            modifier = Modifier.fillMaxSize(),
                        )

                        else -> Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Orbit.Gradient),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                tab.title.take(1).uppercase(),
                                style = MaterialTheme.typography.displaySmall,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                    }

                    if (tab.pinned || tab.locked) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(6.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            if (tab.pinned) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                    modifier = Modifier.size(20.dp),
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                        Icon(
                                            Icons.Filled.PushPin,
                                            contentDescription = "Pinned",
                                            modifier = Modifier.size(14.dp),
                                        )
                                    }
                                }
                            }
                            if (tab.locked) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                    modifier = Modifier.size(20.dp),
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                        Icon(
                                            Icons.Filled.Lock,
                                            contentDescription = "Locked",
                                            modifier = Modifier.size(14.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(28.dp),
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxSize()
                                .then(if (selectionMode) Modifier else Modifier.clickable { onClose() }),
                        ) {
                            if (selectionMode) {
                                if (isSelected) {
                                    Icon(
                                        Icons.Filled.Check,
                                        contentDescription = "Selected",
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            } else {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "Close tab",
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }
                Text(
                    text = if (tab.isIncognito) "Incognito" else tab.title,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                )
            }
        }

        DropdownMenu(
            expanded = showMenu && !addToGroupSubmenu,
            onDismissRequest = { showMenu = false },
        ) {
            DropdownMenuItem(
                text = { Text(if (tab.pinned) "Unpin" else "Pin") },
                leadingIcon = { Icon(Icons.Filled.PushPin, contentDescription = null) },
                onClick = { onTogglePinned(); showMenu = false },
            )
            DropdownMenuItem(
                text = { Text(if (tab.locked) "Unlock" else "Lock") },
                leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                onClick = { onToggleLocked(); showMenu = false },
            )
            DropdownMenuItem(
                text = { Text("Add to group…") },
                onClick = { addToGroupSubmenu = true },
            )
            if (tab.groupId != null) {
                DropdownMenuItem(
                    text = { Text("Remove from group") },
                    onClick = { onAssignToGroup(null); showMenu = false },
                )
            }
            DropdownMenuItem(
                text = { Text("Select") },
                onClick = { onStartSelection(); showMenu = false },
            )
        }
        DropdownMenu(
            expanded = showMenu && addToGroupSubmenu,
            onDismissRequest = { showMenu = false; addToGroupSubmenu = false },
        ) {
            groups.forEach { g ->
                DropdownMenuItem(
                    text = { Text(g.name) },
                    onClick = { onAssignToGroup(g.id); showMenu = false; addToGroupSubmenu = false },
                )
            }
            DropdownMenuItem(
                text = { Text("New group…") },
                onClick = { onRequestNewGroup(); showMenu = false; addToGroupSubmenu = false },
            )
        }
    }
}
