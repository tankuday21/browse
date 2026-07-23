package com.udaytank.browse.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReadingTimeTest {

    @Test
    fun `wordsInHtml strips tags and collapses whitespace`() {
        assertEquals(3, ReadingTime.wordsInHtml("<p>one two</p><p>three</p>"))
        assertEquals(3, ReadingTime.wordsInHtml("  one\n\t two   three  "))
    }

    @Test
    fun `wordsInHtml ignores tag attributes`() {
        assertEquals(1, ReadingTime.wordsInHtml("""<a href="x y z">one</a>"""))
        assertEquals(2, ReadingTime.wordsInHtml("""<img src="a.jpg"><span class="c d">hello there</span>"""))
    }

    @Test
    fun `wordsInHtml is zero for empty or tags-only`() {
        assertEquals(0, ReadingTime.wordsInHtml(""))
        assertEquals(0, ReadingTime.wordsInHtml("   "))
        assertEquals(0, ReadingTime.wordsInHtml("<p></p><br><div></div>"))
    }

    @Test
    fun `minutes rounds up with a floor of one`() {
        assertEquals(0, ReadingTime.minutes(0))
        assertEquals(1, ReadingTime.minutes(1))
        assertEquals(1, ReadingTime.minutes(200))
        assertEquals(2, ReadingTime.minutes(201))
        assertEquals(2, ReadingTime.minutes(400))
        assertEquals(3, ReadingTime.minutes(401))
    }

    @Test
    fun `label is null for no words and formatted otherwise`() {
        assertNull(ReadingTime.label("<p></p>"))
        assertEquals("1 min read", ReadingTime.label("word ".repeat(200)))
        assertEquals("3 min read", ReadingTime.label("<p>${"word ".repeat(450)}</p>"))
    }
}
