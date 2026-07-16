package com.udaytank.browse.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.udaytank.browse.browser.BarState
import com.udaytank.browse.ui.theme.OrbitMotion
import com.udaytank.browse.ui.theme.OrbitRadii
import com.udaytank.browse.ui.theme.OrbitSpacing
import com.udaytank.browse.ui.theme.orbit

/** Full-size floating pill height — the Orbit spec's "56dp floating rounded pill". */
val OmniBarHeight = 56.dp

/** Collapsed grab-pill height shown while [BarState.Slim] (not editing). */
private val OmniBarSlimHeight = 30.dp

/** Slim-pill hit target — kept comfortably tappable (2x [OrbitSpacing.xxl]) rather than a bare literal. */
private val OmniBarSlimWidth = OrbitSpacing.xxl * 2

/** Side/bottom insets that float the bar off the screen edges (Orbit spacing scale). */
val OmniBarInset: androidx.compose.ui.unit.Dp = OrbitSpacing.md

/**
 * Total vertical footprint the bar occupies at its Full size. Content laid out above it (the
 * active WebView, the Home canvas) pads its bottom by exactly this much so the page never
 * draws underneath the bar. The inset is held constant across Full/Slim — shrinking the bar
 * gives back visual breathing room without reflowing/re-measuring the page underneath it.
 */
val OmniBarReservedHeight: androidx.compose.ui.unit.Dp = OmniBarHeight + OmniBarInset

/**
 * Footprint the bar occupies while [BarState.Slim] (just the grab-pill + its inset). Content
 * tracks this smaller value when the bar shrinks, so the page grows to fill the space the bar
 * vacates instead of leaving an empty band below the pill.
 */
val OmniBarSlimReservedHeight: androidx.compose.ui.unit.Dp = OmniBarSlimHeight + OmniBarInset

/**
 * Andromeda's ONE bottom bar (v3.1 OmniBar). Home and every web page render this exact
 * composable, in the exact same bottom-anchored position — that's what removes the old
 * home <-> web layout jump (a centered pill moving to a bottom-anchored one on first navigation).
 *
 * - [BarState.Full] or [isEditing]: the 56dp pill hosting [CommandBar] verbatim — back, the
 *   address/omnibox (or the home search-pill display when [homePill]), the tab counter, and
 *   the menu button. Editing (text field, voice mic, long-press paste/copy/share, the A6
 *   clipboard-chip suggestion rendered by the caller above this bar) is unchanged, just
 *   restyled to Orbit tokens inside [CommandBar] itself.
 * - [BarState.Slim] (only reachable while not editing — a scrolled-down web page): the bar
 *   shrinks, via [OrbitMotion.structuralDp], to a small centered grab-pill showing just a
 *   handle. Tapping it invokes [onBarTap] — the caller's hook to force [BarState.Full] again
 *   (resets the scroll hysteresis; a tap isn't itself a scroll event, so [BarState] can't
 *   flip back on its own without this).
 *
 * Content-swap between the full bar and the slim pill is a discrete switch rather than a
 * cross-fade (the height/shape animation already carries the "shrink" motion); this keeps the
 * bar's own interactive elements from ever being present-but-invisible mid-transition.
 */
@Composable
fun OmniBar(
    barState: BarState,
    homePill: Boolean,
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
    onBarTap: () -> Unit,
    modifier: Modifier = Modifier,
    pageUrl: String? = null,
    incognito: Boolean = false,
    onVoiceSubmit: ((String) -> Unit)? = null,
    /** Task 7 Orbit indicator — passed straight through to [CommandBar]. */
    activeOrbitColor: Int? = null,
    onOpenOrbitSwitch: () -> Unit = {},
) {
    val scheme = orbit()
    val expanded = isEditing || barState == BarState.Full
    // Slim -> Full snaps immediately (no grow animation): CommandBar's Row (back/address/
    // tab-count/menu) is measured at its natural 56dp the instant it's composed, so it's never
    // laid out under a too-small, still-animating height and can't clip/squish. Full -> Slim
    // still shrinks smoothly via OrbitMotion, since only the simple grab-pill is on screen then.
    val height by animateDpAsState(
        targetValue = if (expanded) OmniBarHeight else OmniBarSlimHeight,
        animationSpec = if (expanded) snap() else OrbitMotion.structuralDp(),
        label = "OmniBarHeight",
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height),
        contentAlignment = Alignment.Center,
    ) {
        if (expanded) {
            CommandBar(
                displayHost = displayHost,
                addressBarText = addressBarText,
                isSecure = isSecure,
                isLoading = isLoading,
                progress = progress,
                canGoBack = canGoBack,
                tabCount = tabCount,
                isEditing = isEditing,
                onEditingChanged = onEditingChanged,
                onAddressChange = onAddressChange,
                onGo = onGo,
                onBack = onBack,
                onOpenTabs = onOpenTabs,
                onMenuClick = onMenuClick,
                menu = menu,
                pageUrl = pageUrl,
                homePill = homePill,
                incognito = incognito,
                onVoiceSubmit = onVoiceSubmit,
                activeOrbitColor = activeOrbitColor,
                onOpenOrbitSwitch = onOpenOrbitSwitch,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Surface(
                shape = RoundedCornerShape(percent = OrbitRadii.pill),
                // Elevated fill + hairline border, not a stark pure-white box: in light
                // theme surfaces.surface is #FFFFFF, so the old slim pill was a hard white
                // nub with a heavy shadow. The elevated tone + border reads as a refined chip.
                color = scheme.surfaces.elevated,
                tonalElevation = 0.dp,
                shadowElevation = 2.dp,
                border = BorderStroke(1.dp, scheme.text.muted.copy(alpha = 0.20f)),
                modifier = Modifier
                    .size(width = OmniBarSlimWidth, height = OmniBarSlimHeight)
                    .clickable { onBarTap() }
                    .semantics { contentDescription = "Show address bar" },
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 28.dp, height = 4.dp)
                            .background(scheme.text.muted, RoundedCornerShape(percent = OrbitRadii.pill)),
                    )
                }
            }
        }
    }
}
