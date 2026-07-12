package com.udaytank.browse.browser.adblock

/** Resource types a network rule can target via `$script`, `$image`, ... options. */
enum class ResourceType { SCRIPT, IMAGE, STYLESHEET, XHR, SUBDOCUMENT, FONT, MEDIA, DOCUMENT, OTHER }

internal fun typeBit(type: ResourceType): Int = 1 shl type.ordinal

/**
 * One parsed Adblock-Plus network rule (blocking or `@@` exception).
 *
 * [includeTypes]/[excludeTypes] are bitmasks over [ResourceType.ordinal]; 0 means "don't care".
 * [thirdParty] null means the rule applies regardless of party-ness.
 *
 * Not a data class on purpose: rules are identity objects held in indexes, and the compiled
 * [regex] must never take part in equality.
 */
class NetworkRule(
    /** Original pattern part (anchors stripped, no options). */
    val pattern: String,
    val isException: Boolean,
    /** `||` — anchor at a hostname boundary. */
    val domainAnchor: Boolean,
    /** Leading `|` — anchor at the very start of the URL. */
    val startAnchor: Boolean,
    /** Trailing `|` — anchor at the very end of the URL. */
    val endAnchor: Boolean,
    /** From `$domain=` — page hosts the rule is limited to / excluded from. */
    val includeDomains: List<String> = emptyList(),
    val excludeDomains: List<String> = emptyList(),
    /** From `$third-party` / `$~third-party`; null = don't care. */
    val thirdParty: Boolean? = null,
    val includeTypes: Int = 0,
    val excludeTypes: Int = 0,
) {
    /**
     * Compiled lazily (thread-safe by default `lazy` semantics) so that parsing 100k rules
     * costs no regex compilation; only rules that actually become match candidates pay it,
     * exactly once.
     */
    internal val regex: Regex by lazy { RuleMatcher.compile(this) }
}

/** One element-hiding rule: `##sel`, `domains##sel`, or a `#@#` exception. */
data class CosmeticRule(
    val selector: String,
    val includeDomains: List<String>,
    val excludeDomains: List<String>,
    val isException: Boolean,
)

data class ParsedList(
    val network: List<NetworkRule>,
    val cosmetic: List<CosmeticRule>,
)
