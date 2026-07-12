package com.udaytank.browse.browser.adblock

/**
 * Full Adblock-Plus-syntax parser (network + cosmetic rules), including hosts-format lines.
 *
 * Design rule: when in doubt, SKIP. A skipped rule means one ad slips through; a misparsed
 * rule can break a page. Every unsupported or unknown `$option` therefore drops the whole rule.
 */
object AbpParser {

    /**
     * A `$` tail only counts as an options list when it looks like one — this keeps a literal
     * `$` inside a URL path (e.g. `||ex.com/api$/price`) from being eaten as options.
     */
    private val OPTIONS_TAIL = Regex("^[\\w~,=|.*-]+$")

    /**
     * Unsupported options whose VALUES contain characters the guard above rejects
     * (`$replace=/x/y/`, `$csp=script-src 'self'`, ...). The tail then fails the guard, but the
     * line is still an options rule we can't enforce — drop it instead of keeping a `$` pattern.
     */
    private val UNSUPPORTED_VALUED_OPTION =
        Regex("^(?:replace|csp|header|removeparam|redirect(?:-rule)?|denyallow|method|to|from)=")

    private val HOSTS_IPS = setOf("0.0.0.0", "127.0.0.1", "::1")

    private val HOSTS_SKIP = setOf(
        "localhost", "localhost.localdomain", "local", "broadcasthost",
        "ip6-localhost", "ip6-loopback", "ip6-localnet", "ip6-mcastprefix",
        "ip6-allnodes", "ip6-allrouters", "ip6-allhosts", "0.0.0.0",
    )

    private val TYPE_NAMES = mapOf(
        "script" to ResourceType.SCRIPT,
        "image" to ResourceType.IMAGE,
        "stylesheet" to ResourceType.STYLESHEET,
        "css" to ResourceType.STYLESHEET,
        "xmlhttprequest" to ResourceType.XHR,
        "xhr" to ResourceType.XHR,
        "subdocument" to ResourceType.SUBDOCUMENT,
        "frame" to ResourceType.SUBDOCUMENT,
        "font" to ResourceType.FONT,
        "media" to ResourceType.MEDIA,
        "object" to ResourceType.OTHER,
        "other" to ResourceType.OTHER,
        "document" to ResourceType.DOCUMENT,
        "doc" to ResourceType.DOCUMENT,
    )

