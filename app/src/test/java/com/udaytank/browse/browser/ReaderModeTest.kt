package com.udaytank.browse.browser

import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderModeTest {

    @Test
    fun `reader html carries the title and content`() {
        val html = ReaderMode.buildReaderHtml("My Article", "<p>Hello world</p>", dark = false)
        assertTrue(html.contains("My Article"))
        assertTrue(html.contains("<p>Hello world</p>"))
        assertTrue(html.contains("color-scheme: light"))
    }

    @Test
    fun `dark reader uses the dark palette`() {
        val html = ReaderMode.buildReaderHtml("T", "<p>x</p>", dark = true)
        assertTrue(html.contains("#14142E"))
        assertTrue(html.contains("color-scheme: dark"))
    }

    @Test
    fun `title is html-escaped to prevent breakage`() {
        val html = ReaderMode.buildReaderHtml("A <b> & B", "<p>x</p>", dark = false)
        assertTrue(html.contains("A &lt;b&gt; &amp; B"))
    }
}
