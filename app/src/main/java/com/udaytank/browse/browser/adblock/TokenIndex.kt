package com.udaytank.browse.browser.adblock

/**
 * Token index over network rules (the uBlock trick): instead of scanning every rule for every
 * request, each rule is filed under one literal token from its pattern, and a lookup only
 * examines the rules filed under tokens actually present in the request URL.
 *
 * A token is a run of [a-z0-9%] of length >= [MIN_TOKEN_LENGTH]. A rule token is only usable
 * when the pattern guarantees the URL contains it as a COMPLETE token (bounded by separators
 * or anchors on both sides) — otherwise a rule like "banner" would be missed for the URL
 * ".../megabanner.js". Rules with no usable token go to a small [tailRules] list that is
 * always scanned.
 *
 * "Rarest-ish token pragmatically" = the longest usable token.
 */
class TokenIndex(rules: List<NetworkRule>) {

    private val byToken = HashMap<String, MutableList<NetworkRule>>()
    private val tail = ArrayList<NetworkRule>()

    val tailRules: List<NetworkRule> get() = tail
    val indexedTokenCount: Int get() = byToken.size

    init {
        for (rule in rules) {
            val token = bestToken(rule)
            if (token == null) tail.add(rule) else byToken.getOrPut(token) { ArrayList(2) }.add(rule)
        }
    }

    /** All rules that could possibly match a URL containing [tokens] (candidates, not matches). */
    fun candidates(tokens: List<String>): List<NetworkRule> {
        val out = ArrayList<NetworkRule>(tail.size + 8)
        out.addAll(tail)
        for (token in tokens) byToken[token]?.let(out::addAll)
        return out
    }

    fun candidates(urlLower: String): List<NetworkRule> = candidates(tokensOf(urlLower))

    /** First rule that actually matches [ctx]; [tokens] must be [tokensOf] ctx.urlLower. */
    fun findMatch(ctx: RequestContext, tokens: List<String>): NetworkRule? {
        for (token in tokens) {
            val bucket = byToken[token] ?: continue
            for (rule in bucket) if (RuleMatcher.matches(rule, ctx)) return rule
        }
        for (rule in tail) if (RuleMatcher.matches(rule, ctx)) return rule
        return null
    }

    companion object {
        private const val MIN_TOKEN_LENGTH = 4

        /** URLs (data: URIs...) can carry hundreds of tokens; matching cost stays bounded. */
        private const val MAX_URL_TOKENS = 64

        private fun isTokenChar(c: Char): Boolean =
            c in 'a'..'z' || c in '0'..'9' || c == '%'

        /** Distinct tokens of a LOWERCASE string, in order of appearance. */
        fun tokensOf(lower: String): List<String> {
            val seen = LinkedHashSet<String>(16)
            var start = -1
            for (i in lower.indices) {
                if (isTokenChar(lower[i])) {
                    if (start < 0) start = i
                } else if (start >= 0) {
                    if (i - start >= MIN_TOKEN_LENGTH) seen.add(lower.substring(start, i))
                    start = -1
                    if (seen.size >= MAX_URL_TOKENS) return seen.toList()
                }
            }
            if (start >= 0 && lower.length - start >= MIN_TOKEN_LENGTH) {
                seen.add(lower.substring(start))
            }
            return seen.toList()
        }

        /**
         * Longest token of the pattern that is guaranteed to appear as a complete URL token:
         * its left edge must sit on an anchor or a literal separator (anything but `*`), and
         * its right edge on a literal separator, `^`, or an end anchor.
         */
        internal fun bestToken(rule: NetworkRule): String? {
            val p = rule.pattern.lowercase()
            var best: String? = null
            var start = -1
            for (i in 0..p.length) {
                val isToken = i < p.length && isTokenChar(p[i])
                if (isToken) {
                    if (start < 0) start = i
                    continue
                }
                if (start >= 0) {
                    val leftOk = if (start == 0) rule.domainAnchor || rule.startAnchor
                    else p[start - 1] != '*'
                    val rightOk = if (i == p.length) rule.endAnchor else p[i] != '*'
                    val length = i - start
                    if (leftOk && rightOk && length >= MIN_TOKEN_LENGTH &&
                        length > (best?.length ?: 0)
                    ) {
                        best = p.substring(start, i)
                    }
                    start = -1
                }
            }
            return best
        }
    }
}
