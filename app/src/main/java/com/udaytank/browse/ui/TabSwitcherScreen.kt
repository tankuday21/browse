package com.udaytank.browse.ui

import android.content.Intent
import android.widget.Toast
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VisibilityOff
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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.udaytank.browse.R
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.udaytank.browse.BrowserViewModel
import com.udaytank.browse.browser.TabOrderPolicy
import com.udaytank.browse.browser.TabSearchFilter
import com.udaytank.browse.browser.UrlHosts
import com.udaytank.browse.data.ClosedTabEntity
import com.udaytank.browse.data.TabEntity
import com.udaytank.browse.data.TabGroupEntity
import com.udaytank.browse.ui.components.OrbitListRow
import com.udaytank.browse.ui.components.OrbitTopBar
import com.udaytank.browse.ui.theme.LocalOrbit
import com.udaytank.browse.ui.theme.OrbitRadii
import com.udaytank.browse.ui.theme.OrbitSpacing
import com.udaytank.browse.ui.theme.darkOrbit
import com.udaytank.browse.ui.theme.orbit
import com.udaytank.browse.ui.theme.orbitBody
import com.udaytank.browse.ui.theme.orbitCaption

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

    // Chrome-style incognito separation: normal and incognito tabs live on their own screens,
    // switched by a toggle up top. Open onto whichever side the active tab belongs to.
    val normalTabs = tabs.filter { !it.isIncognito }
    val incognitoTabs = tabs.filter { it.isIncognito }
    var incognitoMode by rememberSaveable {
        mutableStateOf(tabs.find { it.id == activeTabId }?.isIncognito == true)
    }

    val sourceTabs = if (incognitoMode) incognitoTabs else normalTabs
    val filteredTabs = TabSearchFilter.filter(sourceTabs, searchQuery.orEmpty())
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

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    // Close a tab with an Undo affordance (reopens the just-closed tab from recently-closed).
    val closeWithUndo: (Long) -> Unit = { id ->
        onCloseTabView(id)
        viewModel.onCloseTab(id)
        scope.launch {
            val result = snackbarHostState.showSnackbar(message = "Tab closed", actionLabel = "Undo")
            if (result == SnackbarResult.ActionPerformed) {
                recentlyClosed.maxByOrNull { it.closedAt }?.let { viewModel.onReopenClosed(it) }
            }
        }
    }

    Scaffold(
        topBar = {
            val scheme = orbit()
            Column {
                OrbitTopBar(
                    title = "Tabs",
                    onBack = onBack,
                    actions = {
                        IconButton(onClick = { searchQuery = if (searchQuery == null) "" else null }) {
                            Icon(
                                Icons.Filled.Search,
                                contentDescription = "Search tabs",
                                tint = scheme.text.primary,
                            )
                        }
                        IconButton(onClick = { viewModel.onSwitcherLayoutToggled() }) {
                            Icon(
                                if (listLayout) Icons.Filled.GridView else Icons.AutoMirrored.Filled.List,
                                contentDescription = "Toggle layout",
                                tint = scheme.text.primary,
                            )
                        }
                        IconButton(onClick = { showRecentlyClosed = true }) {
                            Icon(
                                Icons.Filled.History,
                                contentDescription = "Recently closed",
                                tint = scheme.text.primary,
                            )
                        }
                    },
                )
                // Normal | Incognito toggle — each side is its own screen (Chrome-style).
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = OrbitSpacing.md, vertical = OrbitSpacing.sm),
                ) {
                    SegmentedButton(
                        selected = !incognitoMode,
                        onClick = { incognitoMode = false; selection = emptySet() },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    ) { Text("Tabs (${normalTabs.size})") }
                    SegmentedButton(
                        selected = incognitoMode,
                        onClick = { incognitoMode = true; selection = emptySet() },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        icon = {
                            Icon(
                                Icons.Filled.VisibilityOff,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        },
                    ) { Text("Incognito (${incognitoTabs.size})") }
                }
                if (searchQuery != null) {
                    OutlinedTextField(
                        value = searchQuery.orEmpty(),
                        onValueChange = { searchQuery = it },
                        singleLine = true,
                        placeholder = { Text("Search tabs") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = OrbitSpacing.md, vertical = OrbitSpacing.sm),
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (selection.isEmpty()) {
                val scheme = orbit()
                // Custom circular button rather than M3 FloatingActionButton: a transparent-
                // container FAB still drew its own rounded shape/shadow behind the gradient
                // circle (the "hexagon"). A single clipped Box is unambiguously one circle.
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .shadow(6.dp, CircleShape)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(scheme.accent.gradient))
                        .clickable {
                            if (incognitoMode) viewModel.onNewIncognitoTab() else viewModel.onNewTab()
                            onTabChosen()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "New tab", tint = Color.White)
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
                    // Privacy: incognito tabs never leave the app via bulk share — their urls
                    // are excluded, and an all-incognito selection has nothing to share at all.
                    val shareableTabs = tabs.filter { it.id in selection && !it.isIncognito }
                    TextButton(
                        enabled = shareableTabs.isNotEmpty(),
                        onClick = {
                            val urls = shareableTabs.joinToString("\n") { it.url }
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, urls)
                            }
                            context.startActivity(Intent.createChooser(intent, "Share tabs"))
                        },
                    ) { Text("Share") }
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
        // On the incognito side the whole canvas goes dark regardless of the phone theme
        // (that's the signature "you're private now" cue), by swapping the Orbit scheme the
        // cards/headers below read from. The top bar + toggle stay outside this, normal-themed.
        val contentScheme = if (incognitoMode) darkOrbit else orbit()
        CompositionLocalProvider(LocalOrbit provides contentScheme) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(if (incognitoMode) darkOrbit.surfaces.base else Color.Transparent),
            ) {
                if (renderItems.isEmpty()) {
                    TabsEmptyState(
                        incognito = incognitoMode,
                        searching = !searchQuery.isNullOrBlank(),
                        query = searchQuery.orEmpty(),
                    )
                } else {
                    LazyVerticalGrid(
                        columns = if (listLayout) GridCells.Fixed(1) else GridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize().padding(OrbitSpacing.md),
                        // List rows are compact (P6 improve pass) — tighter spacing so ~8+ fit a screen.
                        verticalArrangement = Arrangement.spacedBy(if (listLayout) OrbitSpacing.sm else OrbitSpacing.md),
                        horizontalArrangement = Arrangement.spacedBy(OrbitSpacing.md),
                    ) {
                        if (incognitoMode) {
                            item(key = "incognito-banner", span = { GridItemSpan(maxLineSpan) }) {
                                IncognitoBanner()
                            }
                        }
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
                            modifier = Modifier.animateItem(),
                        )
                    }

                    is SwitcherItem.Tab -> {
                        val tab = entry.tab
                        val hiddenByCollapse = !tab.pinned && tab.groupId != null && tab.groupId in collapsedGroups
                        if (!hiddenByCollapse) {
                            item(key = tab.id) {
                                val onClick = {
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
                                }
                                val onClose = {
                                    // Locked tabs go through the confirm dialog; everything else
                                    // closes immediately with an Undo snackbar.
                                    if (tab.locked) viewModel.onCloseTab(tab.id) else closeWithUndo(tab.id)
                                }
                                val onStartSelection = { selection = setOf(tab.id) }
                                val onTogglePinned = { viewModel.onTogglePinned(tab.id) }
                                val onToggleLocked = { viewModel.onToggleLocked(tab.id) }
                                val onAssignToGroup =
                                    { groupId: Long? -> viewModel.onAssignTabToGroup(tab.id, groupId) }
                                val onRequestNewGroup = {
                                    groupPromptTabIds = listOf(tab.id)
                                    groupNameInput = ""
                                }
                                if (listLayout) {
                                    TabListRow(
                                        tab = tab,
                                        isActive = tab.id == activeTabId,
                                        isSelected = tab.id in selection,
                                        selectionMode = selection.isNotEmpty(),
                                        groups = groups,
                                        holder = holder,
                                        onClick = onClick,
                                        onClose = onClose,
                                        onStartSelection = onStartSelection,
                                        onTogglePinned = onTogglePinned,
                                        onToggleLocked = onToggleLocked,
                                        onAssignToGroup = onAssignToGroup,
                                        onRequestNewGroup = onRequestNewGroup,
                                        modifier = Modifier.animateItem(),
                                    )
                                } else {
                                    TabCard(
                                        tab = tab,
                                        isActive = tab.id == activeTabId,
                                        isSelected = tab.id in selection,
                                        selectionMode = selection.isNotEmpty(),
                                        groups = groups,
                                        holder = holder,
                                        onClick = onClick,
                                        onClose = onClose,
                                        onStartSelection = onStartSelection,
                                        onTogglePinned = onTogglePinned,
                                        onToggleLocked = onToggleLocked,
                                        onAssignToGroup = onAssignToGroup,
                                        onRequestNewGroup = onRequestNewGroup,
                                        modifier = Modifier.animateItem(),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
                    } // else (has tabs)
                } // Box
            } // CompositionLocalProvider
    }

    if (showRecentlyClosed) {
        val scheme = orbit()
        ModalBottomSheet(
            onDismissRequest = { showRecentlyClosed = false },
            containerColor = scheme.surfaces.elevated,
            shape = RoundedCornerShape(topStart = OrbitRadii.bar, topEnd = OrbitRadii.bar),
        ) {
            if (recentlyClosed.isEmpty()) {
                Text(
                    "No recently closed tabs",
                    style = orbitBody,
                    color = scheme.text.muted,
                    modifier = Modifier.padding(OrbitSpacing.lg),
                )
            } else {
                Column(modifier = Modifier.padding(bottom = OrbitSpacing.lg)) {
                    recentlyClosed.sortedByDescending { it.closedAt }.forEach { entry: ClosedTabEntity ->
                        OrbitListRow(
                            leadingIcon = null,
                            title = entry.title.ifBlank { entry.url },
                            subtitle = entry.url,
                            onClick = {
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
                        viewModel.onCreateGroupWithTabs(groupNameInput.trim(), ids) { message ->
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        }
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
    modifier: Modifier = Modifier,
) {
    val scheme = orbit()
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = OrbitSpacing.xs)
            .clickable { onToggleCollapse() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OrbitSpacing.sm),
    ) {
        val groupColor = GroupColors[group.color % GroupColors.size]
        // Group name as a chip tinted with the group color — clearer than a bare dot + label.
        Surface(
            shape = RoundedCornerShape(percent = OrbitRadii.pill),
            color = groupColor.copy(alpha = 0.16f),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(OrbitSpacing.xs),
                modifier = Modifier.padding(horizontal = OrbitSpacing.md, vertical = 6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(groupColor),
                )
                Text(
                    "${group.name} ($count)",
                    style = orbitCaption,
                    color = scheme.text.primary,
                    maxLines = 1,
                )
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        Icon(
            if (collapsed) Icons.Filled.ExpandMore else Icons.Filled.ExpandLess,
            contentDescription = if (collapsed) "Expand group" else "Collapse group",
            tint = scheme.text.secondary,
        )
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(
                    Icons.Filled.MoreVert,
                    contentDescription = "Group options",
                    tint = scheme.text.secondary,
                )
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
    modifier: Modifier = Modifier,
) {
    val scheme = orbit()
    val cardShape = RoundedCornerShape(OrbitRadii.card)
    val thumbnail = remember(tab.id, tab.url) {
        holder.thumbnails.load(tab.id)?.asImageBitmap()
    }
    var showMenu by remember { mutableStateOf(false) }

    // Swipe a card away to close it (locked tabs excluded — they use the X / confirm dialog).
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value != SwipeToDismissBoxValue.Settled && !tab.locked) { onClose(); true } else false
        },
    )

    Box(modifier = modifier) {
        SwipeToDismissBox(
            state = dismissState,
            enableDismissFromStartToEnd = !tab.locked,
            enableDismissFromEndToStart = !tab.locked,
            backgroundContent = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(cardShape)
                        .background(scheme.accent.solid.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = null,
                        tint = scheme.accent.solid,
                        modifier = Modifier.size(20.dp),
                    )
                }
            },
        ) {
        Surface(
            shape = cardShape,
            color = scheme.surfaces.surface,
            // Active tab reads as lifted (elevation) + accent-ringed, not just outlined.
            tonalElevation = if (isActive) 6.dp else 2.dp,
            shadowElevation = if (isActive) 6.dp else 0.dp,
            modifier = Modifier
                .clip(cardShape)
                .then(
                    if (isActive) {
                        Modifier.border(2.dp, scheme.accent.solid, cardShape)
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
                        tab.isIncognito -> Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(scheme.surfaces.elevated),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                Icons.Filled.VisibilityOff,
                                contentDescription = null,
                                tint = scheme.text.secondary,
                                modifier = Modifier.size(28.dp),
                            )
                            Text(
                                "Incognito",
                                style = orbitCaption,
                                color = scheme.text.secondary,
                                modifier = Modifier.padding(top = OrbitSpacing.xs),
                            )
                        }

                        isHomeUrl(tab.url) -> HomeTabPreview()

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
                                .background(Brush.horizontalGradient(scheme.accent.gradient)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                placeholderLetter(tab),
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
                                    color = scheme.surfaces.surface.copy(alpha = 0.85f),
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
                                    color = scheme.surfaces.surface.copy(alpha = 0.85f),
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
                        color = scheme.surfaces.surface.copy(alpha = 0.85f),
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
                                        tint = scheme.accent.solid,
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
                    text = when {
                        tab.isIncognito -> "Incognito"
                        isHomeUrl(tab.url) -> "Home"
                        else -> tab.title
                    },
                    style = orbitBody,
                    color = scheme.text.primary,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = OrbitSpacing.md, vertical = OrbitSpacing.sm),
                )
            }
        }
        } // SwipeToDismissBox

        TabContextMenu(
            tab = tab,
            groups = groups,
            expanded = showMenu,
            onDismiss = { showMenu = false },
            onTogglePinned = onTogglePinned,
            onToggleLocked = onToggleLocked,
            onAssignToGroup = onAssignToGroup,
            onRequestNewGroup = onRequestNewGroup,
            onStartSelection = onStartSelection,
        )
    }
}

/**
 * Compact single-column row for the switcher's list layout (P6 improve pass): small thumbnail,
 * title + host, pin/lock markers, close/selection affordance — ~8+ rows fit a phone screen
 * where the grid card previously fit ~3. Same gestures and context menu as [TabCard].
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TabListRow(
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
    modifier: Modifier = Modifier,
) {
    val scheme = orbit()
    val cardShape = RoundedCornerShape(OrbitRadii.card)
    val thumbnail = remember(tab.id, tab.url) {
        holder.thumbnails.load(tab.id)?.asImageBitmap()
    }
    var showMenu by remember { mutableStateOf(false) }

    // Swipe a row away to close it. Locked tabs don't swipe (closing them needs the confirm
    // dialog) — the X button / long-press menu still handle those.
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value != SwipeToDismissBoxValue.Settled && !tab.locked) {
                onClose(); true
            } else {
                false
            }
        },
    )

    Box(modifier = modifier) {
        SwipeToDismissBox(
            state = dismissState,
            enableDismissFromStartToEnd = !tab.locked,
            enableDismissFromEndToStart = !tab.locked,
            backgroundContent = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(cardShape)
                        .background(scheme.accent.solid.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = null,
                        tint = scheme.accent.solid,
                        modifier = Modifier.size(20.dp),
                    )
                }
            },
        ) {
        Surface(
            shape = cardShape,
            color = scheme.surfaces.surface,
            tonalElevation = if (isActive) 6.dp else 2.dp,
            modifier = Modifier
                .clip(cardShape)
                .then(
                    if (isActive) {
                        Modifier.border(2.dp, scheme.accent.solid, cardShape)
                    } else {
                        Modifier
                    }
                )
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { if (!selectionMode) showMenu = true },
                ),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .padding(horizontal = OrbitSpacing.sm),
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(cardShape),
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        tab.isIncognito -> Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(scheme.surfaces.elevated),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Filled.VisibilityOff,
                                contentDescription = null,
                                tint = scheme.text.secondary,
                                modifier = Modifier.size(20.dp),
                            )
                        }

                        isHomeUrl(tab.url) -> Image(
                            painter = painterResource(R.drawable.home_backdrop),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )

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
                                .background(Brush.horizontalGradient(scheme.accent.gradient)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                placeholderLetter(tab),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                    }
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = OrbitSpacing.md),
                ) {
                    Text(
                        when {
                            tab.isIncognito -> "Incognito"
                            isHomeUrl(tab.url) -> "Home"
                            else -> tab.title.ifBlank { tab.url }
                        },
                        style = orbitBody,
                        color = scheme.text.primary,
                        maxLines = 1,
                    )
                    Text(
                        when {
                            tab.isIncognito -> "Private tab"
                            isHomeUrl(tab.url) -> "New tab"
                            else -> UrlHosts.of(tab.url) ?: tab.url
                        },
                        style = orbitCaption,
                        color = scheme.text.muted,
                        maxLines = 1,
                    )
                }
                if (tab.pinned) {
                    Icon(
                        Icons.Filled.PushPin,
                        contentDescription = "Pinned",
                        modifier = Modifier.size(16.dp).padding(end = 2.dp),
                        tint = scheme.text.secondary,
                    )
                }
                if (tab.locked) {
                    Icon(
                        Icons.Filled.Lock,
                        contentDescription = "Locked",
                        modifier = Modifier.size(16.dp).padding(end = 2.dp),
                        tint = scheme.text.secondary,
                    )
                }
                if (selectionMode) {
                    if (isSelected) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = "Selected",
                            tint = scheme.accent.solid,
                            modifier = Modifier.padding(horizontal = 12.dp).size(20.dp),
                        )
                    }
                } else {
                    IconButton(onClick = onClose) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Close tab",
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
        } // SwipeToDismissBox

        TabContextMenu(
            tab = tab,
            groups = groups,
            expanded = showMenu,
            onDismiss = { showMenu = false },
            onTogglePinned = onTogglePinned,
            onToggleLocked = onToggleLocked,
            onAssignToGroup = onAssignToGroup,
            onRequestNewGroup = onRequestNewGroup,
            onStartSelection = onStartSelection,
        )
    }
}

/**
 * The long-press context menu shared by [TabCard] and [TabListRow], including the add-to-group
 * submenu (which checkmarks the tab's CURRENT group — P6 improve pass).
 */
@Composable
private fun TabContextMenu(
    tab: TabEntity,
    groups: List<TabGroupEntity>,
    expanded: Boolean,
    onDismiss: () -> Unit,
    onTogglePinned: () -> Unit,
    onToggleLocked: () -> Unit,
    onAssignToGroup: (Long?) -> Unit,
    onRequestNewGroup: () -> Unit,
    onStartSelection: () -> Unit,
) {
    var addToGroupSubmenu by remember { mutableStateOf(false) }
    LaunchedEffect(expanded) {
        if (!expanded) addToGroupSubmenu = false
    }
    DropdownMenu(
        expanded = expanded && !addToGroupSubmenu,
        onDismissRequest = onDismiss,
    ) {
        DropdownMenuItem(
            text = { Text(if (tab.pinned) "Unpin" else "Pin") },
            leadingIcon = { Icon(Icons.Filled.PushPin, contentDescription = null) },
            onClick = { onTogglePinned(); onDismiss() },
        )
        DropdownMenuItem(
            text = { Text(if (tab.locked) "Unlock" else "Lock") },
            leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
            onClick = { onToggleLocked(); onDismiss() },
        )
        DropdownMenuItem(
            text = { Text("Add to group…") },
            leadingIcon = { Icon(Icons.Filled.Folder, contentDescription = null) },
            onClick = { addToGroupSubmenu = true },
        )
        if (tab.groupId != null) {
            DropdownMenuItem(
                text = { Text("Remove from group") },
                leadingIcon = { Icon(Icons.Filled.FolderOff, contentDescription = null) },
                onClick = { onAssignToGroup(null); onDismiss() },
            )
        }
        DropdownMenuItem(
            text = { Text("Select") },
            leadingIcon = { Icon(Icons.Filled.CheckCircle, contentDescription = null) },
            onClick = { onStartSelection(); onDismiss() },
        )
    }
    DropdownMenu(
        expanded = expanded && addToGroupSubmenu,
        onDismissRequest = onDismiss,
    ) {
        groups.forEach { g ->
            DropdownMenuItem(
                text = { Text(g.name) },
                trailingIcon = {
                    if (tab.groupId == g.id) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = "Current group",
                            tint = orbit().accent.solid,
                        )
                    }
                },
                onClick = { onAssignToGroup(g.id); onDismiss() },
            )
        }
        DropdownMenuItem(
            text = { Text("New group…") },
            leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
            onClick = { onRequestNewGroup(); onDismiss() },
        )
    }
}

