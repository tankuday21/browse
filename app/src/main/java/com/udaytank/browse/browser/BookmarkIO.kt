package com.udaytank.browse.browser

import com.udaytank.browse.data.Bookmark

/** Import/export bookmarks in the Netscape bookmark-file format every browser understands. */
object BookmarkIO {

    fun export(bookmarks: List<Bookmark>): String {
        val sb = StringBuilder()
        sb.append("<!DOCTYPE NETSCAPE-Bookmark-file-1>\n")
        sb.append("<META HTTP-EQUIV=\"Content-Type\" CONTENT=\"text/html; charset=UTF-8\">\n")
        sb.append("<TITLE>Bookmarks</TITLE>\n<H1>Bookmarks</H1>\n<DL><p>\n")
        bookmarks.forEach { b ->
            sb.append("    <DT><A HREF=\"${escape(b.url)}\" ADD_DATE=\"${b.createdAt / 1000}\">")
            sb.append("${escape(b.title)}</A>\n")
        }
        sb.append("</DL><p>\n")
        return sb.toString()
    }

    /** Parses HREF/anchor pairs from a Netscape bookmark file. Robust to messy real-world files. */
    fun parse(html: String, now: Long): List<Bookmark> {
        val regex = Regex("""<A[^>]*HREF="([^"]*)"[^>]*>(.*?)</A>""", RegexOption.IGNORE_CASE)
        return regex.findAll(html).mapNotNull { match ->
            val url = unescape(match.groupValues[1]).trim()
            val title = unescape(match.groupValues[2]).trim()
            if (url.startsWith("http")) {
                Bookmark(url = url, title = title.ifBlank { url }, createdAt = now)
            } else {
                null
            }
        }.toList()
    }

    private fun escape(s: String) = s
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun unescape(s: String) = s
        .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
}
