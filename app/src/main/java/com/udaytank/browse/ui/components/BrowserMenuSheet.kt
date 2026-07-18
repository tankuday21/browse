package com.udaytank.browse.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddHome
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.udaytank.browse.ui.theme.OrbitRadii
import com.udaytank.browse.ui.theme.OrbitSpacing
import com.udaytank.browse.ui.theme.orbit
import com.udaytank.browse.ui.theme.orbitBody
import com.udaytank.browse.ui.theme.orbitCaption

/**
 * The browser's overflow (⋮) menu, as an M3 [ModalBottomSheet] (Orbit v3.1 spec §5) replacing
 * the old corner `DropdownMenu`. This is a CONTAINER + STYLING change only: every action,
 * enabled-condition, and badge below mirrors the prior `DropdownMenu` in `BrowserScreen.kt` 1:1
 * — callers pass the exact same callbacks/state, each already wired (by the caller) to close the
 * sheet then run, exactly as every `DropdownMenuItem.onClick` used to set `menuOpen = false` then
 * act. Swipe-down / scrim-tap dismiss is the `ModalBottomSheet` default (via [onDismissRequest]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserMenuSheet(
    onDismissRequest: () -> Unit,
    // ── Icon action row (back / forward / refresh / share / bookmark star / add-to-home) ──
    canGoBack: Boolean,
    canGoForward: Boolean,
    hasPage: Boolean,
    isBookmarked: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onReload: () -> Unit,
    onShare: () -> Unit,
    onToggleBookmark: () -> Unit,
    onAddToHome: () -> Unit,
    // ── New tabs ──
    onNewTab: () -> Unit,
    onNewIncognitoTab: () -> Unit,
    // ── Library ──
    onOpenBookmarks: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenPasswords: () -> Unit,
    onScanQr: () -> Unit,
    onOpenDownloads: () -> Unit,
    activeDownloadCount: Int,
    onOpenReadingList: () -> Unit,
    unreadCount: Int,
    // ── This page ──
    onSaveForLater: () -> Unit,
    isHome: Boolean,
    readerActive: Boolean,
    onToggleReaderMode: () -> Unit,
    onFindInPage: () -> Unit,
    // ── Site controls ──
    isDesktopSite: Boolean,
    onToggleDesktopSite: () -> Unit,
    currentHost: String?,
    onOpenSiteSettings: () -> Unit,
    onPrint: () -> Unit,
    // ── Element Zapper (v4.0) ──
    onZapElement: () -> Unit,
    onOpenHiddenElements: () -> Unit,
    hiddenCount: Int,
    // ── App ──
    onOpenSettings: () -> Unit,
    // ── Ad-block footer (only shown when there is a current host, exactly as before) ──
    blockedOnPage: Int,
    isAdAllowedOnSite: Boolean,
    onToggleAdAllowlist: () -> Unit,
) {
    val scheme = orbit()
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = scheme.surfaces.elevated,
        shape = RoundedCornerShape(topStart = OrbitRadii.bar, topEnd = OrbitRadii.bar),
    ) {
        // Scrollable so every row stays reachable: the full action list (~13 rows) is
        // taller than the sheet's partially-expanded height, so without this the tail
        // (Settings, the ad-block footer) was clipped below the fold and unreachable.
        // navigationBarsPadding keeps the last row clear of the gesture bar.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding(),
        ) {
        // ── Icon-only action row: the five most-reached page actions plus the bookmark
        // star (the star toggle predates the reorg; nothing is lost). ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = OrbitSpacing.sm, vertical = OrbitSpacing.xs),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(enabled = canGoBack, onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            IconButton(enabled = canGoForward, onClick = onForward) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Forward")
            }
            IconButton(onClick = onReload) {
                Icon(Icons.Filled.Refresh, contentDescription = "Reload")
            }
            IconButton(enabled = hasPage, onClick = onShare) {
                Icon(Icons.Filled.Share, contentDescription = "Share page")
            }
            IconButton(enabled = hasPage, onClick = onToggleBookmark) {
                Icon(
                    if (isBookmarked) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = if (isBookmarked) "Remove bookmark" else "Add bookmark",
                    tint = if (isBookmarked) scheme.accent.solid else scheme.text.secondary,
                )
            }
            IconButton(enabled = hasPage, onClick = onAddToHome) {
                Icon(Icons.Filled.AddHome, contentDescription = "Add to home")
            }
        }
        HorizontalDivider()

        // ── New tabs ────────────────────────────────
        MenuRow(icon = Icons.Filled.Add, label = "New tab", onClick = onNewTab)
        MenuRow(icon = Icons.Filled.VisibilityOff, label = "New incognito tab", onClick = onNewIncognitoTab)
        HorizontalDivider()

        // ── Library ─────────────────────────────────
        MenuRow(icon = Icons.Filled.Bookmarks, label = "Bookmarks", onClick = onOpenBookmarks)
        MenuRow(icon = Icons.Filled.History, label = "History", onClick = onOpenHistory)
        MenuRow(icon = Icons.Filled.Key, label = "Passwords", onClick = onOpenPasswords)
        MenuRow(icon = Icons.Filled.QrCodeScanner, label = "Scan QR code", onClick = onScanQr)
        MenuRow(
            icon = Icons.Filled.Download,
            label = "Downloads",
            badgeCount = activeDownloadCount,
            onClick = onOpenDownloads,
        )
        MenuRow(
            icon = Icons.AutoMirrored.Filled.MenuBook,
            label = "Reading list",
            badgeCount = unreadCount,
            onClick = onOpenReadingList,
        )
        HorizontalDivider()

        // ── This page ───────────────────────────────
        MenuRow(
            icon = Icons.Filled.BookmarkAdd,
            label = "Save for later",
            enabled = hasPage,
            onClick = onSaveForLater,
        )
        MenuRow(
            icon = Icons.AutoMirrored.Filled.Article,
            label = if (readerActive) "Exit reader" else "Reader mode",
            enabled = !isHome,
            onClick = onToggleReaderMode,
        )
        MenuRow(
            icon = Icons.Filled.FindInPage,
            label = "Find in page",
            enabled = !isHome,
            onClick = onFindInPage,
        )
        HorizontalDivider()

        // ── Site controls ───────────────────────────
        MenuRow(
            icon = Icons.Filled.Computer,
            label = if (isDesktopSite) "Mobile site" else "Desktop site",
            enabled = !isHome,
            onClick = onToggleDesktopSite,
        )
        MenuRow(
            icon = Icons.Filled.Tune,
            label = "Site settings",
            enabled = currentHost != null,
            onClick = onOpenSiteSettings,
        )
        MenuRow(
            icon = Icons.Filled.Print,
            label = "Print / Save as PDF",
            enabled = hasPage,
            onClick = onPrint,
        )
        MenuRow(
            icon = Icons.Filled.Bolt,
            label = "Zap element",
            enabled = !isHome,
            onClick = onZapElement,
        )
        MenuRow(
            icon = Icons.Filled.VisibilityOff,
            label = "Hidden elements",
            enabled = hiddenCount > 0,
            badgeCount = hiddenCount,
            onClick = onOpenHiddenElements,
        )
        HorizontalDivider()

        // ── App ─────────────────────────────────────
        MenuRow(icon = Icons.Filled.Settings, label = "Settings", onClick = onOpenSettings)

        if (currentHost != null) {
            HorizontalDivider()
            Text(
                "$blockedOnPage ads blocked on this page",
                style = orbitCaption,
                color = scheme.text.muted,
                modifier = Modifier.padding(horizontal = OrbitSpacing.lg, vertical = OrbitSpacing.sm),
            )
            MenuRow(
                icon = Icons.Filled.Block,
                label = if (isAdAllowedOnSite) "Block ads on this site" else "Allow ads on this site",
                onClick = onToggleAdAllowlist,
            )
        }
        }
    }
}

/** One grouped-list row: leading icon, title, optional trailing count badge — Orbit tokens throughout. */
@Composable
private fun MenuRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    badgeCount: Int = 0,
) {
    val scheme = orbit()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = OrbitSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OrbitSpacing.lg),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (enabled) scheme.accent.solid else scheme.text.muted,
            modifier = Modifier.size(24.dp),
        )
        Text(
            label,
            style = orbitBody,
            color = if (enabled) scheme.text.primary else scheme.text.muted,
            modifier = Modifier.weight(1f),
        )
        if (badgeCount > 0) {
            Badge { Text("$badgeCount") }
        }
    }
}
