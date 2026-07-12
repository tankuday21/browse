package com.udaytank.browse.browser

import com.udaytank.browse.data.Bookmark
import com.udaytank.browse.data.HomeShortcutEntity
import com.udaytank.browse.data.ReadingListEntry
import com.udaytank.browse.data.TabGroupEntity

/**
 * Everything a backup file carries (J1). Reading list is METADATA only — offline article
 * files never travel. Row ids are deliberately not serialized: they're per-device
 * autoincrements, and restore always merges into an existing database.
 */
data class Backup(
    val settings: Map<String, String>,
    val bookmarks: List<Bookmark>,
    val homeShortcuts: List<HomeShortcutEntity>,
    val readingList: List<ReadingListEntry>,
    val tabGroups: List<TabGroupEntity>,
)

/**
 * Versioned JSON encode/decode for [Backup]. JSON is hand-rolled (same reasoning as
 * [ReaderExtraction]): org.json is an unusable android.jar stub in local unit tests, and
 * the codec must stay dependency-free and plain-JUnit testable.
 */
object BackupCodec {

    const val SCHEMA_VERSION = 1

    fun encode(backup: Backup): String = buildString {
        append("{\"schemaVersion\":").append(SCHEMA_VERSION)
        append(",\"settings\":{")
        backup.settings.entries.forEachIndexed { i, (key, value) ->
            if (i > 0) append(',')
            append(str(key)).append(':').append(str(value))
        }
        append("},\"bookmarks\":[")
        backup.bookmarks.forEachIndexed { i, b ->
            if (i > 0) append(',')
            append("{\"url\":").append(str(b.url))
            append(",\"title\":").append(str(b.title))
            append(",\"createdAt\":").append(b.createdAt)
            append(",\"folder\":").append(b.folder?.let(::str) ?: "null")
            append('}')
        }
        append("],\"homeShortcuts\":[")
        backup.homeShortcuts.forEachIndexed { i, s ->
            if (i > 0) append(',')
            append("{\"url\":").append(str(s.url))
            append(",\"title\":").append(str(s.title))
            append(",\"position\":").append(s.position)
            append('}')
        }
        append("],\"readingList\":[")
        backup.readingList.forEachIndexed { i, r ->
            if (i > 0) append(',')
            append("{\"url\":").append(str(r.url))
            append(",\"title\":").append(str(r.title))
            append(",\"addedAt\":").append(r.addedAt)
            append(",\"readAt\":").append(r.readAt?.toString() ?: "null")
            append('}')
        }
        append("],\"tabGroups\":[")
        backup.tabGroups.forEachIndexed { i, g ->
            if (i > 0) append(',')
            append("{\"name\":").append(str(g.name))
            append(",\"color\":").append(g.color)
            append(",\"position\":").append(g.position)
            append('}')
        }
        append("]}")
    }

    /** Null on malformed input or a schemaVersion this build doesn't know (newer app's file). */
    fun decode(text: String): Backup? = runCatching {
        val root = Parser(text).parseSingle() as? Map<*, *> ?: return null
        if ((root["schemaVersion"] as? Double)?.toInt() != SCHEMA_VERSION) return null

        val settings = (root["settings"] as? Map<*, *> ?: return null)
            .entries.associate { (k, v) -> (k as String) to (v as String) }

        val bookmarks = (root["bookmarks"] as? List<*> ?: return null).map { raw ->
            val o = raw as Map<*, *>
            Bookmark(
                url = o["url"] as String,
                title = o["title"] as String,
                createdAt = (o["createdAt"] as Double).toLong(),
                folder = o["folder"] as String?,
            )
        }

        val homeShortcuts = (root["homeShortcuts"] as? List<*> ?: return null).map { raw ->
            val o = raw as Map<*, *>
            HomeShortcutEntity(
                url = o["url"] as String,
                title = o["title"] as String,
                position = (o["position"] as Double).toInt(),
            )
        }

        val readingList = (root["readingList"] as? List<*> ?: return null).map { raw ->
            val o = raw as Map<*, *>
            ReadingListEntry(
                url = o["url"] as String,
                title = o["title"] as String,
                addedAt = (o["addedAt"] as Double).toLong(),
                readAt = (o["readAt"] as Double?)?.toLong(),
                filePath = null, // metadata only, never a device path
            )
        }

        val tabGroups = (root["tabGroups"] as? List<*> ?: return null).map { raw ->
            val o = raw as Map<*, *>
            TabGroupEntity(
                name = o["name"] as String,
                color = (o["color"] as Double).toInt(),
                position = (o["position"] as Double).toInt(),
            )
        }

        Backup(settings, bookmarks, homeShortcuts, readingList, tabGroups)
    }.getOrNull()

    /** JSON string literal with the mandatory escapes; non-ASCII goes through untouched. */
    private fun str(s: String): String = buildString(s.length + 2) {
        append('"')
        for (c in s) {
            when {
                c == '"' -> append("\\\"")
                c == '\\' -> append("\\\\")
                c == '\n' -> append("\\n")
                c == '\r' -> append("\\r")
                c == '\t' -> append("\\t")
                c < ' ' -> append("\\u%04x".format(c.code))
                else -> append(c)
            }
        }
        append('"')
    }

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
