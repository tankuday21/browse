package com.udaytank.browse.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.udaytank.browse.BrowserViewModel
import com.udaytank.browse.data.CredentialEntity
import com.udaytank.browse.data.OrbitEntity
import com.udaytank.browse.ui.components.FaviconOrLetter
import com.udaytank.browse.ui.components.OrbitAvatar
import com.udaytank.browse.ui.components.OrbitScopeHeader
import com.udaytank.browse.ui.components.OrbitTopBar
import com.udaytank.browse.ui.theme.OrbitSpacing
import com.udaytank.browse.ui.theme.orbit
import com.udaytank.browse.ui.theme.orbitBody
import com.udaytank.browse.ui.theme.orbitCaption
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Saved logins for the active Orbit (v4.7). Each row shows the site + username with the password
 * masked behind a reveal toggle (decrypted on demand via the Keystore), plus copy + delete. Add is
 * capture-only in Phase 1 — logins arrive from the save prompt when you sign in on a page.
 */
@Composable
fun PasswordsScreen(
    viewModel: BrowserViewModel,
    onBack: () -> Unit,
) {
    val credentials by viewModel.credentials.collectAsStateWithLifecycle()
    val orbits by viewModel.orbits.collectAsStateWithLifecycle()
    val activeOrbitId by viewModel.activeOrbitId.collectAsStateWithLifecycle()
    val activeOrbit = remember(orbits, activeOrbitId) { orbits.firstOrNull { it.id == activeOrbitId } }
    val scheme = orbit()

    Scaffold(
        topBar = {
            Column {
                OrbitTopBar(title = "Passwords", onBack = onBack)
                if (activeOrbit != null) OrbitScopeHeader(activeOrbit, scope = "passwords")
            }
        },
        containerColor = scheme.surfaces.base,
    ) { innerPadding ->
        if (credentials.isEmpty()) {
            PasswordsEmptyState(activeOrbit, Modifier.fillMaxSize().padding(innerPadding))
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                items(credentials, key = { it.id }) { entry ->
                    CredentialRow(
                        entry = entry,
                        reveal = { viewModel.revealCredential(entry) },
                        onDelete = { viewModel.onDeleteCredential(entry.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CredentialRow(
    entry: CredentialEntity,
    reveal: suspend () -> String?,
    onDelete: () -> Unit,
) {
    val scheme = orbit()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var revealed by remember { mutableStateOf<String?>(null) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(horizontal = OrbitSpacing.lg, vertical = OrbitSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OrbitSpacing.md),
    ) {
        FaviconOrLetter(url = "https://${entry.host}", label = entry.host, size = 36.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(entry.host, style = orbitBody, color = scheme.text.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = entry.username.ifBlank { "(no username)" },
                style = orbitCaption,
                color = scheme.text.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            revealed?.let {
                Text(it, style = orbitCaption, color = scheme.text.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        IconButton(onClick = {
            if (revealed != null) {
                revealed = null
            } else {
                scope.launch { revealed = reveal() ?: "(can't decrypt)" }
            }
        }) {
            Icon(
                if (revealed != null) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                contentDescription = if (revealed != null) "Hide password" else "Reveal password",
                tint = scheme.text.secondary,
            )
        }
        IconButton(onClick = {
            scope.launch {
                val pw = reveal() ?: return@launch
                copyToClipboard(context, pw)
                Toast.makeText(context, "Password copied — clears in 60s", Toast.LENGTH_SHORT).show()
                // Auto-clear so a copied password doesn't linger on the clipboard indefinitely.
                delay(60_000)
                clearClipboardIfOurs(context)
            }
        }) {
            Icon(Icons.Filled.ContentCopy, contentDescription = "Copy password", tint = scheme.text.secondary)
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = "Delete login", tint = scheme.text.secondary)
        }
    }
}

@Composable
private fun PasswordsEmptyState(activeOrbit: OrbitEntity?, modifier: Modifier = Modifier) {
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
                    Icon(Icons.Filled.Key, contentDescription = null, tint = scheme.text.secondary, modifier = Modifier.size(36.dp))
                }
            }
        }
        Text(
            if (activeOrbit != null) "No saved passwords in ${activeOrbit.name} yet — sign in on a site and Andromeda will offer to save it"
            else "No saved passwords yet",
            style = orbitBody,
            color = scheme.text.muted,
            modifier = Modifier.padding(top = OrbitSpacing.md),
        )
    }
}

private const val CLIP_LABEL = "password"

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    // Mark the clip sensitive so it's kept off clipboard previews/history where supported.
    val clip = ClipData.newPlainText(CLIP_LABEL, text).apply {
        description.extras = android.os.PersistableBundle().apply {
            putBoolean("android.content.extra.IS_SENSITIVE", true)
        }
    }
    clipboard.setPrimaryClip(clip)
}

/** Clears the clipboard only if it still holds the password we put there (don't clobber other copies). */
private fun clearClipboardIfOurs(context: Context) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    if (clipboard.primaryClip?.description?.label == CLIP_LABEL) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            clipboard.clearPrimaryClip()
        } else {
            clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
        }
    }
}
