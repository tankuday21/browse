package com.udaytank.browse.browser.feed

import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import javax.xml.parsers.DocumentBuilderFactory

/** Parses RSS 2.0 and Atom feeds into [FeedItem]s without touching the network. */
object RssParser {

    // Extra RFC-822/1123-ish patterns beyond RFC_1123_DATE_TIME (which is picky about "GMT" vs offsets).
    private val RSS_FALLBACK_PATTERNS = listOf(
        "EEE, dd MMM yyyy HH:mm:ss Z",
        "EEE, dd MMM yyyy HH:mm:ss zzz",
        "dd MMM yyyy HH:mm:ss Z",
    )

    /**
     * Parse RSS 2.0 or Atom XML into [FeedItem]s tagged with [sourceId]/[category].
     * Supports RSS <item> (title, link, pubDate or dc:date, thumbnail from <enclosure url>,
     * <media:thumbnail url>, or <media:content url>) and Atom <entry> (title, <link href>,
     * published or updated). Returns [] on any parse failure. Trims text, skips items with a
     * blank title or link.
     */
    fun parse(xml: String, sourceId: String, category: FeedCategory): List<FeedItem> {
        val doc = runCatching {
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = false
                isValidating = false
                // Harden against XXE / entity expansion; feeds are untrusted input.
                runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
                runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
                runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
                isExpandEntityReferences = false
            }
            factory.newDocumentBuilder().parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))
        }.getOrNull() ?: return emptyList()

        val items = doc.getElementsByTagName("item")
        val entries = doc.getElementsByTagName("entry")

        val result = mutableListOf<FeedItem>()
        for (i in 0 until items.length) {
            (items.item(i) as? Element)?.let { parseRssItem(it, sourceId, category)?.let(result::add) }
        }
        for (i in 0 until entries.length) {
            (entries.item(i) as? Element)?.let { parseAtomEntry(it, sourceId, category)?.let(result::add) }
        }
        return result
    }

    private fun parseRssItem(item: Element, sourceId: String, category: FeedCategory): FeedItem? {
        val title = firstText(item, "title")
        val link = firstText(item, "link")
        if (title.isBlank() || link.isBlank()) return null

        val dateText = firstText(item, "pubDate").ifBlank { firstText(item, "dc:date") }
        val publishedAt = parseRssDate(dateText)
        val thumbnail = rssThumbnail(item)
        val description = snippet(firstText(item, "description"))

        return FeedItem(sourceId, title, link, publishedAt, thumbnail, category, description)
    }

    private fun parseAtomEntry(entry: Element, sourceId: String, category: FeedCategory): FeedItem? {
        val title = firstText(entry, "title")
        val link = atomLink(entry)
        if (title.isBlank() || link.isBlank()) return null

        val dateText = firstText(entry, "published").ifBlank { firstText(entry, "updated") }
        val publishedAt = parseAtomDate(dateText)
        val description = snippet(firstText(entry, "summary").ifBlank { firstText(entry, "content") })

        return FeedItem(sourceId, title, link, publishedAt, thumbnailUrl = null, category, description)
    }

    /** Thumbnail: first of enclosure@url (image type or none), media:thumbnail@url, media:content@url. */
    private fun rssThumbnail(item: Element): String? {
        childElements(item, "enclosure").forEach { enc ->
            val url = enc.getAttribute("url").trim()
            val type = enc.getAttribute("type").trim()
            if (url.isNotBlank() && (type.isBlank() || type.startsWith("image"))) return url
        }
        firstAttr(item, "media:thumbnail", "url")?.let { return it }
        firstAttr(item, "media:content", "url")?.let { return it }
        return null
    }

    /** Atom link: prefer rel="alternate" with href, else the first link carrying an href. */
    private fun atomLink(entry: Element): String {
        val links = childElements(entry, "link")
        links.firstOrNull { it.getAttribute("rel").trim() == "alternate" && it.getAttribute("href").isNotBlank() }
            ?.let { return it.getAttribute("href").trim() }
        links.firstOrNull { it.getAttribute("href").isNotBlank() }
            ?.let { return it.getAttribute("href").trim() }
        return ""
    }

    private fun parseRssDate(text: String): Long {
        if (text.isBlank()) return 0L
        runCatching {
            return OffsetDateTime.parse(text, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()
        }
        for (pattern in RSS_FALLBACK_PATTERNS) {
            runCatching {
                val fmt = DateTimeFormatter.ofPattern(pattern, java.util.Locale.ENGLISH)
                return OffsetDateTime.parse(text, fmt).toInstant().toEpochMilli()
            }
        }
        return 0L
    }

    private fun parseAtomDate(text: String): Long {
        if (text.isBlank()) return 0L
        return runCatching { OffsetDateTime.parse(text).toInstant().toEpochMilli() }.getOrDefault(0L)
    }

    // --- Snippet cleanup ---

    private val HTML_TAG = Regex("<[^>]*>")
    private val WHITESPACE = Regex("\\s+")
    private const val SNIPPET_MAX = 160

    /**
     * Turn a raw RSS/Atom description/summary into a short plain-text snippet: strip HTML tags,
     * unescape the common entities, collapse whitespace, and trim to ~[SNIPPET_MAX] chars.
     * Returns "" when the source had nothing usable.
     */
    private fun snippet(raw: String): String {
        if (raw.isBlank()) return ""
        val text = HTML_TAG.replace(raw, "")
            .replace("&nbsp;", " ")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&amp;", "&") // last, so "&amp;lt;" → "&lt;" not "<"
            .let { WHITESPACE.replace(it, " ") }
            .trim()
        return if (text.length > SNIPPET_MAX) text.take(SNIPPET_MAX).trimEnd() + "…" else text
    }

    // --- DOM helpers (namespace-unaware: tags matched by literal "prefix:local" name) ---

    private fun childElements(parent: Element, tag: String): List<Element> {
        val nodes = parent.getElementsByTagName(tag)
        return (0 until nodes.length).mapNotNull { nodes.item(it) as? Element }
    }

    private fun firstText(parent: Element, tag: String): String =
        childElements(parent, tag).firstOrNull()?.textContent?.trim().orEmpty()

    private fun firstAttr(parent: Element, tag: String, attr: String): String? =
        childElements(parent, tag).firstNotNullOfOrNull { it.getAttribute(attr).trim().ifBlank { null } }
}
