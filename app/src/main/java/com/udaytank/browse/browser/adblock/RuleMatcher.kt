package com.udaytank.browse.browser.adblock

/**
 * Everything the matcher needs to know about one network request.
 * [urlLower] is the caller's responsibility (lowercased once per request, not per rule).
 */
data class RequestContext(
    val url: String,
    val urlLower: String,
    val requestHost: String,
    val pageHost: String?,
    val type: ResourceType,
    val thirdParty: Boolean,
)

object RuleMatcher {

    fun matches(rule: NetworkRule, ctx: RequestContext): Boolean {
        // Cheap option gates first; the regex runs only when they all pass.
        if (rule.includeTypes != 0 && (rule.includeTypes and typeBit(ctx.type)) == 0) return false
        if (rule.excludeTypes != 0 && (rule.excludeTypes and typeBit(ctx.type)) != 0) return false
        val party = rule.thirdParty
        if (party != null && ctx.thirdParty != party) return false
        if (rule.excludeDomains.isNotEmpty() && ctx.pageHost != null &&
            DomainChains.matches(ctx.pageHost, rule.excludeDomains)
        ) return false
        if (rule.includeDomains.isNotEmpty()) {
            val pageHost = ctx.pageHost ?: return false
            if (!DomainChains.matches(pageHost, rule.includeDomains)) return false
        }
        return rule.regex.containsMatchIn(ctx.urlLower)
    }

    /** Characters that must be escaped when translating an ABP pattern to a regex. */
    private const val REGEX_SPECIALS = "\\.[]{}()+?$|"

    /**
     * Translates a rule's ABP pattern into a [Regex], once per rule (see [NetworkRule.regex]):
     *   `*` -> `.*`
     *   `^` -> separator: any char outside [\w.%-], or the end of the URL
     *   `||` -> anchored at a hostname boundary (scheme, then optional subdomain labels)
     *   `|` start/end -> `^` / `$`
     * Patterns are lowercased; matching runs against [RequestContext.urlLower].
     */
    internal fun compile(rule: NetworkRule): Regex {
        val sb = StringBuilder(rule.pattern.length + 24)
        when {
            rule.domainAnchor -> sb.append("^[a-z][a-z0-9+.-]*://(?:[^/]*\\.)?")
            rule.startAnchor -> sb.append('^')
        }
        for (c in rule.pattern.lowercase()) {
            when {
                c == '*' -> sb.append(".*")
                c == '^' -> sb.append("(?:[^\\w.%-]|\$)")
                c in REGEX_SPECIALS -> sb.append('\\').append(c)
                else -> sb.append(c)
            }
        }
        if (rule.endAnchor) sb.append('$')
        return Regex(sb.toString())
    }
}
