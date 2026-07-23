package com.udaytank.browse.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/**
 * The one "Enter the Black Hole?" confirmation, shared by Settings → Danger Zone and the v6.2
 * shake gesture. Centralized so the destructive-action copy can never drift between entry points,
 * and so BOTH paths are gated by the same explicit confirm tap.
 */
@Composable
fun BlackHoleConfirmDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(Icons.Filled.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error)
        },
        title = { Text("Enter the Black Hole?") },
        text = {
            Text(
                "This permanently erases ALL of your data in Andromeda — every Orbit, tab, " +
                    "cookie, login, bookmark, home shortcut, history entry, download, and saved " +
                    "page. The app restarts to a clean slate. This cannot be undone.",
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Erase everything", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
