package com.udaytank.browse.browser

import com.udaytank.browse.data.SiteSettingsEntity

/**
 * Pure merge of the global display settings with a per-site override row (H6).
 *
 * Tri-state fields in [SiteSettingsEntity] mean: -1 = no opinion (fall through to the global
 * setting), 0 = explicitly off for this site, 1 = explicitly on. textZoom has no global
 * counterpart, so unset (-1 / any non-positive value) resolves to the WebView default of 100%.
 */
object SiteSettingsResolver {
    data class Effective(val textZoom: Int, val forceDark: Boolean, val desktopMode: Boolean)

    fun resolve(globalForceDark: Boolean, globalDesktop: Boolean, override: SiteSettingsEntity?): Effective =
        Effective(
            textZoom = override?.textZoom?.takeIf { it > 0 } ?: 100,
            forceDark = resolveTriState(override?.forceDark, globalForceDark),
            desktopMode = resolveTriState(override?.desktopMode, globalDesktop),
        )

    private fun resolveTriState(override: Int?, global: Boolean): Boolean = when (override) {
        0 -> false
        1 -> true
        else -> global // -1 or no row: the global setting decides
    }
}
