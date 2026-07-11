package com.udaytank.browse.browser

/**
 * Answers one question for every network request a page makes:
 * should this request be blocked?
 *
 * Thread-safety: shouldBlock is called from WebView's background threads.
 * All state is immutable-after-write behind @Volatile references.
 */
class AdBlockEngine {

    @Volatile
    private var blockList: BlockList = BlockList.EMPTY

    @Volatile
    private var enabled: Boolean = true

    @Volatile
    private var siteAllowlist: Set<String> = emptySet()

    fun load(list: BlockList) {
        blockList = list
    }

    fun updatePolicy(enabled: Boolean, siteAllowlist: Set<String>) {
        this.enabled = enabled
        this.siteAllowlist = siteAllowlist.map { it.lowercase() }.toSet()
    }

    fun shouldBlock(requestHost: String?, pageHost: String?): Boolean {
        if (!enabled) return false
        val host = requestHost?.lowercase() ?: return false
        if (pageHost != null && matchesDomainChain(pageHost.lowercase(), siteAllowlist)) return false
        if (matchesDomainChain(host, blockList.allowedDomains)) return false
        return matchesDomainChain(host, blockList.blockedDomains)
    }

    /**
     * Walks the domain upward label by label:
     * stats.g.doubleclick.net -> g.doubleclick.net -> doubleclick.net
     * so a rule for a domain also covers all its subdomains — but a
     * lookalike suffix (evildoubleclick.net) never matches.
     */
    private fun matchesDomainChain(host: String, domains: Set<String>): Boolean {
        if (domains.isEmpty()) return false
        var current = host
        while (true) {
            if (current in domains) return true
            val dot = current.indexOf('.')
            if (dot == -1) return false
            current = current.substring(dot + 1)
        }
    }
}