    fun parse(text: String): ParsedList {
        val network = ArrayList<NetworkRule>(60_000)
        val cosmetic = ArrayList<CosmeticRule>(30_000)
        text.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("!") || line.startsWith("[")) return@forEach
            if (parseHostsLine(line, network)) return@forEach
            // Procedural / snippet / scriptlet cosmetic markers we can't enforce in a WebView.
            if (line.contains("#?#") || line.contains("#\$#") || line.contains("#%#")) return@forEach
            val exceptionIdx = line.indexOf("#@#")
            val hideIdx = line.indexOf("##")
            if (exceptionIdx >= 0) {
                parseCosmetic(line.substring(0, exceptionIdx), line.substring(exceptionIdx + 3), true)
                    ?.let(cosmetic::add)
            } else if (hideIdx >= 0) {
                parseCosmetic(line.substring(0, hideIdx), line.substring(hideIdx + 2), false)
                    ?.let(cosmetic::add)
            } else {
                parseNetwork(line)?.let(network::add)
            }
        }
        return ParsedList(network, cosmetic)
    }

    // ---------------------------------------------------------------- hosts format

    /** Returns true when the line was a hosts-format line (even if its host was skipped). */
    private fun parseHostsLine(line: String, out: MutableList<NetworkRule>): Boolean {
        val sep = line.indexOfFirst { it == ' ' || it == '\t' }
        if (sep <= 0) return false
        if (line.substring(0, sep) !in HOSTS_IPS) return false
        // Hosts files allow trailing "# comment" and multiple hosts on one line.
        val rest = line.substring(sep + 1).substringBefore('#').trim()
        for (candidate in rest.split(' ', '\t')) {
            val host = candidate.trim().lowercase()
            if (host.isEmpty() || host in HOSTS_SKIP) continue
            if (!host.contains('.')) continue
            if (host.any { !(it.isLetterOrDigit() || it == '.' || it == '-') }) continue
            // Equivalent of "||host^" — the trailing separator keeps "ads.com" from
            // matching "ads.community.net".
            out.add(
                NetworkRule(
                    pattern = "$host^",
                    isException = false,
                    domainAnchor = true,
                    startAnchor = false,
                    endAnchor = false,
                )
            )
        }
        return true
    }

    // ---------------------------------------------------------------- cosmetic rules

    private fun parseCosmetic(domainsPart: String, selectorRaw: String, isException: Boolean): CosmeticRule? {
        val selector = selectorRaw.trim()
        if (!isSafeSelector(selector)) return null
        val include = ArrayList<String>(2)
        val exclude = ArrayList<String>(1)
        val domains = domainsPart.trim()
        if (domains.isNotEmpty()) {
            for (entry in domains.split(',')) {
                val d = entry.trim().lowercase()
                if (d.isEmpty()) return null
                val negated = d.startsWith("~")
                val name = if (negated) d.substring(1) else d
                // Wildcard domains (example.*) can't be chain-matched; dropping only the
                // entry could widen the rule's scope, so drop the whole rule.
                if (name.isEmpty() || name.contains('*')) return null
                if (name.any { !(it.isLetterOrDigit() || it == '.' || it == '-') }) return null
                if (negated) exclude.add(name) else include.add(name)
            }
        }
        return CosmeticRule(selector, include, exclude, isException)
    }

    /** Accept plain class/id/attribute selectors; reject procedural/scriptlet ones. */
    private fun isSafeSelector(selector: String): Boolean {
        if (selector.isEmpty() || selector.length > 250) return false
        if (selector.startsWith("+js")) return false // uBlock scriptlet injection
        if (selector.contains(":style") || selector.contains(":-abp") ||
            selector.contains(":has") || selector.contains(":xpath") ||
            selector.contains(":matches") || selector.contains(":upward") ||
            selector.contains(":remove") ||
            selector.contains("{") || selector.contains("}")
        ) return false
        return true
    }

    // ---------------------------------------------------------------- network rules

    private fun parseNetwork(fullLine: String): NetworkRule? {
        var line = fullLine
        val isException = line.startsWith("@@")
        if (isException) line = line.substring(2)
        if (line.isEmpty()) return null

        // Split "$options" on the LAST unescaped $ whose tail looks like an options list.
        var body = line
        var optionsPart: String? = null
        val dollar = lastUnescapedDollar(line)
        if (dollar > 0 && dollar < line.length - 1) {
            val tail = line.substring(dollar + 1)
            if (OPTIONS_TAIL.matches(tail)) {
                body = line.substring(0, dollar)
                optionsPart = tail
            } else if (UNSUPPORTED_VALUED_OPTION.containsMatchIn(tail)) {
                return null
            }
        }

        // Regex rules (/.../): unsupported, skip.
        if (body.length >= 2 && body.startsWith("/") && body.endsWith("/")) return null

        var domainAnchor = false
        var startAnchor = false
        var endAnchor = false
        if (body.startsWith("||")) {
            domainAnchor = true
            body = body.substring(2)
        } else if (body.startsWith("|")) {
            startAnchor = true
            body = body.substring(1)
        }
        if (body.endsWith("|")) {
            endAnchor = true
            body = body.dropLast(1)
        }
        body = body.replace("\\$", "$") // un-escape literal dollars kept in the pattern

        var thirdParty: Boolean? = null
        var includeTypes = 0
        var excludeTypes = 0
        var includeDomains: List<String> = emptyList()
        var excludeDomains: List<String> = emptyList()

        if (optionsPart != null) {
            for (optRaw in optionsPart.split(',')) {
                val opt = optRaw.trim()
                when {
                    opt.isEmpty() -> Unit
                    opt.startsWith("domain=") -> {
                        val inc = ArrayList<String>(2)
                        val exc = ArrayList<String>(1)
                        for (entry in opt.substring(7).split('|')) {
                            val d = entry.trim().lowercase()
                            if (d.isEmpty()) return null
                            val negated = d.startsWith("~")
                            val name = if (negated) d.substring(1) else d
                            if (name.isEmpty() || name.contains('*')) return null
                            if (negated) exc.add(name) else inc.add(name)
                        }
                        includeDomains = inc
                        excludeDomains = exc
                    }
                    opt == "third-party" || opt == "3p" || opt == "~first-party" || opt == "~1p" ->
                        thirdParty = true
                    opt == "~third-party" || opt == "~3p" || opt == "first-party" || opt == "1p" ->
                        thirdParty = false
                    opt == "match-case" -> Unit // we match case-insensitively; ignore
                    opt == "important" -> Unit // no exception-override tiering; treat as normal
                    opt == "badfilter" -> return null
                    else -> {
                        val negated = opt.startsWith("~")
                        val name = if (negated) opt.substring(1) else opt
                        // Everything unknown/unenforceable lands here and drops the rule:
                        // popup, csp=, redirect=, redirect-rule=, removeparam, replace=,
                        // header=, denyallow=, method=, to=, from=, websocket, ping, ...
                        val type = TYPE_NAMES[name] ?: return null
                        if (negated) excludeTypes = excludeTypes or typeBit(type)
                        else includeTypes = includeTypes or typeBit(type)
                    }
                }
            }
        }

        // We never block main-frame documents. A pure $document BLOCK rule is therefore
        // meaningless for us — but @@$document exceptions become page-allowlist rules.
        if (!isException && includeTypes == typeBit(ResourceType.DOCUMENT) && excludeTypes == 0) return null

        // A pattern with no literal text ("", "*", "**") matches every URL. Only keep such a
        // rule when it is meaningfully constrained; otherwise it would nuke the whole web.
        val hasLiteralText = body.any { it != '*' }
        if (!hasLiteralText && includeTypes == 0 && includeDomains.isEmpty()) return null

        return NetworkRule(
            pattern = body,
            isException = isException,
            domainAnchor = domainAnchor,
            startAnchor = startAnchor,
            endAnchor = endAnchor,
            includeDomains = includeDomains,
            excludeDomains = excludeDomains,
            thirdParty = thirdParty,
            includeTypes = includeTypes,
            excludeTypes = excludeTypes,
        )
    }

    private fun lastUnescapedDollar(line: String): Int {
        for (i in line.length - 1 downTo 0) {
            if (line[i] == '$' && (i == 0 || line[i - 1] != '\\')) return i
        }
        return -1
    }
}
