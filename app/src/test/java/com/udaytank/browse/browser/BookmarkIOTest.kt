package com.udaytank.browse.browser

import com.udaytank.browse.data.Bookmark
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BookmarkIOTest {

    @Test
    fun `export then parse round-trips bookmarks`() {
        val original = listOf(
            Bookmark(url = "https://kotlinlang.org", title = "Kotlin", createdAt = 1_000),
            Bookmark(url = "https://developer.android.com", title = "Android Devs", createdAt = 2_000),
        )
        val html = BookmarkIO.export(original)
        val parsed = BookmarkIO.parse(html, now = 5_000)
        assertEquals(listOf("https://kotlinlang.org", "https://developer.android.com"), parsed.map { it.url })
        assertEquals(listOf("Kotlin", "Android Devs"), parsed.map { it.title })
    }

    @Test
    fun `special characters survive the round-trip`() {
        val html = BookmarkIO.export(listOf(Bookmark(url = "https://a.com?x=1&y=2", title = "A & B", createdAt = 1)))
        val parsed = BookmarkIO.parse(html, now = 1)
        assertEquals("https://a.com?x=1&y=2", parsed[0].url)
        assertEquals("A & B", parsed[0].title)
    }

    @Test
    fun `parse tolerates a real chrome export and skips non-http entries`() {
        val html = """
            <!DOCTYPE NETSCAPE-Bookmark-file-1>
            <DL><p>
                <DT><A HREF="https://good.com" ADD_DATE="1700000000">Good</A>
                <DT><A HREF="javascript:void(0)">Bad</A>
                <DT><A HREF="https://also-good.org">Also</A>
            </DL><p>
        """.trimIndent()
        val parsed = BookmarkIO.parse(html, now = 1)
        assertEquals(2, parsed.size)
        assertTrue(parsed.all { it.url.startsWith("http") })
    }
}
