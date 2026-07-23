package com.udaytank.browse.browser

import com.udaytank.browse.data.Bookmark
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookmarkSearchTest {

    private fun bm(url: String, title: String) =
        Bookmark(url = url, title = title, createdAt = 0, orbitId = 1)

    @Test
    fun `matches hits title and url case-insensitively`() {
        val b = bm("https://developer.mozilla.org", "MDN Web Docs")
        assertTrue(BookmarkSearch.matches(b, "mdn"))
        assertTrue(BookmarkSearch.matches(b, "WEB"))
        assertTrue(BookmarkSearch.matches(b, "mozilla"))
        assertFalse(BookmarkSearch.matches(b, "python"))
    }

    @Test
    fun `blank query matches everything`() {
        val b = bm("https://a.com", "A")
        assertTrue(BookmarkSearch.matches(b, ""))
        assertTrue(BookmarkSearch.matches(b, "   "))
    }

    @Test
    fun `filter keeps order and drops non-matches`() {
        val list = listOf(
            bm("https://kotlinlang.org", "Kotlin"),
            bm("https://python.org", "Python"),
            bm("https://developer.android.com", "Android Developers"),
        )
        val hits = BookmarkSearch.filter(list, "o") // Kotlin, Python(url), Android(url) all contain 'o'
        assertEquals(listOf("Kotlin", "Python", "Android Developers"), hits.map { it.title })

        val android = BookmarkSearch.filter(list, "android")
        assertEquals(listOf("Android Developers"), android.map { it.title })
    }

    @Test
    fun `blank filter returns the list unchanged and no-match returns empty`() {
        val list = listOf(bm("https://a.com", "A"), bm("https://b.com", "B"))
        assertEquals(list, BookmarkSearch.filter(list, "  "))
        assertTrue(BookmarkSearch.filter(list, "zzz").isEmpty())
    }
}
