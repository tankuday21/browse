package com.udaytank.browse.browser.adblock

/**
 * Suffix-chain domain matching, shared by the rule matcher ($domain= options), the engine's
 * per-site allowlist, and cosmetic-rule scoping.
 *
 * Walks the host upward label by label:
 *   stats.g.doubleclick.net -> g.doubleclick.net -> doubleclick.net
 * so an entry for a domain also covers all its subdomains — but a lookalike suffix
 * (evildoubleclick.net) never matches.
 */
internal object DomainChains {
    fun matches(host: String, domains: Collection<String>): Boolean {
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

/**
 * eTLD+1 approximation used for third-party detection: the registrable domain is the last two
 * labels, or the last three when the two-label suffix is a known multi-part public suffix
 * (compact table instead of bundling the full Public Suffix List).
 */
object RegistrableDomain {

    private val multiPartSuffixes = setOf(
        "co.uk", "org.uk", "ac.uk", "gov.uk",
        "co.in", "net.in", "org.in",
        "com.au", "net.au", "org.au",
        "co.jp", "ne.jp", "or.jp",
        "com.br", "com.mx", "com.ar",
        "co.za", "com.sg", "com.hk", "com.tw",
        "co.kr", "com.cn", "com.tr", "co.id",
        "com.my", "co.th", "com.vn", "com.ph",
        "com.eg", "com.sa", "co.il", "com.ua", "com.pl",
    )

    /** "stats.g.doubleclick.net" -> "doubleclick.net"; "shop.example.co.uk" -> "example.co.uk". */
    fun of(host: String): String {
        val labels = host.split('.')
        if (labels.size <= 2) return host
        val lastTwo = labels[labels.size - 2] + "." + labels[labels.size - 1]
        return if (lastTwo in multiPartSuffixes) {
            labels[labels.size - 3] + "." + lastTwo
        } else {
            lastTwo
        }
    }

    /**
     * A request is third-party when its registrable domain differs from the page's.
     * An unknown page host is treated as third-party (the conservative direction for
     * `$third-party` blocking rules; `$~third-party` rules simply won't fire).
     */
    fun isThirdParty(requestHost: String, pageHost: String?): Boolean {
        if (pageHost.isNullOrEmpty()) return true
        return of(requestHost) != of(pageHost)
    }
}
