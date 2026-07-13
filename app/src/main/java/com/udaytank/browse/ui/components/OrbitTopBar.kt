package com.udaytank.browse.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.udaytank.browse.ui.theme.OrbitSpacing
import com.udaytank.browse.ui.theme.orbit
import com.udaytank.browse.ui.theme.orbitTitle

/**
 * Andromeda's ONE top-app-bar shape (Orbit v3.1 §6/§9): a 56dp bar with a back chevron, an
 * [orbitTitle] title, and an optional end-aligned [actions] slot — on [orbit]'s base surface so
 * it reads as part of the screen canvas rather than a separate elevated strip. Every library/
 * detail screen (Bookmarks/History/Downloads/Reading list/saved-article reader) renders this
 * instead of a bespoke M3 `TopAppBar`, so height/type/spacing are identical everywhere.
 */
@Composable
fun OrbitTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    val scheme = orbit()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(scheme.surfaces.base)
            .padding(horizontal = OrbitSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = scheme.text.primary,
            )
        }
        Text(
            title,
            style = orbitTitle,
            color = scheme.text.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(start = OrbitSpacing.sm),
        )
        if (actions != null) {
            Row(verticalAlignment = Alignment.CenterVertically, content = actions)
        }
    }
}
