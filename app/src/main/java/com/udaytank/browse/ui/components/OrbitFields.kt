package com.udaytank.browse.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import com.udaytank.browse.ui.theme.OrbitRadii
import com.udaytank.browse.ui.theme.orbit
import com.udaytank.browse.ui.theme.orbitBody

/**
 * The one text field for the whole app (v4.1 "Chrome-smooth" pass). A filled, tonal, rounded
 * input with a border that animates to the brand accent on focus (Material handles the ~150ms
 * transition), a floating label, an optional leading icon, and an auto "×" clear button. Every
 * box the user types into — Settings, dialogs, find-in-page — uses this so text entry feels
 * identical and smooth everywhere, rather than each screen rolling its own.
 *
 * Themed entirely through Orbit tokens, so it's correct in light and dark automatically.
 */
@Composable
fun OrbitTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    leadingIcon: ImageVector? = null,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Done,
    onImeAction: (() -> Unit)? = null,
    isError: Boolean = false,
    errorText: String? = null,
    showClear: Boolean = true,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    val scheme = orbit()
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it, style = orbitBody, color = scheme.text.muted) } },
        singleLine = singleLine,
        textStyle = orbitBody,
        shape = RoundedCornerShape(OrbitRadii.card),
        isError = isError,
        supportingText = errorText?.takeIf { isError }?.let { { Text(it) } },
        leadingIcon = leadingIcon?.let {
            { Icon(it, contentDescription = null, tint = scheme.text.muted) }
        },
        trailingIcon = if (showClear && value.isNotEmpty()) {
            {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(Icons.Filled.Close, contentDescription = "Clear", tint = scheme.text.muted)
                }
            }
        } else {
            null
        },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        keyboardActions = KeyboardActions(
            onDone = { onImeAction?.invoke() },
            onGo = { onImeAction?.invoke() },
            onSearch = { onImeAction?.invoke() },
            onSend = { onImeAction?.invoke() },
        ),
        visualTransformation = visualTransformation,
        // Filled tonal container + an accent ring on focus. No hard outline when idle — just a
        // whisper of the muted token — so the field reads as a soft chip until you engage it.
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = scheme.surfaces.elevated,
            unfocusedContainerColor = scheme.surfaces.elevated,
            errorContainerColor = scheme.surfaces.elevated,
            focusedBorderColor = scheme.accent.solid,
            unfocusedBorderColor = scheme.text.muted.copy(alpha = 0.22f),
            errorBorderColor = MaterialTheme.colorScheme.error,
            cursorColor = scheme.accent.solid,
            focusedTextColor = scheme.text.primary,
            unfocusedTextColor = scheme.text.primary,
            focusedLabelColor = scheme.accent.solid,
            unfocusedLabelColor = scheme.text.muted,
            focusedLeadingIconColor = scheme.accent.solid,
            unfocusedLeadingIconColor = scheme.text.muted,
        ),
    )
}
