package com.udaytank.browse.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import com.udaytank.browse.ui.theme.OrbitRadii
import com.udaytank.browse.ui.theme.OrbitSpacing
import com.udaytank.browse.ui.theme.orbit
import com.udaytank.browse.ui.theme.orbitBody
import com.udaytank.browse.ui.theme.orbitCaption

@Composable
fun FindBar(
    query: String,
    active: Int,
    total: Int,
    onQueryChange: (String) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    val scheme = orbit()
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(OrbitRadii.bar),
        color = scheme.surfaces.elevated,
        tonalElevation = 0.dp,
        shadowElevation = 2.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = orbitBody.copy(color = scheme.text.primary),
                cursorBrush = SolidColor(scheme.accent.solid),
                decorationBox = { inner ->
                    if (query.isEmpty()) {
                        Text("Find on page", style = orbitBody, color = scheme.text.muted)
                    }
                    inner()
                },
                modifier = Modifier
                    .weight(1f)
                    .padding(start = OrbitSpacing.xl, top = OrbitSpacing.lg, bottom = OrbitSpacing.lg)
                    .focusRequester(focusRequester),
            )
            Text(
                text = if (total > 0) "$active/$total" else "0/0",
                style = orbitCaption,
                color = scheme.text.muted,
            )
            IconButton(onClick = onPrev) {
                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Previous match")
            }
            IconButton(onClick = onNext) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Next match")
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "Close find")
            }
        }
    }
}
