package com.udaytank.browse.translate

import org.json.JSONArray

/**
 * Pure JSON marshalling between the app and the page's translate collector/applier scripts (v6.1).
 *
 * The collect script returns an ordered JSON array of the page's text-node strings; the apply
 * script consumes an ordered JSON array of their translations. Both directions go through
 * `evaluateJavascript`, so escaping must be exact — a translated string can contain quotes,
 * backslashes, newlines, or even `</script>`-like text, and none of it may break out of the
 * string literal we hand back to the page.
 */
object TranslatePayload {

    /** Lenient parse of the collector's output; malformed input yields an empty list, never throws. */
    fun parseCollected(json: String?): List<String> = runCatching {
        val array = JSONArray(json.orEmpty())
        (0 until array.length()).map { array.optString(it) }
    }.getOrDefault(emptyList())

    /**
     * The JSON array literal the apply script receives. `JSONArray.toString` handles quote/
     * backslash/newline escaping; we additionally neutralize `<` so a translated
     * `</script>` fragment can't terminate an inline script context on the page side.
     */
    fun buildApplyPayload(translations: List<String>): String {
        val array = JSONArray()
        translations.forEach { array.put(it) }
        return array.toString().replace("<", "\\u003C")
    }

    /**
     * A representative sample for language detection: the first [maxChars] characters of the
     * joined non-blank strings. Detecting on one short node misfires; the whole page is wasteful.
     */
    fun detectionSample(texts: List<String>, maxChars: Int = 600): String {
        val sb = StringBuilder()
        for (t in texts) {
            val trimmed = t.trim()
            if (trimmed.isEmpty()) continue
            if (sb.isNotEmpty()) sb.append(' ')
            sb.append(trimmed)
            if (sb.length >= maxChars) break
        }
        return sb.take(maxChars).toString()
    }
}
