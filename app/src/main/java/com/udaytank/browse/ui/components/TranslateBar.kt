package com.udaytank.browse.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Translate
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.udaytank.browse.translate.TranslateLang
import com.udaytank.browse.translate.TranslateState
import com.udaytank.browse.ui.theme.OrbitSpacing
import com.udaytank.browse.ui.theme.orbit
import com.udaytank.browse.ui.theme.orbitBody
import com.udaytank.browse.ui.theme.orbitCaption

/**
 * Slim status bar for full-page translation (v6.1), shown above the command bar whenever the
 * translate state isn't Idle. Progress states are non-interactive; [TranslateState.Shown] offers
 * a target-language switch and "Show original"; terminal states offer dismiss.
 */
@Composable
fun TranslateBar(
    state: TranslateState,
    onShowOriginal: () -> Unit,
    onChangeTarget: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state is TranslateState.Idle) return
    val scheme = orbit()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(scheme.surfaces.elevated, RoundedCornerShape(OrbitRadiiBar))
            .padding(horizontal = OrbitSpacing.md, vertical = OrbitSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OrbitSpacing.sm),
    ) {
        when (state) {
            is TranslateState.Detecting,
            is TranslateState.Downloading,
            is TranslateState.Translating,
            -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = scheme.accent.solid,
                )
                Text(
                    text = when (state) {
                        is TranslateState.Downloading -> "Downloading ${state.language} — one time (~30 MB)"
                        is TranslateState.Translating -> "Translating…"
                        else -> "Detecting language…"
                    },
                    style = orbitBody,
                    color = scheme.text.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                // A dismiss is always reachable — even a wedged download/collect can be cancelled.
                DismissButton(onDismiss)
            }

            is TranslateState.Shown -> {
                Icon(Icons.Filled.Translate, contentDescription = null, tint = scheme.accent.solid, modifier = Modifier.size(18.dp))
                Text(
                    "Translated to ${state.targetName}",
                    style = orbitBody,
                    color = scheme.text.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                TargetLanguageButton(current = state.target, onPick = onChangeTarget)
                TextButton(onClick = onShowOriginal) { Text("Show original", color = scheme.accent.solid) }
            }

            is TranslateState.AlreadyTarget -> {
                Text(
                    "Already in ${state.languageName}",
                    style = orbitBody,
                    color = scheme.text.primary,
                    modifier = Modifier.weight(1f),
                )
                DismissButton(onDismiss)
            }

            is TranslateState.Error -> {
                Text(
                    state.message,
                    style = orbitBody,
                    color = scheme.text.primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                DismissButton(onDismiss)
            }

            TranslateState.Idle -> Unit
        }
    }
}

@Composable
private fun TargetLanguageButton(current: String, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    TextButton(onClick = { open = true }) {
        Text(TranslateLang.displayName(current), style = orbitCaption, color = orbit().text.secondary)
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        TranslateLang.SUPPORTED.forEach { (code, name) ->
            DropdownMenuItem(
                text = { Text(name) },
                onClick = { open = false; onPick(code) },
            )
        }
    }
}

@Composable
private fun DismissButton(onDismiss: () -> Unit) {
    androidx.compose.material3.IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
        Icon(Icons.Filled.Close, contentDescription = "Dismiss", tint = orbit().text.secondary, modifier = Modifier.size(18.dp))
    }
}

private val OrbitRadiiBar = 22.dp
