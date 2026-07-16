package com.udaytank.browse.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.udaytank.browse.ui.theme.orbit

/**
 * Andromeda's ONE Orbit-identity avatar (v4.2 icon-avatars pass): a circle filled with the
 * Orbit's own [colorArgb], with [orbitIcon]'s vector for [iconKey] centered inside in whichever
 * of black/white contrasts more against that fill (picked by [contrastingOn]'s luminance check)
 * — legible on any color the user picks, in both light and dark theme. A real per-identity glyph
 * (Firefox-container style) rather than a bare colored dot.
 *
 * Every place an Orbit shows its identity — the tab-switcher chips, the quick-switch sheet, the
 * bar indicator, the manage sheet's rows and its icon/color pickers — renders this ONE composable
 * instead of a bespoke dot, so a future change to "what an Orbit looks like" only has to happen
 * here.
 *
 * [selected] draws a thin accent ring around the avatar (with a hairline of padding so the ring
 * doesn't touch the fill) — used by rows/pickers to show which Orbit (or icon/color option) is
 * currently chosen, independent of whatever selection styling the surrounding row/chip also does.
 */
@Composable
fun OrbitAvatar(
    colorArgb: Int,
    iconKey: String,
    size: Dp,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    val scheme = orbit()
    val fill = Color(colorArgb)
    val onFill = contrastingOn(fill)
    Box(
        modifier = modifier
            .size(size)
            .then(
                if (selected) {
                    Modifier
                        .border(2.dp, scheme.accent.solid, CircleShape)
                        .padding(2.dp)
                } else {
                    Modifier
                },
            )
            .clip(CircleShape)
            .background(fill),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            orbitIcon(iconKey),
            contentDescription = null,
            tint = onFill,
            modifier = Modifier.size(size * 0.55f),
        )
    }
}

/** Picks black or white — whichever contrasts more — for content drawn on top of [bg]. */
fun contrastingOn(bg: Color): Color = if (bg.luminance() > 0.5f) Color.Black else Color.White
