package com.udaytank.browse.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.udaytank.browse.ui.theme.OrbitSpacing
import com.udaytank.browse.ui.theme.orbit
import com.udaytank.browse.ui.theme.orbitBody
import com.udaytank.browse.ui.theme.orbitCaption

/**
 * Andromeda's ONE list-row shape (Orbit v3.1 §6/§9): a 56dp-minimum row with [OrbitSpacing.lg]
 * side padding, a 24dp leading icon tinted [com.udaytank.browse.ui.theme.OrbitText.secondary],
 * an [orbitBody]/text.primary title, an optional [orbitCaption]/text.muted subtitle, and a
 * trailing slot for whatever a screen needs there (badge, icon button, switch, chip, …).
 *
 * Every library screen (Bookmarks/History/Downloads/Reading list) renders its rows through
 * this composable so spacing/type/icon-size/ripple are identical everywhere; screens keep
 * their own bespoke content (sparklines, swipe-to-dismiss, offline dot, …) by passing it as
 * part of [trailing] or by wrapping this row in their own gesture container.
 *
 * [onClick] is optional: a row that only exposes a trailing action (no whole-row tap target)
 * can omit it and this composable renders with no click modifier / no ripple at all.
 */
@Composable
fun OrbitListRow(
    leadingIcon: ImageVector?,
    title: String,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val scheme = orbit()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = OrbitSpacing.lg, vertical = OrbitSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OrbitSpacing.lg),
    ) {
        if (leadingIcon != null) {
            Icon(
                leadingIcon,
                contentDescription = null,
                tint = scheme.text.secondary,
                modifier = Modifier.size(24.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = orbitBody,
                color = scheme.text.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = orbitCaption,
                    color = scheme.text.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing?.invoke()
    }
}
