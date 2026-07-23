package com.udaytank.browse.browser

import com.udaytank.browse.data.ReaderTheme
import java.util.Locale
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderModeTest {

    private fun build(
        title: String = "T",
        content: String = "<p>x</p>",
        theme: ReaderTheme = ReaderTheme.LIGHT,
        systemDark: Boolean = false,
        fontScale: Int = 100,
        wide: Boolean = false,
    ) = ReaderMode.buildReaderHtml(title, content, theme, systemDark, fontScale, wide)

    @Test
    fun `reader html carries the title and content`() {
        val html = build(title = "My Article", content = "<p>Hello world</p>")
        assertTrue(html.contains("My Article"))
        assertTrue(html.contains("<p>Hello world</p>"))
        assertTrue(html.contains("color-scheme: light"))
    }

    @Test
    fun `reader html shows a reading-time byline for real content and omits it when empty`() {
        val withText = build(content = "<p>${"word ".repeat(300)}</p>")
        assertTrue(withText.contains("min read"))
        assertTrue(withText.contains("reading-time"))
        // An empty body produces no byline (no bogus "0 min read").
        val empty = build(content = "<p></p>")
        assertFalse(empty.contains("min read"))
    }

    @Test
    fun `dark theme uses the dark palette`() {
        val html = build(theme = ReaderTheme.DARK)
        assertTrue(html.contains("#14142E"))
        assertTrue(html.contains("#E4E6F1"))
        assertTrue(html.contains("color-scheme: dark"))
    }

    @Test
    fun `sepia theme uses the sepia palette`() {
        val html = build(theme = ReaderTheme.SEPIA)
        assertTrue(html.contains("#F4ECD8"))
        assertTrue(html.contains("#5B4636"))
        assertTrue(html.contains("#8A6D3B"))
        assertTrue(html.contains("color-scheme: light"))
    }

    @Test
    fun `system theme with dark system resolves to the dark palette`() {
        val html = build(theme = ReaderTheme.SYSTEM, systemDark = true)
        assertTrue(html.contains("#14142E"))
        assertTrue(html.contains("color-scheme: dark"))
    }

    @Test
    fun `system theme with light system resolves to the light palette`() {
        val html = build(theme = ReaderTheme.SYSTEM, systemDark = false)
        assertTrue(html.contains("#FFFFFF"))
        assertTrue(html.contains("#171A2C"))
        assertTrue(html.contains("color-scheme: light"))
    }

    @Test
    fun `font scale 100 renders the 19px base size`() {
        val html = build(fontScale = 100)
        assertTrue(html.contains("font-size:19px"))
    }

    @Test
    fun `font scale 130 renders a scaled size with one decimal`() {
        val html = build(fontScale = 130)
        assertTrue(html.contains("font-size:24.7px"))
    }

    @Test
    fun `font size uses a dot decimal separator regardless of locale`() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY) // comma-decimal locale
            val html = build(fontScale = 130)
            assertTrue(html.contains("font-size:24.7px"))
            assertFalse(html.contains("24,7"))
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun `narrow layout constrains content to 680px`() {
        val html = build(wide = false)
        assertTrue(html.contains("max-width:680px"))
    }

    @Test
    fun `wide layout omits the max-width constraint`() {
        val html = build(wide = true)
        assertFalse(html.contains("max-width:680px"))
    }

    @Test
    fun `title is html-escaped to prevent breakage`() {
        val html = build(title = "A <b> & B")
        assertTrue(html.contains("A &lt;b&gt; &amp; B"))
    }
}
