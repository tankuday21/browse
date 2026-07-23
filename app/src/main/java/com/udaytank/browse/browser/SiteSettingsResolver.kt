package com.udaytank.browse.browser

import com.udaytank.browse.data.SiteSettingsEntity

/**
 * Pure merge of the global display settings with a per-site override row (H6).
 *
 * Tri-state fields in [SiteSettingsEntity] mean: -1 = no opinion (fall through to the global
 * setting), 0 = explicitly off for this site, 1 = explicitly on. textZoom follows the same
 * shape since I3: a positive per-site value is absolute and wins; unset (-1 / any non-positive
 * value) falls through to [resolve]'s globalTextZoom — the user's global text scale (100 = the
 * WebView default when that setting is untouched).
 */
object SiteSettingsResolver {
    data class Effective(
        val textZoom: Int,
        val forceDark: Boolean,
        val desktopMode: Boolean,
        val blockImages: Boolean,
    )

    fun resolve(
        globalForceDark: Boolean,
        globalDesktop: Boolean,
        globalTextZoom: Int,
        globalBlockImages: Boolean,
        override: SiteSettingsEntity?,
    ): Effective =
        Effective(
            textZoom = override?.textZoom?.takeIf { it > 0 } ?: globalTextZoom,
            forceDark = resolveTriState(override?.forceDark, globalForceDark),
            desktopMode = resolveTriState(override?.desktopMode, globalDesktop),
            blockImages = resolveTriState(override?.blockImages, globalBlockImages),
        )

    private fun resolveTriState(override: Int?, global: Boolean): Boolean = when (override) {
        0 -> false
        1 -> true
        else -> global // -1 or no row: the global setting decides
    }
}
