package com.udaytank.browse.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.unit.dp
import com.udaytank.browse.browser.PrivacyStatsFormat
import com.udaytank.browse.data.HomeShortcutEntity
import java.time.LocalTime

private fun greeting(): String = when (LocalTime.now().hour) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    in 17..21 -> "Good evening"
    else -> "Up late, explorer?"
}

/** Does the clipboard text look like something we can navigate to? (Same cases as UrlInput 1+2.) */
private fun looksLikeUrl(text: String): Boolean {
    val t = text.trim()
    return t.isNotEmpty() && !t.contains(' ') && !t.contains('\n') &&
        (t.startsWith("http://") || t.startsWith("https://") || t.contains('.'))
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomePage(
    shortcuts: List<HomeShortcutEntity>,
    isIncognito: Boolean,
    onOpenUrl: (String) -> Unit,
    onAddShortcut: (url: String, title: String) -> Unit,
    onRemoveShortcut: (Long) -> Unit,
    onMoveShortcutToFront: (Long) -> Unit,
    modifier: Modifier = Modifier,
    lifetimeBlocked: Long = 0L,
    /**
     * Chrome-NTP centered search (v3-ux): the REAL CommandBar in home-pill display state,
     * rendered under the wordmark with the shortcut grid + stats below. Null while the bar
     * is editing (it then sits at the bottom above the keyboard, same as on web pages).
     */
    searchBar: (@Composable () -> Unit)? = null,
) {
    val clipboard = LocalClipboardManager.current
    var showAddDialog by remember { mutableStateOf(false) }
    var menuForId by remember { mutableStateOf<Long?>(null) }

    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            if (isIncognito) "Incognito" else "Andromeda",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 48.dp, bottom = 8.dp),
        )
        if (!isIncognito) {
            Text(
                greeting(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 32.dp),
            )
        }
        if (isIncognito) {
            Text(
                "Pages you view in this tab won't appear in your history,\nand the tab disappears when you close the app.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 24.dp),
            )
        }
        // ── Centered search pill (v3-ux) — logo above, shortcuts + stats below ──
        searchBar?.let { bar ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 36.dp),
            ) {
                bar()
            }
        }
        // ── Shortcut grid (C1) — user-curated, so shown in incognito too ──
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(shortcuts, key = { it.id }) { shortcut ->
                Box {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.combinedClickable(
                            onClick = { onOpenUrl(shortcut.url) },
                            onLongClick = { menuForId = shortcut.id },
                        ),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                shortcut.title.take(1).uppercase(),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                        Text(
                            shortcut.title,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    DropdownMenu(
                        expanded = menuForId == shortcut.id,
                        onDismissRequest = { menuForId = null },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Move to front") },
                            onClick = {
                                onMoveShortcutToFront(shortcut.id)
                                menuForId = null
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Remove") },
                            onClick = {
                                onRemoveShortcut(shortcut.id)
                                menuForId = null
                            },
                        )
                    }
                }
            }
            item(key = "add-tile") {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.combinedClickable(onClick = { showAddDialog = true }),
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = "Add shortcut",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        "Add",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
        // ── Privacy stats (C3) — under the shortcut grid, hidden until anything's blocked ──
        if (!isIncognito && lifetimeBlocked > 0) {
            val (blockedLine, savedLine) = PrivacyStatsFormat.format(lifetimeBlocked)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(top = 40.dp),
            ) {
                Icon(
                    Icons.Filled.Shield,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                )
                Column(modifier = Modifier.padding(start = 12.dp)) {
                    Text(
                        blockedLine,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        savedLine,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        // Clipboard is read once, at the moment the dialog opens - never passively.
        val clipboardUrl = remember {
            clipboard.getText()?.text?.trim().orEmpty().takeIf(::looksLikeUrl).orEmpty()
        }
        var url by remember { mutableStateOf(clipboardUrl) }
        var title by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add to home") },
            text = {
                Column {
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text("Link") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = url.isNotBlank(),
                    onClick = {
                        onAddShortcut(url, title)
                        showAddDialog = false
                    },
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("Cancel") }
            },
        )
    }
}
