package com.udaytank.browse.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.udaytank.browse.browser.Suggestion
import com.udaytank.browse.ui.theme.Orbit
import com.udaytank.browse.ui.theme.OrbitMotion
import com.udaytank.browse.ui.theme.OrbitRadii
import com.udaytank.browse.ui.theme.OrbitSpacing
import com.udaytank.browse.ui.theme.orbit
import com.udaytank.browse.ui.theme.orbitBody

/**
 * The Chrome-NTP search experience for the home page (v4.1). Tapping the centered home pill opens
 * this full-screen overlay: the search field animates in at the TOP, live suggestions fill the
 * middle, and the keyboard sits at the bottom — one clear input, no competing bottom bar. On web
 * pages the shared bottom OmniBar still owns editing; this is home-only.
 *
 * State is the caller's (the VM's address text + suggestions); this is pure presentation.
 */
@Composable
fun HomeSearchOverlay(
    visible: Boolean,
    addressText: String,
    suggestions: List<Suggestion>,
    copiedUrl: String?,
    onTextChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onPick: (Suggestion) -> Unit,
    onPickCopied: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(OrbitMotion.StandardMs)) +
            slideInVertically(tween(OrbitMotion.StandardMs, easing = Orbit.Easing)) { -it / 8 },
        exit = fadeOut(tween(OrbitMotion.StandardMs / 2)) +
            slideOutVertically(tween(OrbitMotion.StandardMs / 2)) { -it / 8 },
    ) {
        val scheme = orbit()
        val focusRequester = remember { FocusRequester() }
        // Seed from the current address and select it all, so the first keystroke replaces it —
        // exactly like Chrome's omnibox when you tap the NTP search box.
        var field by remember {
            mutableStateOf(TextFieldValue(addressText, selection = TextRange(0, addressText.length)))
        }
        LaunchedEffect(Unit) { focusRequester.requestFocus() }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(scheme.surfaces.base),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(OrbitSpacing.md)
                    .imePadding(),
            ) {
                // Top search field — a rounded tonal pill with a back affordance and clear "×".
                Surface(
                    shape = RoundedCornerShape(OrbitRadii.bar),
                    color = scheme.surfaces.elevated,
                    tonalElevation = 0.dp,
                    shadowElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Close search",
                                tint = scheme.text.secondary,
                            )
                        }
                        Icon(
                            Icons.Filled.Search,
                            contentDescription = null,
                            tint = scheme.text.muted,
                            modifier = Modifier.padding(end = OrbitSpacing.sm),
                        )
                        BasicTextField(
                            value = field,
                            onValueChange = {
                                field = it
                                onTextChange(it.text)
                            },
                            singleLine = true,
                            textStyle = orbitBody.copy(color = scheme.text.primary),
                            cursorBrush = SolidColor(scheme.accent.solid),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                            keyboardActions = KeyboardActions(onGo = { onSubmit() }),
                            decorationBox = { inner ->
                                if (field.text.isEmpty()) {
                                    Text("Search or type URL", style = orbitBody, color = scheme.text.muted)
                                }
                                inner()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .padding(vertical = OrbitSpacing.lg)
                                .focusRequester(focusRequester),
                        )
                        if (field.text.isNotEmpty()) {
                            IconButton(onClick = {
                                field = TextFieldValue("")
                                onTextChange("")
                            }) {
                                Icon(Icons.Filled.Close, contentDescription = "Clear", tint = scheme.text.muted)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(OrbitSpacing.md))

                // Suggestions fill the space between the field and the keyboard.
                if (suggestions.isNotEmpty() || copiedUrl != null) {
                    SuggestionsPanel(
                        suggestions = suggestions,
                        onPick = onPick,
                        copiedUrl = copiedUrl,
                        onPickCopied = onPickCopied,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
