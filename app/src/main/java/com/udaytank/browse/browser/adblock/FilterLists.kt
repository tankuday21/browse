package com.udaytank.browse.browser.adblock

/**
 * One filter list the app knows about: where its bundled snapshot lives, where fresh copies
 * come from, and how Settings should present it.
 *
 * A list's effective source is filesDir/adblock/<id>.txt (written by [FilterListUpdater])
 * when that exists, else the bundled asset at [assetPath] — the asset is the permanent
 * fallback, so a wiped update can never leave the engine list-less.
 */
data class FilterListDef(
    val id: String,
    val label: String,
    /** Short user-facing description ("Ads", "Trackers", ...). */
    val description: String,
    val assetPath: String,
    val updateUrl: String,
    val defaultEnabled: Boolean,
)

/** The registry of every filter list Andromeda ships. */
object FilterLists {

    /** Ad/tracker lists feeding the main ad-block engine, individually toggleable. */
    val ADS: List<FilterListDef> = listOf(
        FilterListDef(
            id = "easylist",
            label = "EasyList",
            description = "Ads",
            assetPath = "adblock/easylist.txt",
            updateUrl = "https://easylist.to/easylist/easylist.txt",
            defaultEnabled = true,
        ),
        FilterListDef(
            id = "easyprivacy",
            label = "EasyPrivacy",
            description = "Trackers",
            assetPath = "adblock/easyprivacy.txt",
            updateUrl = "https://easylist.to/easylist/easyprivacy.txt",
            defaultEnabled = true,
        ),
        FilterListDef(
            id = "adguard-mobile",
            label = "AdGuard Mobile",
            description = "Mobile-specific ads",
            assetPath = "adblock/adguard-mobile.txt",
            updateUrl = "https://filters.adtidy.org/extension/ublock/filters/11.txt",
            defaultEnabled = true,
        ),
        FilterListDef(
            id = "peter-lowe",
            label = "Peter Lowe's list",
            description = "Ad/tracking servers",
            assetPath = "adblock/peter-lowe.txt",
            updateUrl = "https://pgl.yoyo.org/adservers/serverlist.php?hostformat=hosts&showintro=0&mimetype=plaintext",
            defaultEnabled = true,
        ),
    )

    /** Cookie-consent annoyance list feeding the separate annoyance engine (D2). */
    val ANNOYANCE: FilterListDef = FilterListDef(
        id = "annoyance-cookies",
        label = "Cookie notices",
        description = "Consent pop-ups",
        assetPath = "adblock/annoyance-cookies.txt",
        updateUrl = "https://secure.fanboy.co.nz/fanboy-cookiemonster.txt",
        defaultEnabled = true,
    )

    /** Ids of the ad lists that are on for a fresh install (currently: all of them). */
    val DEFAULT_ENABLED_IDS: Set<String> =
        ADS.filter { it.defaultEnabled }.map { it.id }.toSet()
}
