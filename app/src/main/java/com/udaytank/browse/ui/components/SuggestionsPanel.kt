package com.udaytank.browse.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.udaytank.browse.browser.Suggestion
import com.udaytank.browse.browser.SuggestionKind
import com.udaytank.browse.browser.UrlHosts
import com.udaytank.browse.ui.theme.OrbitRadii
import com.udaytank.browse.ui.theme.OrbitSpacing
import com.udaytank.browse.ui.theme.orbit
import com.udaytank.browse.ui.theme.orbitBody
import com.udaytank.browse.ui.theme.orbitCaption

/**
 * [copiedUrl] is the clipboard chip (A6): a URL read ONCE when the bar entered edit state,
 * shown as a distinct first row ("Go to copied link") that navigates directly on tap.
 */
@Composable
fun SuggestionsPanel(
    suggestions: List<Suggestion>,
    onPick: (Suggestion) -> Unit,
    modifier: Modifier = Modifier,
    copiedUrl: String? = null,
    onPickCopied: (String) -> Unit = {},
) {
    val scheme = orbit()
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(OrbitRadii.card),
        color = scheme.surfaces.elevated,
        tonalElevation = 0.dp,
        shadowElevation = 3.dp,
    ) {
        Column {
            if (copiedUrl != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPickCopied(copiedUrl) }
                        .padding(horizontal = OrbitSpacing.lg, vertical = OrbitSpacing.md),
                ) {
                    Icon(
                        imageVector = Icons.Filled.ContentPaste,
                        contentDescription = null,
                        tint = scheme.accent.solid,
                        modifier = Modifier.size(22.dp),
                    )
                    Column(modifier = Modifier.padding(start = OrbitSpacing.md)) {
                        Text(
                            "Go to copied link",
                            style = orbitBody,
                            color = scheme.accent.solid,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            UrlHosts.of(copiedUrl) ?: copiedUrl,
                            style = orbitCaption,
                            color = scheme.text.muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            suggestions.forEach { suggestion ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(suggestion) }
                        .padding(horizontal = OrbitSpacing.lg, vertical = OrbitSpacing.md),
                ) {
                    // Site rows lead with the real favicon (Chrome-style); a plain search query
                    // keeps the search glyph.
                    if (suggestion.kind == SuggestionKind.SEARCH) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = null,
                            tint = scheme.text.muted,
                            modifier = Modifier.size(22.dp),
                        )
                    } else {
                        FaviconOrLetter(
                            url = suggestion.url,
                            label = suggestion.title.ifBlank { suggestion.url },
                            size = 24.dp,
                        )
                    }
                    Column(modifier = Modifier.padding(start = OrbitSpacing.md)) {
                        Text(
                            suggestion.title,
                            style = orbitBody,
                            color = scheme.text.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (suggestion.kind != SuggestionKind.SEARCH) {
                            Text(
                                UrlHosts.of(suggestion.url) ?: suggestion.url,
                                style = orbitCaption,
                                color = scheme.text.muted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}
