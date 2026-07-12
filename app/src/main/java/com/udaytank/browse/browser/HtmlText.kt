package com.udaytank.browse.browser

/**
 * Converts cleaned reader HTML into plain text suitable for TTS. Block-level
 * closers become sentence breaks (a "." is appended when the block doesn't
 * already end with terminal punctuation, so the engine pauses naturally),
 * remaining tags are dropped, common entities are decoded, and whitespace
 * runs collapse to single spaces. Script/style bodies are removed defensively
 * even though the reader extractor shouldn't emit them.
 */
object HtmlText {

    /** Script/style elements including their text content. */
    private val SCRIPT_STYLE = Regex("(?is)<(script|style)\\b[^>]*>.*?</\\1\\s*>")

    /** Closers of block elements (and <br>) that should read as sentence/paragraph breaks. */
    private val BLOCK_BREAK = Regex("(?i)</(?:p|h[1-6]|li|blockquote)\\s*>|<br\\s*/?\\s*>")

    private val TAG = Regex("<[^>]*>")
    private val WHITESPACE = Regex("[\\s\\u00A0]+")

    /** Characters that already terminate a spoken clause — no "." is injected after them. */
    private const val TERMINAL = ".!?…:;"

    /** Internal marker for block breaks; NUL can't legally appear in HTML text. */
    private const val BREAK = '\u0000'

    fun strip(html: String): String {
        var s = SCRIPT_STYLE.replace(html, " ")
        s = BLOCK_BREAK.replace(s, BREAK.toString())
        s = TAG.replace(s, " ")
        s = decodeEntities(s)
        val raw = s.split(BREAK)
        val out = StringBuilder()
        raw.forEachIndexed { index, rawSegment ->
            val segment = WHITESPACE.replace(rawSegment, " ").trim()
            if (segment.isEmpty()) return@forEachIndexed
            if (out.isNotEmpty()) out.append('\n')
            out.append(segment)
            // A break followed this segment: make sure TTS hears a clause end.
            if (index < raw.lastIndex && segment.last() !in TERMINAL) out.append('.')
        }
        return out.toString()
    }

    /** Named entities the reader content actually uses, plus numeric (&#39; &#x2019;) forms. */
    private fun decodeEntities(s: String): String {
        if ('&' !in s) return s
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c != '&') {
                sb.append(c)
                i++
                continue
            }
            val end = s.indexOf(';', i + 1)
            val body = if (end in (i + 2)..(i + 10)) s.substring(i + 1, end) else null
            val decoded = when {
                body == null -> null
                body == "amp" -> "&"
                body == "lt" -> "<"
                body == "gt" -> ">"
                body == "quot" -> "\""
                body == "apos" -> "'"
                body == "nbsp" -> " "
                body.startsWith("#x") || body.startsWith("#X") ->
                    body.drop(2).toIntOrNull(16)?.takeIf { it in 1..0x10FFFF }
                        ?.let { String(Character.toChars(it)) }
                body.startsWith("#") ->
                    body.drop(1).toIntOrNull()?.takeIf { it in 1..0x10FFFF }
                        ?.let { String(Character.toChars(it)) }
                else -> null
            }
            if (decoded != null) {
                sb.append(decoded)
                i = end + 1
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }
}