/**
 * Full-screen empty state: the Chrome-style incognito explainer, a "no results" message while
 * searching, or a plain "no tabs". Reads the ambient (possibly dark, on the incognito side)
 * Orbit scheme so it matches whichever screen it fills.
 */
@Composable
private fun TabsEmptyState(incognito: Boolean, searching: Boolean, query: String) {
    val scheme = orbit()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(OrbitSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            if (incognito) Icons.Filled.VisibilityOff else Icons.AutoMirrored.Filled.List,
            contentDescription = null,
            tint = scheme.text.secondary,
            modifier = Modifier.size(48.dp),
        )
        Spacer(modifier = Modifier.height(OrbitSpacing.md))
        Text(
            when {
                searching -> "No tabs match “$query”"
                incognito -> "You’ve gone Incognito"
                else -> "No open tabs"
            },
            style = orbitBody,
            color = scheme.text.primary,
        )
        if (incognito && !searching) {
            Spacer(modifier = Modifier.height(OrbitSpacing.sm))
            Text(
                "Pages you view here won’t stay in your history, and these tabs vanish when you " +
                    "close them. Tap + to open one.",
                style = orbitCaption,
                color = scheme.text.secondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Slim privacy reminder pinned above the incognito grid when private tabs are open. */
@Composable
private fun IncognitoBanner() {
    val scheme = orbit()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = OrbitSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OrbitSpacing.sm),
    ) {
        Icon(
            Icons.Filled.VisibilityOff,
            contentDescription = null,
            tint = scheme.text.secondary,
            modifier = Modifier.size(20.dp),
        )
        Text(
            "You’re browsing privately",
            style = orbitCaption,
            color = scheme.text.secondary,
        )
    }
}

/** True for the internal home tab (browse://home) — shown as "Home" with a cosmic preview. */
private fun isHomeUrl(url: String) = url == com.udaytank.browse.BrowserViewModel.HOME_URL

/** A glimpse of the home page for a home tab's thumbnail: the cosmic backdrop + wordmark. */
@Composable
private fun HomeTabPreview() {
    val scheme = orbit()
    Box(modifier = Modifier.fillMaxSize().background(scheme.surfaces.base)) {
        Image(
            painter = painterResource(R.drawable.home_backdrop),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = Alignment.TopCenter,
            modifier = Modifier.fillMaxSize(),
        )
        Text(
            "Andromeda",
            style = orbitBody,
            color = scheme.accent.solid,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

/**
 * Letter for the no-thumbnail placeholder tile. Fresh (e.g. just-reopened) tabs still carry
 * their url as the title, which would render as "H" for every http page — prefer the host's
 * first letter in that case so the placeholder says something about the page.
 */
private fun placeholderLetter(tab: TabEntity): String {
    val source = tab.title.takeIf { it.isNotBlank() && it != tab.url }
        ?: UrlHosts.of(tab.url)?.removePrefix("www.")
        ?: tab.url
    return source.trim().firstOrNull()?.uppercase() ?: "•"
}
