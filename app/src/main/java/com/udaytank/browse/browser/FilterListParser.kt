package com.udaytank.browse.browser

data class BlockList(
    val blockedDomains: Set<String>,
    val allowedDomains: Set<String>,
    /** Generic element-hiding CSS selectors (from `##selector` rules). */
    val cosmeticSelectors: Set<String> = emptySet(),
) {
    companion object {
        val EMPTY = BlockList(emptySet(), emptySet(), emptySet())
    }
}

/**
 * Parses the domain-anchor subset of Adblock Plus filter syntax:
 *   ||domain.com^     block requests to domain.com and subdomains
 *   @@||domain.com^   exception (never block)
 * Comments (!), headers ([...]), cosmetic rules (##), and rules with
 * paths/wildcards/options ($) are ignored in v1.
 */
object FilterListParser {

    fun parse(text: String): BlockList {
        val blocked = HashSet<String>(60_000)
        val allowed = HashSet<String>(2_000)
        val cosmetic = HashSet<String>(20_000)
        text.lineSequence().forEach { raw ->
            val line = raw.trim()
            when {
                line.isEmpty() || line.startsWith("!") || line.startsWith("[") -> Unit
                // Generic cosmetic rule "##selector" (no domain prefix). Domain-
                // specific (example.com##...) and exceptions (#@#) are skipped in v1.
                line.startsWith("##") -> {
                    val selector = line.substring(2).trim()
                    if (isSafeSelector(selector)) cosmetic.add(selector)
                }
                line.contains("##") || line.contains("#@#") || line.contains("#?#") -> Unit
                line.startsWith("@@||") -> extractDomain(line.substring(4))?.let(allowed::add)
                line.startsWith("||") -> extractDomain(line.substring(2))?.let(blocked::add)
                else -> Unit
            }
        }
        return BlockList(blocked, allowed, cosmetic)
    }

    /** Accept plain class/id/attribute selectors; skip procedural (:has, :xpath) ones. */
    private fun isSafeSelector(selector: String): Boolean {
        if (selector.isEmpty() || selector.length > 200) return false
        if (selector.contains(":style") || selector.contains(":-abp") ||
            selector.contains(":has") || selector.contains(":xpath") ||
            selector.contains(":matches") || selector.contains("{")
        ) return false
        return true
    }

    /** "doubleclick.net^" -> "doubleclick.net"; anything beyond a bare domain -> null. */
    private fun extractDomain(rest: String): String? {
        val caret = rest.indexOf('^')
        val candidate: String
        if (caret == -1) {
            candidate = rest
        } else {
            if (caret != rest.length - 1) return null // path/options after ^ need full matching
            candidate = rest.substring(0, caret)
        }
        if (candidate.isEmpty() || !candidate.contains('.')) return null
        if (candidate.any { !(it.isLetterOrDigit() || it == '.' || it == '-') }) return null
        return candidate.lowercase()
    }
}
