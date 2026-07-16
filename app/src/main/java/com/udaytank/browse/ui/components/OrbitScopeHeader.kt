package com.udaytank.browse.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.udaytank.browse.data.OrbitEntity
import com.udaytank.browse.ui.theme.OrbitSpacing
import com.udaytank.browse.ui.theme.orbit
import com.udaytank.browse.ui.theme.orbitCaption

/**
 * A slim strip that names whose data is on screen: the [activeOrbit]'s avatar + "<name> ·
 * <scope>", closed by a hairline tinted in the Orbit's own color — the same ambient-identity cue
 * the tab switcher uses. Reused by every Orbit-scoped library screen (History, Bookmarks, …) so a
 * user always knows which container they're looking at. [scope] is the lowercase noun, e.g.
 * "history" or "bookmarks".
 */
@Composable
fun OrbitScopeHeader(activeOrbit: OrbitEntity, scope: String, modifier: Modifier = Modifier) {
    val scheme = orbit()
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = OrbitSpacing.lg, vertical = OrbitSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(OrbitSpacing.sm),
        ) {
            OrbitAvatar(colorArgb = activeOrbit.colorArgb, iconKey = activeOrbit.iconKey, size = 24.dp)
            Text(
                text = "${activeOrbit.name} · $scope",
                style = orbitCaption,
                color = scheme.text.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(Color(activeOrbit.colorArgb).copy(alpha = 0.6f)),
        )
    }
}
