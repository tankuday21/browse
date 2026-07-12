package com.udaytank.browse.browser.adblock

/**
 * Immutable matching core built from one or more parsed filter lists. The engine swaps whole
 * cores behind a @Volatile reference, so everything in here is safe to read from any thread;
 * the only mutable state (the CSS cache) is synchronized internally.
 */
class AdBlockCore(lists: List<ParsedList>) {

    private val blockIndex: TokenIndex
    private val exceptionIndex: TokenIndex

    /** `@@...$document` rules — matching one against a page host allowlists that whole page. */
    private val documentExceptions: List<NetworkRule>

    /** Selectors from `##sel` rules with no domain scoping at all. */
    private val genericSelectors: List<String>

    /** Generic rules that carry only exclusions (`~example.com##.ad`). */
    private val genericWithExcludes: List<CosmeticRule>

    /** Domain-scoped hiding rules, filed under each of their include domains. */
    private val specificByDomain: Map<String, List<CosmeticRule>>

    /** Domain-scoped `#@#` exceptions, filed under each of their include domains. */
    private val exceptionByDomain: Map<String, List<CosmeticRule>>

    /** Selectors un-hidden everywhere by a generic `#@#` exception. */
    private val genericExceptionSelectors: Set<String>

    private val cssCache = object : LinkedHashMap<String, String>(CSS_CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>): Boolean =
            size > CSS_CACHE_SIZE
    }

    init {
        val blocks = ArrayList<NetworkRule>()
        val exceptions = ArrayList<NetworkRule>()
        val docExceptions = ArrayList<NetworkRule>()
        val generic = LinkedHashSet<String>()
        val genericExcludes = ArrayList<CosmeticRule>()
        val specific = HashMap<String, MutableList<CosmeticRule>>()
        val cosmeticExceptions = HashMap<String, MutableList<CosmeticRule>>()
        val genericExceptions = HashSet<String>()

        for (list in lists) {
            for (rule in list.network) {
                if (rule.isException) {
                    exceptions.add(rule)
                    if (rule.includeTypes and typeBit(ResourceType.DOCUMENT) != 0) {
                        docExceptions.add(rule)
                    }
                } else {
                    blocks.add(rule)
                }
            }
            for (rule in list.cosmetic) {
                when {
                    rule.isException && rule.includeDomains.isEmpty() ->
                        genericExceptions.add(rule.selector)
                    rule.isException ->
                        rule.includeDomains.forEach {
                            cosmeticExceptions.getOrPut(it) { ArrayList(2) }.add(rule)
                        }
                    rule.includeDomains.isEmpty() && rule.excludeDomains.isEmpty() ->
                        generic.add(rule.selector)
                    rule.includeDomains.isEmpty() ->
                        genericExcludes.add(rule)
                    else ->
                        rule.includeDomains.forEach {
                            specific.getOrPut(it) { ArrayList(2) }.add(rule)
                        }
                }
            }
        }

        blockIndex = TokenIndex(blocks)
        exceptionIndex = TokenIndex(exceptions)
        documentExceptions = docExceptions
        genericSelectors = generic.toList()
        genericWithExcludes = genericExcludes
        specificByDomain = specific
        exceptionByDomain = cosmeticExceptions
        genericExceptionSelectors = genericExceptions
    }

    /** True when the request should be blocked. Exceptions always win over blocks. */
    fun decide(ctx: RequestContext): Boolean {
        val tokens = TokenIndex.tokensOf(ctx.urlLower)
        blockIndex.findMatch(ctx, tokens) ?: return false
        if (exceptionIndex.findMatch(ctx, tokens) != null) return false
        if (ctx.pageHost != null && isPageAllowlisted(ctx.pageHost)) return false
        return true
    }

    /** Does an `@@...$document` exception cover this page host? */
    fun isPageAllowlisted(pageHost: String): Boolean {
        if (documentExceptions.isEmpty()) return false
        val syntheticUrl = "https://$pageHost/"
        val ctx = RequestContext(
            url = syntheticUrl,
            urlLower = syntheticUrl,
            requestHost = pageHost,
            pageHost = pageHost,
            type = ResourceType.DOCUMENT,
            thirdParty = false,
        )
        return documentExceptions.any { RuleMatcher.matches(it, ctx) }
    }

    /**
     * The element-hiding CSS for one page host: generic selectors plus host-matching specific
     * ones, minus generic and host-matching exceptions. Selectors are chunked so no single CSS
     * rule carries an absurd selector list. LRU-cached per host.
     */
    fun cssFor(host: String): String {
        val key = host.lowercase()
        synchronized(cssCache) { cssCache[key]?.let { return it } }
        val css = buildCss(key)
        synchronized(cssCache) { cssCache[key] = css }
        return css
    }

    private fun buildCss(host: String): String {
        if (host.isNotEmpty() && isPageAllowlisted(host)) return ""

        val selectors = LinkedHashSet<String>(genericSelectors.size + 64)
        selectors.addAll(genericSelectors)
        for (rule in genericWithExcludes) {
            if (!DomainChains.matches(host, rule.excludeDomains)) selectors.add(rule.selector)
        }
        if (host.isNotEmpty()) {
            // Gather all host-specific selectors first, exceptions second — an exception
            // found at one chain level must beat an include found at another.
            val excepted = HashSet<String>()
            var current = host
            while (true) {
                specificByDomain[current]?.forEach { rule ->
                    if (!DomainChains.matches(host, rule.excludeDomains)) selectors.add(rule.selector)
                }
                exceptionByDomain[current]?.forEach { rule ->
                    if (!DomainChains.matches(host, rule.excludeDomains)) excepted.add(rule.selector)
                }
                val dot = current.indexOf('.')
                if (dot == -1) break
                current = current.substring(dot + 1)
            }
            selectors.removeAll(excepted)
        }
        selectors.removeAll(genericExceptionSelectors)
        if (selectors.isEmpty()) return ""
        return selectors.chunked(SELECTORS_PER_RULE)
            .joinToString("") { chunk -> chunk.joinToString(",") + "{display:none!important;}" }
    }

    companion object {
        private const val CSS_CACHE_SIZE = 16
        private const val SELECTORS_PER_RULE = 500

        val EMPTY = AdBlockCore(emptyList())
    }
}
