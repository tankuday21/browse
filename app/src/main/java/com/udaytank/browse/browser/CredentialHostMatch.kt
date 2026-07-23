package com.udaytank.browse.browser

/**
 * Decides whether a saved credential's host may be offered on another host (v6.5, cross-subdomain
 * fill). The rule is registrable-domain (eTLD+1) EQUALITY, never suffix containment — see the
 * security notes on [sameSite].
 *
 * The public-suffix set is a curated subset of the Mozilla PSL: the common multi-label ccTLDs and
 * the widely-used site-hosting suffixes (where each subdomain is a different owner, so they must
 * NOT collapse to one registrable domain). A suffix we don't carry can cause an over-match, but
 * fill is HTTPS-only and user-tap-initiated, so an over-match only surfaces an extra ignorable
 * suggestion — it can never inject a password on its own.
 */
object CredentialHostMatch {

    /**
     * Multi-label public suffixes. Single-label TLDs (`com`, `org`, `io`, `dev`, …) are implicit —
     * anything not matched here falls back to "last label is the TLD". Kept lowercase.
     */
    private val PUBLIC_SUFFIXES: Set<String> = setOf(
        // Common multi-label ccTLD registration points.
        "co.uk", "org.uk", "me.uk", "ltd.uk", "plc.uk", "net.uk", "sch.uk", "gov.uk", "ac.uk",
        "com.au", "net.au", "org.au", "edu.au", "gov.au", "id.au",
        "co.nz", "net.nz", "org.nz", "govt.nz", "ac.nz",
        "co.jp", "or.jp", "ne.jp", "ac.jp", "go.jp",
        "co.kr", "or.kr", "ne.kr", "go.kr", "re.kr",
        "co.in", "net.in", "org.in", "firm.in", "gen.in", "ind.in", "gov.in", "ac.in",
        "com.br", "net.br", "org.br", "gov.br", "edu.br",
        "com.cn", "net.cn", "org.cn", "gov.cn", "edu.cn", "ac.cn",
        "com.hk", "net.hk", "org.hk", "edu.hk", "gov.hk",
        "com.tw", "net.tw", "org.tw", "gov.tw", "edu.tw",
        "com.sg", "net.sg", "org.sg", "edu.sg", "gov.sg",
        "com.mx", "net.mx", "org.mx", "gob.mx", "edu.mx",
        "com.tr", "net.tr", "org.tr", "gov.tr", "edu.tr",
        "com.ar", "net.ar", "org.ar", "gob.ar", "edu.ar",
        "co.za", "org.za", "net.za", "gov.za", "ac.za",
        "co.il", "org.il", "net.il", "gov.il", "ac.il",
        "com.my", "net.my", "org.my", "gov.my", "edu.my",
        "com.ph", "net.ph", "org.ph", "gov.ph", "edu.ph",
        "com.pk", "net.pk", "org.pk", "gov.pk", "edu.pk",
        "com.vn", "net.vn", "org.vn", "gov.vn", "edu.vn",
        "com.sa", "net.sa", "org.sa", "gov.sa", "edu.sa",
        "com.eg", "net.eg", "org.eg", "gov.eg", "edu.eg",
        "com.ua", "net.ua", "org.ua",
        "com.pl", "net.pl", "org.pl", "gov.pl", "edu.pl",
        "com.ru", "net.ru", "org.ru",
        "co.id", "or.id", "ac.id", "go.id", "net.id", "web.id",
        "co.th", "in.th", "ac.th", "go.th", "net.th", "or.th",
        // Widely-used site-hosting suffixes — each label below is a distinct owner, so they must
        // reduce to distinct registrable domains (do NOT let a.github.io match b.github.io).
        "github.io", "gitlab.io", "githubusercontent.com",
        "blogspot.com", "wordpress.com", "tumblr.com",
        "appspot.com", "web.app", "firebaseapp.com", "cloudfunctions.net",
        "herokuapp.com", "herokussl.com",
        "pages.dev", "workers.dev",
        "vercel.app", "netlify.app", "netlify.com",
        "azurewebsites.net", "cloudapp.net",
        "amazonaws.com", "s3.amazonaws.com", "elasticbeanstalk.com",
        "surge.sh", "now.sh", "glitch.me", "repl.co",
    )

    /**
     * The lowercase registrable domain (eTLD+1) of [host], or null when there is no such concept:
     * blank input, an IP literal (v4 or bracketed v6), or too few labels to sit above the public
     * suffix.
     */
    fun registrableDomain(host: String?): String? {
        val h = host?.trim()?.trimEnd('.')?.lowercase()
        if (h.isNullOrBlank()) return null
        if (isIpLiteral(h)) return null

        val labels = h.split('.')
        if (labels.size < 2 || labels.any { it.isEmpty() }) return null

        // Longest trailing multi-label public suffix wins (e.g. prefer "co.uk" over "uk"). Start
        // at labels.size so a host that IS a bare public suffix ("co.uk") is caught and rejected.
        for (suffixLen in minOf(labels.size, MAX_SUFFIX_LABELS) downTo 2) {
            val candidate = labels.subList(labels.size - suffixLen, labels.size).joinToString(".")
            if (candidate in PUBLIC_SUFFIXES) {
                // Need at least one label in front of the suffix to form a registrable domain.
                if (labels.size <= suffixLen) return null
                return labels.subList(labels.size - suffixLen - 1, labels.size).joinToString(".")
            }
        }
        // Default: single-label TLD → registrable domain is the last two labels.
        return labels.subList(labels.size - 2, labels.size).joinToString(".")
    }

    /**
     * True iff [a] and [b] reduce to the SAME registrable domain. This is equality of the computed
     * eTLD+1, never a suffix test — so `example.com` matches `login.example.com` but not
     * `example.com.evil.net` (whose registrable domain is `evil.net`).
     */
    fun sameSite(a: String?, b: String?): Boolean {
        val ra = registrableDomain(a) ?: return false
        val rb = registrableDomain(b) ?: return false
        return ra == rb
    }

    /**
     * Filters [credentialHosts] to those that fill on [pageHost] (same registrable domain) and
     * ranks them: an exact host match first, then the rest in their original order (stable).
     */
    fun rankHosts(pageHost: String, credentialHosts: List<String>): List<String> {
        val page = pageHost.trim().trimEnd('.').lowercase()
        val matches = credentialHosts.filter { sameSite(page, it) }
        val (exact, others) = matches.partition { it.trim().trimEnd('.').lowercase() == page }
        return exact + others
    }

    private const val MAX_SUFFIX_LABELS = 3

    private fun isIpLiteral(host: String): Boolean {
        if (host.startsWith("[") || host.contains(':')) return true // bracketed / raw IPv6
        val parts = host.split('.')
        return parts.size == 4 && parts.all { p -> p.toIntOrNull()?.let { it in 0..255 } == true }
    }
}
