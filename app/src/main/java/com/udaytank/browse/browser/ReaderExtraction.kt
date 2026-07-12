package com.udaytank.browse.browser

/**
 * Decodes the payload `WebView.evaluateJavascript` hands back for [ReaderMode.EXTRACT_SCRIPT].
 *
 * The script returns `JSON.stringify({ok, title, content})`, and evaluateJavascript re-encodes
 * that string as a JSON *string literal* — so the payload is double-encoded: unwrap the outer
 * literal, then read the object. Parsing is hand-rolled (no org.json) because org.json is an
 * unusable android.jar stub in local unit tests, and the save-for-later pipeline must stay
 * plain-JUnit testable.
 */
object ReaderExtraction {

    data class Result(val title: String, val content: String)

    /** Null when extraction reported `ok:false`, content is blank, or the payload is malformed. */
    fun parse(raw: String?): Result? = runCatching {
        val trimmed = raw?.trim().takeUnless { it.isNullOrEmpty() } ?: return null
        val inner = Parser(trimmed).parseSingle() as? String ?: return null
        val obj = Parser(inner).parseSingle() as? Map<*, *> ?: return null
        if (obj["ok"] != true) return null
        val content = (obj["content"] as? String).takeUnless { it.isNullOrBlank() } ?: return null
        Result(title = obj["title"] as? String ?: "", content = content)
    }.getOrNull()

    /** Minimal JSON reader: objects, arrays, strings, numbers, booleans, null. */
    private class Parser(private val s: String) {
        private var i = 0

        fun parseSingle(): Any? {
            val value = parseValue()
            skipWs()
            check(i == s.length) { "trailing data" }
            return value
        }

        private fun parseValue(): Any? {
            skipWs()
            return when (peek()) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> parseString()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> parseNumber()
            }
        }

        private fun parseObject(): Map<String, Any?> {
            i++ // consume '{'
            val map = mutableMapOf<String, Any?>()
            skipWs()
            if (peek() == '}') { i++; return map }
            while (true) {
                skipWs()
                val key = parseString()
                skipWs()
                check(peek() == ':') { "expected ':'" }
                i++
                map[key] = parseValue()
                skipWs()
                when (peek()) {
                    ',' -> i++
                    '}' -> { i++; return map }
                    else -> error("expected ',' or '}'")
                }
            }
        }

        private fun parseArray(): List<Any?> {
            i++ // consume '['
            val list = mutableListOf<Any?>()
            skipWs()
            if (peek() == ']') { i++; return list }
            while (true) {
                list += parseValue()
                skipWs()
                when (peek()) {
                    ',' -> i++
                    ']' -> { i++; return list }
                    else -> error("expected ',' or ']'")
                }
            }
        }

        private fun parseString(): String {
            check(peek() == '"') { "expected string" }
            i++
            val sb = StringBuilder()
            while (true) {
                check(i < s.length) { "unterminated string" }
                when (val c = s[i++]) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        check(i < s.length) { "dangling escape" }
                        when (val e = s[i++]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'f' -> sb.append(12.toChar())
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                check(i + 4 <= s.length) { "bad unicode escape" }
                                sb.append(s.substring(i, i + 4).toInt(16).toChar())
                                i += 4
                            }
                            else -> error("bad escape \\$e")
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }

        private fun parseNumber(): Double {
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] in "-+.eE")) i++
            check(i > start) { "expected value" }
            return s.substring(start, i).toDouble()
        }

        private fun literal(text: String, value: Any?): Any? {
            check(s.startsWith(text, i)) { "bad literal" }
            i += text.length
            return value
        }

        private fun peek(): Char {
            check(i < s.length) { "unexpected end of input" }
            return s[i]
        }

        private fun skipWs() {
            while (i < s.length && s[i].isWhitespace()) i++
        }
    }
}
