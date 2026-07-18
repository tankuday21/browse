package com.udaytank.browse.browser

import android.content.Intent

/**
 * Hardens a page-supplied intent:// URI before it may launch (v4.9). A web page fully controls
 * the parsed Intent's action, data, package, extras, and flags, so every field is constrained:
 *
 * - data scheme re-validated against [ExternalLinks.unsafeSchemes] (and the selector's) — the
 *   navigation-level block on file/content/javascript must not be bypassable by smuggling the
 *   scheme inside `#Intent;scheme=…;end`;
 * - action forced to ACTION_VIEW — the same never-auto-dial/never-call guarantee the plain
 *   external-scheme path has (a page could otherwise set ACTION_CALL);
 * - component nulled (including the selector's) + CATEGORY_BROWSABLE required — a page must
 *   never target non-exported/internal activities, ours or anyone's;
 * - flags replaced wholesale with NEW_TASK — strips smuggled FLAG_GRANT_*_URI_PERMISSION and
 *   launch/identity flags;
 * - extras cleared — confused-deputy hygiene: legitimate scheme handlers resolve on the URI,
 *   not on extras forwarded by a browser (caller reads browser_fallback_url BEFORE hardening);
 * - our own package de-targeted — a page must not force-loop the browser into itself.
 *
 * Kept free of Context/WebView so instrumented tests can drive it with hostile URIs directly.
 */
object IntentHardening {

    /** Returns the hardened intent, or null when it must not launch at all. */
    fun harden(parsed: Intent, selfPackage: String): Intent? {
        if (parsed.scheme?.lowercase() in ExternalLinks.unsafeSchemes) return null
        if (parsed.selector?.scheme?.lowercase() in ExternalLinks.unsafeSchemes) return null
        parsed.action = Intent.ACTION_VIEW
        parsed.component = null
        parsed.addCategory(Intent.CATEGORY_BROWSABLE)
        parsed.selector?.let { sel ->
            sel.component = null
            sel.addCategory(Intent.CATEGORY_BROWSABLE)
        }
        parsed.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        parsed.replaceExtras(null as android.os.Bundle?)
        if (parsed.`package` == selfPackage) parsed.`package` = null
        return parsed
    }
}
