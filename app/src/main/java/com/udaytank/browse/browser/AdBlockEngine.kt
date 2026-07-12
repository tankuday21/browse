package com.udaytank.browse.browser

import com.udaytank.browse.browser.adblock.AdBlockCore
import com.udaytank.browse.browser.adblock.DomainChains
import com.udaytank.browse.browser.adblock.ParsedList
import com.udaytank.browse.browser.adblock.RegistrableDomain
import com.udaytank.browse.browser.adblock.RequestContext
import com.udaytank.browse.browser.adblock.ResourceType

/**
 * Answers one question for every network request a page makes:
 * should this request be blocked?
 *
 * v2: wraps the token-indexed [AdBlockCore] (full ABP syntax) while keeping the v1 surface
 * that WebViewHolder/MainActivity already use.
 *
 * Thread-safety: shouldBlock is called from WebView's background threads.
 * All state is immutable-after-write behind @Volatile references.
 */
class AdBlockEngine {

    @Volatile
    private var core: AdBlockCore = AdBlockCore.EMPTY

    @Volatile
    private var enabled: Boolean = true

    @Volatile
    private var siteAllowlist: Set<String> = emptySet()

    fun load(list: ParsedList) = load(listOf(list))

    /** Builds a fresh immutable core from all lists and swaps it in atomically. */
    fun load(lists: List<ParsedList>) {
        core = AdBlockCore(lists)
    }

    fun updatePolicy(enabled: Boolean, siteAllowlist: Set<String>) {
        this.enabled = enabled
        this.siteAllowlist = siteAllowlist.map { it.lowercase() }.toSet()
    }

    /**
     * v1-compatible overload: host-only decision (no URL/type information). Kept so existing
     * call sites work unchanged; new code should prefer the full-context overload.
     */
    fun shouldBlock(requestHost: String?, pageHost: String?): Boolean {
        val host = requestHost?.lowercase() ?: return false
        return shouldBlock(
            url = "https://$host/",
            requestHost = host,
            pageHost = pageHost,
            type = ResourceType.OTHER,
            mainFrame = false,
        )
    }

    /** Full-context decision. Main-frame documents are never blocked (deliberate UX choice). */
    fun shouldBlock(
        url: String,
        requestHost: String?,
        pageHost: String?,
        type: ResourceType,
        mainFrame: Boolean,
    ): Boolean {
        if (!enabled || mainFrame) return false
        val host = requestHost?.lowercase() ?: return false
        val page = pageHost?.lowercase()
        if (page != null && DomainChains.matches(page, siteAllowlist)) return false
        val ctx = RequestContext(
            url = url,
            urlLower = url.lowercase(),
            requestHost = host,
            pageHost = page,
            type = type,
            thirdParty = RegistrableDomain.isThirdParty(host, page),
        )
        return core.decide(ctx)
    }

    /** JS that injects a <style> hiding cosmetic-filter elements; empty when disabled. */
    fun cosmeticInjectionScript(pageHost: String?): String {
        if (!enabled) return ""
        val page = pageHost?.lowercase()
        if (page != null && DomainChains.matches(page, siteAllowlist)) return ""
        val css = core.cssFor(page ?: "")
        if (css.isEmpty()) return ""
        val escaped = css.replace("\\", "\\\\").replace("'", "\\'")
        return "(function(){var s=document.createElement('style');" +
            "s.textContent='$escaped';document.documentElement.appendChild(s);})();"
    }
}
