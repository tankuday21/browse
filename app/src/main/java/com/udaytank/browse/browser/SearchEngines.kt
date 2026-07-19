package com.udaytank.browse.browser

import com.udaytank.browse.data.SearchEngine
import org.json.JSONArray
import org.json.JSONObject

/** A user-defined engine (v5.8): display name + query template with `%s` for the terms. */
data class CustomSearchEngine(val name: String, val template: String)

/**
 * What the address bar actually searches with — a built-in or a custom, resolved.
 * [suggestUrl] is null for custom engines: their suggest endpoint is unknown, and guessing
 * (or falling back to Google) would send keystrokes to a party the user didn't pick.
 */
data class ResolvedSearchEngine(val label: String, val queryUrl: String, val suggestUrl: String? = null)

/**
 * Pure logic for custom search engines (v5.8): JSON codec (persisted as one DataStore string,
 * the weatherCache pattern), validation, and built-in/custom resolution.
 */
object SearchEngines {

    /** Serializes for the `custom_search_engines` pref; empty list → "" (blank clears the key). */
    fun encode(engines: List<CustomSearchEngine>): String {
        if (engines.isEmpty()) return ""
        val array = JSONArray()
        engines.forEach { engine ->
            array.put(JSONObject().put("name", engine.name).put("template", engine.template))
        }
        return array.toString()
    }

    /** Lenient decode: malformed JSON or entries yield an empty/partial list, never a throw. */
    fun decode(json: String): List<CustomSearchEngine> {
        if (json.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(json)
            (0 until array.length()).mapNotNull { i ->
                val obj = array.optJSONObject(i) ?: return@mapNotNull null
                val name = obj.optString("name").trim()
                val template = obj.optString("template").trim()
                if (name.isEmpty() || template.isEmpty()) null
                else CustomSearchEngine(name, template)
            }
        }.getOrDefault(emptyList())
    }

    /**
     * Add-dialog validation. HTTPS is REQUIRED — an engine receiving every query over
     * plaintext is a privacy hole; `%s` marks where the encoded terms go (the convention
     * every browser documents).
     */
    fun validate(name: String, template: String): Boolean {
        val cleanName = name.trim()
        val cleanTemplate = template.trim()
        // The marker must live in the PATH/QUERY, never the authority: "https://%s.example.com"
        // would route every search to a term-derived host — a whole class of confusing
        // failures. Extracted case-insensitively (the scheme check below accepts any casing,
        // so "Https://%s.example.com" must not slip past a case-sensitive prefix strip).
        val authority = cleanTemplate.substringAfter("://", "").substringBefore('/')
        if (authority.contains("%s")) return false
        // The host must be dotted (or localhost, for self-hosted instances): a template like
        // "https://%s" parses to a bogus single-label host once the marker is substituted.
        // (Known limitation: IPv6 literal hosts are rejected — bracketed form has no dot.)
        val host = UrlHosts.of(cleanTemplate.replace("%s", "q"))
        return cleanName.isNotEmpty() &&
            cleanTemplate.startsWith("https://", ignoreCase = true) &&
            cleanTemplate.contains("%s") &&
            host != null && (host.contains('.') || host == "localhost")
    }

    /**
     * The engine searches actually use: the selected custom when it still exists, else the
     * built-in (a deleted selection falls back silently).
     */
    fun resolve(
        builtIn: SearchEngine,
        customs: List<CustomSearchEngine>,
        selectedName: String?,
    ): ResolvedSearchEngine {
        val custom = selectedName?.takeIf { it.isNotBlank() }
            ?.let { name -> customs.find { it.name == name } }
        return if (custom != null) {
            ResolvedSearchEngine(custom.name, custom.template, suggestUrl = null)
        } else {
            ResolvedSearchEngine(builtIn.label, builtIn.queryUrl, builtIn.suggestUrl)
        }
    }
}
