package com.udaytank.browse.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.udaytank.browse.ui.theme.Orbit

/**
 * Orbit's signature component: a floating pill that drives the browser.
 * Collapsed = glanceable address + navigation; editing = full URL field.
 */
@Composable
fun CommandBar(
    displayHost: String?,
    addressBarText: String,
    isSecure: Boolean,
    isLoading: Boolean,
    progress: Int,
    canGoBack: Boolean,
    tabCount: Int,
    isEditing: Boolean,
    onEditingChanged: (Boolean) -> Unit,
    onAddressChange: (String) -> Unit,
    onGo: () -> Unit,
    onBack: () -> Unit,
    onOpenTabs: () -> Unit,
    onMenuClick: () -> Unit,
    menu: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(tween(Orbit.MotionMs, easing = Orbit.Easing)),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
    ) {
        Box {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isEditing) {
                    EditingContent(
                        addressBarText = addressBarText,
                        onAddressChange = onAddressChange,
                        onGo = onGo,
                        onDismiss = { onEditingChanged(false) },
                    )
                } else {
                    IconButton(onClick = onBack, enabled = canGoBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .weight(1f)
                            .clip(CircleShape)
                            .clickable { onEditingChanged(true) }
                            .padding(horizontal = 10.dp, vertical = 12.dp),
                    ) {
                        if (isSecure) {
                            Icon(
                                Icons.Filled.Lock,
                                contentDescription = "Secure connection",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                        Text(
                            text = displayHost ?: "Search or type URL",
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (displayHost != null) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = if (isSecure) 6.dp else 0.dp),
                        )
                    }
                    IconButton(onClick = onOpenTabs) {
                        Text(
                            text = "$tabCount",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier
                                .border(1.5.dp, LocalContentColor.current, RoundedCornerShape(6.dp))
                                .padding(horizontal = 7.dp, vertical = 2.dp),
                        )
                    }
                    Box {
                        IconButton(onClick = onMenuClick) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Menu")
                        }
                        menu()
                    }
                }
            }
            // Orbit gradient progress along the pill's top edge.
            if (isLoading && !isEditing) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth(fraction = progress.coerceIn(0, 100) / 100f)
                        .height(3.dp)
                        .background(Orbit.Gradient),
                )
            }
        }
    }
}

@Composable
private fun EditingContent(
    addressBarText: String,
    onAddressChange: (String) -> Unit,
    onGo: () -> Unit,
    onDismiss: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    // Select-all on entry so typing replaces the URL, like every real browser.
    var fieldValue by remember {
        mutableStateOf(
            TextFieldValue(addressBarText, selection = TextRange(0, addressBarText.length))
        )
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        BasicTextField(
            value = fieldValue,
            onValueChange = {
                fieldValue = it
                onAddressChange(it.text)
            },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onSurface,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = {
                onGo()
                onDismiss()
            }),
            modifier = Modifier
                .weight(1f)
                .padding(start = 20.dp, top = 16.dp, bottom = 16.dp)
                .focusRequester(focusRequester),
        )
        IconButton(onClick = onDismiss) {
            Icon(Icons.Filled.Close, contentDescription = "Close editing")
        }
    }
}
