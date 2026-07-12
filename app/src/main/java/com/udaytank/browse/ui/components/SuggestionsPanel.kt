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
import com.udaytank.browse.browser.Suggestion
import com.udaytank.browse.browser.SuggestionKind
import com.udaytank.browse.browser.UrlHosts

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
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 4.dp,
        shadowElevation = 6.dp,
    ) {
        Column {
            if (copiedUrl != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPickCopied(copiedUrl) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.ContentPaste,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Column(modifier = Modifier.padding(start = 12.dp)) {
                        Text(
                            "Go to copied link",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            UrlHosts.of(copiedUrl) ?: copiedUrl,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Icon(
                        imageVector = when (suggestion.kind) {
                            SuggestionKind.BOOKMARK -> Icons.Filled.Star
                            SuggestionKind.HISTORY -> Icons.Filled.History
                            SuggestionKind.SEARCH -> Icons.Filled.Search
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                    Column(modifier = Modifier.padding(start = 12.dp)) {
                        Text(
                            suggestion.title,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (suggestion.kind != SuggestionKind.SEARCH) {
                            Text(
                                suggestion.url,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
