package com.udaytank.browse.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.udaytank.browse.ui.theme.OrbitSpacing
import com.udaytank.browse.ui.theme.orbit
import com.udaytank.browse.ui.theme.orbitBody

/**
 * A slim prompt above the address bar offering to save a just-submitted login (v4.7). No auto-save
 * — the user decides. "Not now" simply dismisses (a persistent "never for this site" list is a
 * later phase).
 */
@Composable
fun SavePasswordBar(host: String, onSave: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = orbit()
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = OrbitSpacing.md, vertical = OrbitSpacing.xs),
        color = scheme.surfaces.elevated,
        shape = RoundedCornerShape(OrbitSpacing.md),
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.padding(start = OrbitSpacing.md, end = OrbitSpacing.xs).padding(vertical = OrbitSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(OrbitSpacing.sm),
        ) {
            Icon(Icons.Filled.Key, contentDescription = null, tint = scheme.accent.solid)
            Text(
                "Save password for $host?",
                style = orbitBody,
                color = scheme.text.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onSave) { Text("Save") }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Close, contentDescription = "Not now", tint = scheme.text.muted)
            }
        }
    }
}

/**
 * A slim prompt offering to fill a saved login on the current page (v4.7). One button per saved
 * username; the password never appears here — filling is what injects it, on the user's tap.
 */
@Composable
fun FillPasswordBar(
    usernames: List<String>,
    onFill: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = orbit()
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = OrbitSpacing.md, vertical = OrbitSpacing.xs),
        color = scheme.surfaces.elevated,
        shape = RoundedCornerShape(OrbitSpacing.md),
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.padding(start = OrbitSpacing.md, end = OrbitSpacing.xs).padding(vertical = OrbitSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(OrbitSpacing.sm),
        ) {
            Icon(Icons.Filled.Key, contentDescription = null, tint = scheme.accent.solid)
            Text("Fill password", style = orbitBody, color = scheme.text.primary, maxLines = 1)
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(OrbitSpacing.xs),
            ) {
                usernames.take(3).forEach { user ->
                    TextButton(onClick = { onFill(user) }) {
                        Text(
                            user.ifBlank { "(login)" },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 120.dp),
                        )
                    }
                }
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Close, contentDescription = "Dismiss", tint = scheme.text.muted)
            }
        }
    }
}
