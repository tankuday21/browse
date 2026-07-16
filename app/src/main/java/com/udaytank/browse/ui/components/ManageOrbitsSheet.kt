package com.udaytank.browse.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.udaytank.browse.data.OrbitEntity
import com.udaytank.browse.ui.theme.OrbitRadii
import com.udaytank.browse.ui.theme.OrbitSpacing
import com.udaytank.browse.ui.theme.orbit
import com.udaytank.browse.ui.theme.orbitBody
import com.udaytank.browse.ui.theme.orbitTitle

/**
 * A curated palette of ~8 accent-family colors offered when creating a new Orbit — deliberately
 * distinct from one another (and from the default brand accent) so Orbits stay visually
 * distinguishable in the quick-switch sheet and tab strip. Plain ARGB `Int`s (as stored on
 * [OrbitEntity.colorArgb]) rather than Compose [Color] so they can be persisted and compared
 * directly against DB rows without a conversion at every call site.
 */
val OrbitColors: List<Int> = listOf(
    0xFF4A7BFF.toInt(), // blue
    0xFF8B5CF6.toInt(), // violet
    0xFF22B8CF.toInt(), // cyan
    0xFF34C759.toInt(), // green
    0xFFF5A623.toInt(), // amber
    0xFFF43F7E.toInt(), // rose
    0xFF14B8A6.toInt(), // teal
    0xFF64748B.toInt(), // slate
)

/**
 * Orbit management sheet (Task 8): rename or delete any existing Orbit, or create a new one with
 * a name and a color from [OrbitColors]. Reached from the quick-switch sheet's "Manage Orbits"
 * row (Task 7). Mirrors [OrbitQuickSwitchSheet] / [BrowserMenuSheet]'s tonal [ModalBottomSheet],
 * grouped-row styling, and Orbit tokens throughout.
 *
 * Deleting an Orbit wipes its cookies/tabs, so every delete goes through a confirm dialog, and the
 * action is disabled outright once only one Orbit remains — the app always needs at least one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageOrbitsSheet(
    orbits: List<OrbitEntity>,
    onCreate: (name: String, colorArgb: Int, iconKey: String) -> Unit,
    onRename: (id: Long, name: String) -> Unit,
    onSetIcon: (id: Long, iconKey: String) -> Unit,
    onSetColor: (id: Long, colorArgb: Int) -> Unit,
    onDelete: (id: Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val scheme = orbit()
    var editingId by remember { mutableStateOf<Long?>(null) }
    var editingName by remember { mutableStateOf("") }
    var editingColor by remember { mutableStateOf(OrbitColors.first()) }
    var editingIcon by remember { mutableStateOf(OrbitIcons.first().key) }
    var deleteTarget by remember { mutableStateOf<OrbitEntity?>(null) }
    var newName by remember { mutableStateOf("") }
    var newColor by remember { mutableStateOf<Int?>(null) }
    var newIcon by remember { mutableStateOf(OrbitIcons.first().key) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = scheme.surfaces.elevated,
        shape = RoundedCornerShape(topStart = OrbitRadii.bar, topEnd = OrbitRadii.bar),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding(),
        ) {
            Text(
                "Manage Orbits",
                style = orbitTitle,
                color = scheme.text.primary,
                modifier = Modifier.padding(horizontal = OrbitSpacing.lg, vertical = OrbitSpacing.md),
            )

            orbits.forEach { entry ->
                if (editingId == entry.id) {
                    EditingOrbitRow(
                        name = editingName,
                        onNameChange = { editingName = it },
                        color = editingColor,
                        onColorChange = { editingColor = it },
                        iconKey = editingIcon,
                        onIconChange = { editingIcon = it },
                        onConfirm = {
                            if (editingName.isNotBlank()) onRename(entry.id, editingName.trim())
                            onSetColor(entry.id, editingColor)
                            onSetIcon(entry.id, editingIcon)
                            editingId = null
                        },
                        onCancel = { editingId = null },
                    )
                } else {
                    OrbitManageRow(
                        entry = entry,
                        deleteEnabled = orbits.size > 1,
                        onEdit = {
                            editingId = entry.id
                            editingName = entry.name
                            editingColor = entry.colorArgb
                            editingIcon = entry.iconKey
                        },
                        onDelete = { deleteTarget = entry },
                    )
                }
            }

            HorizontalDivider(color = scheme.text.muted.copy(alpha = 0.15f))

            // ── Add Orbit ────────────────────────────────
            Column(modifier = Modifier.padding(OrbitSpacing.lg)) {
                Text(
                    "Add Orbit",
                    style = orbitTitle,
                    color = scheme.text.primary,
                    modifier = Modifier.padding(bottom = OrbitSpacing.sm),
                )
                OrbitTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = "Name",
                    imeAction = ImeAction.Done,
                    onImeAction = {
                        if (newName.isNotBlank()) {
                            onCreate(newName.trim(), newColor ?: OrbitColors.first(), newIcon)
                            newName = ""
                            newColor = null
                            newIcon = OrbitIcons.first().key
                        }
                    },
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = OrbitSpacing.md),
                    horizontalArrangement = Arrangement.spacedBy(OrbitSpacing.sm),
                ) {
                    OrbitColors.forEach { colorArgb ->
                        ColorSwatch(
                            colorArgb = colorArgb,
                            selected = (newColor ?: OrbitColors.first()) == colorArgb,
                            onClick = { newColor = colorArgb },
                        )
                    }
                }
                // Icon picker (v4.2): pick which glyph rides on top of the color above.
                OrbitIconPickerRow(
                    selectedKey = newIcon,
                    onSelect = { newIcon = it },
                    modifier = Modifier.padding(top = OrbitSpacing.sm),
                )
                Button(
                    onClick = {
                        if (newName.isNotBlank()) {
                            onCreate(newName.trim(), newColor ?: OrbitColors.first(), newIcon)
                            newName = ""
                            newColor = null
                            newIcon = OrbitIcons.first().key
                        }
                    },
                    enabled = newName.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = scheme.accent.solid),
                    shape = RoundedCornerShape(OrbitRadii.chip),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = OrbitSpacing.lg),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(
                        "Add",
                        style = orbitBody,
                        modifier = Modifier.padding(start = OrbitSpacing.xs),
                    )
                }
            }
        }
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete \"${target.name}\"?") },
            text = { Text("This permanently deletes this Orbit's tabs and cookies. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(target.id)
                    deleteTarget = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            },
        )
    }
}

/**
 * One existing-Orbit row: an [OrbitAvatar] (tapping it opens the same edit mode as the pencil —
 * it's the fastest way in to change the icon/color), name, an edit (pencil) affordance, and a
 * delete (trash) action.
 */
