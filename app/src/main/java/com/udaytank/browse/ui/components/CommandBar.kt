package com.udaytank.browse.ui.components

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.udaytank.browse.browser.TabBadge
import com.udaytank.browse.ui.theme.Orbit
import com.udaytank.browse.ui.theme.OrbitRadii
import com.udaytank.browse.ui.theme.OrbitSpacing
import com.udaytank.browse.ui.theme.orbit
import com.udaytank.browse.ui.theme.orbitBody
import com.udaytank.browse.ui.theme.orbitCaption

/**
 * Orbit's signature component: a floating pill that drives the browser.
 * Collapsed = glanceable address + navigation; editing = full URL field.
 *
 * [pageUrl] is the active tab's real page URL (null on the home page) — it feeds the
 * long-press menu (A5: Copy URL / Paste and go / Share), which only exists when a page is up.
 */
@OptIn(ExperimentalFoundationApi::class)
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
    pageUrl: String? = null,
    /** Incognito active: show a persistent mask indicator in the bar. */
    incognito: Boolean = false,
    /**
     * Home-page (Chrome-NTP) mode: the display state renders as a prominent search pill —
     * search icon + "Search or type URL" + mic (A2 voice) — with no back button. The tab
     * counter and menu stay, so nothing the bottom bar offered is lost on home. Editing
     * behavior is untouched: tapping the pill enters the exact same edit state.
     */
    homePill: Boolean = false,
    /** Voice-search result from the home pill's mic; same typed search-or-url path. */
    onVoiceSubmit: ((String) -> Unit)? = null,
    /**
     * The active Orbit's [colorArgb][com.udaytank.browse.data.OrbitEntity.colorArgb], or null to
     * hide the indicator (incognito, or Orbits not wired). Task 7: a small tappable colored dot
     * that opens the Orbit quick-switch sheet — the Orbit indicator is about normal browsing, so
     * callers pass null while incognito rather than showing a private Orbit's color.
     */
    activeOrbitColor: Int? = null,
    onOpenOrbitSwitch: () -> Unit = {},
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(tween(Orbit.MotionMs, easing = Orbit.Easing)),
        shape = RoundedCornerShape(OrbitRadii.bar),
        color = orbit().surfaces.surface,
        // Hairline border for definition + a whisper of shadow, instead of a heavy 8dp
        // drop-shadow. On the near-white light home canvas the old shadow read as a grey
        // rectangle "halo"; the border keeps the pill crisp in both themes without it.
        tonalElevation = 0.dp,
        shadowElevation = 2.dp,
        border = BorderStroke(1.dp, orbit().text.muted.copy(alpha = 0.18f)),
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
                    if (!homePill) {
                        IconButton(onClick = onBack, enabled = canGoBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                    var barMenuOpen by remember { mutableStateOf(false) }
                    Box(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(CircleShape)
                                .combinedClickable(
                                    onClick = { onEditingChanged(true) },
                                    // A5: page actions on long-press; nothing to act on at home.
                                    onLongClick = { if (pageUrl != null) barMenuOpen = true },
                                )
                                .padding(
                                    horizontal = if (homePill) OrbitSpacing.lg else OrbitSpacing.sm,
                                    vertical = if (homePill) OrbitSpacing.lg else OrbitSpacing.md,
                                ),
                        ) {
                            if (homePill) {
                                Icon(
                                    Icons.Filled.Search,
                                    contentDescription = null,
                                    tint = orbit().text.secondary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            if (incognito) {
                                Icon(
                                    Icons.Filled.VisibilityOff,
                                    contentDescription = "Incognito",
                                    tint = orbit().text.secondary,
                                    modifier = Modifier.size(15.dp),
                                )
                            }
                            if (isSecure) {
                                Icon(
                                    Icons.Filled.Lock,
                                    contentDescription = "Secure connection",
                                    tint = orbit().text.secondary,
                                    modifier = Modifier
                                        .padding(start = if (incognito) 6.dp else 0.dp)
                                        .size(14.dp),
                                )
                            }
                            Text(
                                text = displayHost ?: "Search or type URL",
                                style = orbitBody,
                                color = if (displayHost != null) {
                                    orbit().text.primary
                                } else {
                                    orbit().text.muted
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(
                                    start = if (isSecure || homePill || incognito) 8.dp else 0.dp,
                                ),
                            )
                        }
                        if (pageUrl != null) {
                            BarLongPressMenu(
                                expanded = barMenuOpen,
                                onDismiss = { barMenuOpen = false },
                                pageUrl = pageUrl,
                                onAddressChange = onAddressChange,
                                onGo = onGo,
                            )
                        }
                    }
                    if (homePill) {
                        // A2 voice search from the pill: spoken text takes the exact typed path.
                        val launchVoice = rememberVoiceSearch { spoken ->
                            onVoiceSubmit?.invoke(spoken)
                        }
                        if (launchVoice != null) {
                            IconButton(onClick = launchVoice) {
                                Icon(Icons.Filled.Mic, contentDescription = "Voice search")
                            }
                        }
                    }
                    if (activeOrbitColor != null) {
                        IconButton(onClick = onOpenOrbitSwitch) {
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .background(Color(activeOrbitColor), CircleShape),
                            )
                        }
                    }
                    IconButton(onClick = onOpenTabs) {
                        Text(
                            text = TabBadge.label(tabCount),
                            style = orbitCaption,
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

/**
 * A5 long-press menu. "Paste and go" submits through the exact typed-input path
 * (onAddressChange + onGo = the VM's search-or-url pipeline); the clipboard is only READ at
 * the moment that item is tapped — the enabled check uses hasText(), which looks at the clip
 * description without touching its content.
 */
@Composable
private fun BarLongPressMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    pageUrl: String,
    onAddressChange: (String) -> Unit,
    onGo: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text("Copy URL") },
            onClick = {
                clipboard.setText(AnnotatedString(pageUrl))
                onDismiss()
            },
        )
        DropdownMenuItem(
            text = { Text("Paste and go") },
            enabled = clipboard.hasText(),
            onClick = {
                val copied = clipboard.getText()?.text?.trim()
                if (!copied.isNullOrBlank()) {
                    onAddressChange(copied)
                    onGo()
                }
                onDismiss()
            },
        )
        DropdownMenuItem(
            text = { Text("Share") },
            onClick = {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, pageUrl)
                }
                context.startActivity(Intent.createChooser(send, "Share link"))
                onDismiss()
            },
        )
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

    // A2 voice search (shared launcher): same pipeline as typed input —
    // text -> VM address state -> search-or-url submit.
    val launchVoice = rememberVoiceSearch { spoken ->
        onAddressChange(spoken)
        onGo()
        onDismiss()
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
            textStyle = orbitBody.copy(color = orbit().text.primary),
            cursorBrush = SolidColor(orbit().accent.solid),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = {
                onGo()
                onDismiss()
            }),
            modifier = Modifier
                .weight(1f)
                .padding(start = OrbitSpacing.lg, top = 16.dp, bottom = 16.dp)
                .focusRequester(focusRequester),
        )
        if (launchVoice != null) {
            IconButton(onClick = launchVoice) {
                Icon(Icons.Filled.Mic, contentDescription = "Voice search")
            }
        }
        IconButton(onClick = onDismiss) {
            Icon(Icons.Filled.Close, contentDescription = "Close editing")
        }
    }
}

/**
 * A2 voice search, shared by the edit-state mic and the home pill's mic. Returns a launch
 * lambda, or null when no recognizer activity exists (checked once, like the original inline
 * wiring). A RESULT_OK first result is handed to [onSpoken].
 */
@Composable
private fun rememberVoiceSearch(onSpoken: (String) -> Unit): (() -> Unit)? {
    val context = LocalContext.current
    val voiceIntent = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
        )
    }
    val voiceAvailable = remember {
        context.packageManager.resolveActivity(voiceIntent, 0) != null
    }
    val currentOnSpoken by rememberUpdatedState(onSpoken)
    val voiceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val spoken = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
        if (result.resultCode == Activity.RESULT_OK && !spoken.isNullOrBlank()) {
            currentOnSpoken(spoken)
        }
    }
    return if (voiceAvailable) ({ voiceLauncher.launch(voiceIntent) }) else null
}
