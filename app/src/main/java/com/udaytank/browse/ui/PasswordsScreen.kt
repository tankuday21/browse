package com.udaytank.browse.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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
import com.udaytank.browse.ui.theme.OrbitRadii
import com.udaytank.browse.ui.theme.OrbitSpacing
import com.udaytank.browse.ui.theme.orbit
import com.udaytank.browse.ui.theme.orbitBody
import com.udaytank.browse.ui.theme.orbitCaption
import com.udaytank.browse.ui.theme.orbitTitle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Saved logins for the active Orbit (v4.7). Each row shows the site + username with the password
 * masked behind a reveal toggle (decrypted on demand via the Keystore), plus edit + copy + delete.
 * v5.1 adds manual add/edit (the FAB + pencil) — and the whole screen sits behind the biometric
 * gate in MainActivity when "Require screen lock for passwords" is on.
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

    var editorOpen by remember { mutableStateOf(false) }
    var editorFor by remember { mutableStateOf<CredentialEntity?>(null) } // null = add

    Scaffold(
        topBar = {
            Column {
                OrbitTopBar(title = "Passwords", onBack = onBack)
                if (activeOrbit != null) OrbitScopeHeader(activeOrbit, scope = "passwords")
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editorFor = null; editorOpen = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Add login", style = orbitBody) },
                containerColor = scheme.accent.solid,
                contentColor = Color.White,
            )
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
                        onEdit = { editorFor = entry; editorOpen = true },
                        onDelete = { viewModel.onDeleteCredential(entry.id) },
                    )
                }
            }
        }
    }

    if (editorOpen) {
        CredentialEditorSheet(
            existing = editorFor,
            reveal = { viewModel.revealCredential(it) },
            onSave = { host, username, password ->
                val target = editorFor
                if (target == null) {
                    viewModel.onAddCredential(host, username, password)
                } else {
                    viewModel.onEditCredential(target.id, host, username, password)
                }
            },
            onDismiss = { editorOpen = false },
        )
    }
}

/**
 * Add/edit form (v5.1). Editing prefills site + username and decrypts the current password into
 * the field — acceptable because the screen itself is already behind the biometric gate.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CredentialEditorSheet(
    existing: CredentialEntity?,
    reveal: suspend (CredentialEntity) -> String?,
    onSave: (host: String, username: String, password: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val scheme = orbit()
    var host by remember { mutableStateOf(existing?.host ?: "") }
    var username by remember { mutableStateOf(existing?.username ?: "") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    LaunchedEffect(existing) {
        if (existing != null) password = reveal(existing) ?: ""
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = scheme.surfaces.elevated) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = OrbitSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(OrbitSpacing.md),
        ) {
            Text(
                if (existing == null) "Add login" else "Edit login",
                style = orbitTitle,
                color = scheme.text.primary,
            )
            OutlinedTextField(
                value = host,
                onValueChange = { host = it },
                label = { Text("Site") },
                placeholder = { Text("example.com") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = if (showPassword) "Hide password" else "Show password",
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { onSave(host, username, password); onDismiss() },
                enabled = host.isNotBlank() && password.isNotEmpty(),
                shape = RoundedCornerShape(OrbitRadii.pill),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (existing == null) "Save" else "Update", style = orbitBody) }
            Spacer(Modifier.height(OrbitSpacing.xl))
        }
    }
}

@Composable
private fun CredentialRow(
    entry: CredentialEntity,
    reveal: suspend () -> String?,
    onEdit: () -> Unit,
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
        IconButton(onClick = onEdit) {
            Icon(Icons.Filled.Edit, contentDescription = "Edit login", tint = scheme.text.secondary)
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
            if (activeOrbit != null) "No saved passwords in ${activeOrbit.name} yet — sign in on a site, or add one with the button below"
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