@Composable
private fun OrbitManageRow(
    entry: OrbitEntity,
    deleteEnabled: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val scheme = orbit()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = OrbitSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OrbitSpacing.lg),
    ) {
        OrbitAvatar(
            colorArgb = entry.colorArgb,
            iconKey = entry.iconKey,
            size = 32.dp,
            modifier = Modifier.clickable(onClick = onEdit),
        )
        Text(
            entry.name,
            style = orbitBody,
            color = scheme.text.primary,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onEdit) {
            Icon(Icons.Filled.Edit, contentDescription = "Edit ${entry.name}", tint = scheme.text.secondary)
        }
        IconButton(onClick = onDelete, enabled = deleteEnabled) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = if (deleteEnabled) "Delete ${entry.name}" else "At least one Orbit must remain",
                tint = if (deleteEnabled) scheme.text.secondary else scheme.text.muted.copy(alpha = 0.4f),
            )
        }
    }
}

/**
 * The same row, swapped into inline-edit mode: an [OrbitTextField] for the name plus confirm/
 * cancel icons, then a color row and an icon row (v4.2) so a single "Edit" affordance covers
 * renaming AND restyling an existing Orbit's identity.
 */
@Composable
private fun EditingOrbitRow(
    name: String,
    onNameChange: (String) -> Unit,
    color: Int,
    onColorChange: (Int) -> Unit,
    iconKey: String,
    onIconChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val scheme = orbit()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = OrbitSpacing.lg, vertical = OrbitSpacing.xs),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(OrbitSpacing.sm),
        ) {
            OrbitTextField(
                value = name,
                onValueChange = onNameChange,
                label = "Name",
                imeAction = ImeAction.Done,
                onImeAction = onConfirm,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onConfirm) {
                Icon(Icons.Filled.Check, contentDescription = "Save", tint = scheme.accent.solid)
            }
            IconButton(onClick = onCancel) {
                Icon(Icons.Filled.Close, contentDescription = "Cancel edit", tint = scheme.text.muted)
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = OrbitSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(OrbitSpacing.sm),
        ) {
            OrbitColors.forEach { colorArgb ->
                ColorSwatch(
                    colorArgb = colorArgb,
                    selected = colorArgb == color,
                    onClick = { onColorChange(colorArgb) },
                )
            }
        }
        OrbitIconPickerRow(
            selectedKey = iconKey,
            onSelect = onIconChange,
            modifier = Modifier.padding(top = OrbitSpacing.xs),
        )
    }
}

/** A horizontally-scrolling row of every [OrbitIcons] option, for picking an Orbit's glyph. */
@Composable
private fun OrbitIconPickerRow(
    selectedKey: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(OrbitSpacing.xs),
    ) {
        OrbitIcons.forEach { option ->
            IconSwatch(
                option = option,
                selected = option.key == selectedKey,
                onClick = { onSelect(option.key) },
            )
        }
    }
}

/** One tappable icon option from [OrbitIcons]; a ring shows the current selection. */
@Composable
private fun IconSwatch(
    option: OrbitIconOption,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val scheme = orbit()
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(44.dp)
            .clickable(onClick = onClick),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(36.dp)
                .background(scheme.surfaces.elevated, CircleShape)
                .then(
                    if (selected) {
                        Modifier.border(2.dp, scheme.accent.solid, CircleShape)
                    } else {
                        Modifier
                    },
                ),
        ) {
            Icon(
                option.icon,
                contentDescription = option.label,
                tint = if (selected) scheme.accent.solid else scheme.text.secondary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** One tappable color swatch from [OrbitColors]; a ring + check mark shows the current selection. */
@Composable
private fun ColorSwatch(
    colorArgb: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val scheme = orbit()
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(44.dp)
            .clickable(onClick = onClick),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(36.dp)
                .background(Color(colorArgb), CircleShape)
                .then(
                    if (selected) {
                        Modifier.border(2.dp, scheme.text.primary, CircleShape)
                    } else {
                        Modifier
                    },
                ),
        ) {
            if (selected) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = "Selected",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
