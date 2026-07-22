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

    // JS line terminators (invalid raw inside a JS string on stricter engines). Built from code
    // points so the source stays readable ASCII rather than embedding the literal characters.
    private val LINE_SEP = Char(0x2028).toString() // U+2028 LINE SEPARATOR
    private val PARA_SEP = Char(0x2029).toString() // U+2029 PARAGRAPH SEPARATOR

    /** Lenient parse of the collector's output; malformed input yields an empty list, never throws. */
    fun parseCollected(json: String?): List<String> = runCatching {
        val array = JSONArray(json.orEmpty())
        (0 until array.length()).map { array.optString(it) }
    }.getOrDefault(emptyList())

    /**
     * The JSON array literal the apply script receives. `JSONArray.toString` handles quote/
     * backslash/newline escaping; we additionally neutralize the chars that stay dangerous inside
     * a JS string literal on the shipped runtime. Escaping must NOT depend on which `org.json` is
     * on the classpath: Android's platform `JSONStringer` only `\u`-escapes chars `<= 0x1F`, so we
     * explicitly handle `<` (a `</script>` breakout) and U+2028/U+2029 (raw JS line terminators
     * that would close the literal on a stricter engine) ourselves.
     */
    fun buildApplyPayload(translations: List<String>): String {
        val array = JSONArray()
        translations.forEach { array.put(it) }
        return array.toString()
            .replace("<", "\\u003C")
            .replace(LINE_SEP, "\\u2028")
            .replace(PARA_SEP, "\\u2029")
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
