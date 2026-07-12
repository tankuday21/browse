package com.udaytank.browse.browser

import org.junit.Assert.assertEquals
import org.junit.Test

class HtmlTextTest {

    @Test
    fun `strips inline tags keeping their text`() {
        assertEquals(
            "Hello brave new world",
            HtmlText.strip("Hello <b>brave</b> <a href=\"x\">new</a> <span class='y'>world</span>"),
        )
    }

    @Test
    fun `block closers become sentence breaks with injected period`() {
        assertEquals(
            "First paragraph.\nSecond paragraph.",
            HtmlText.strip("<p>First paragraph</p><p>Second paragraph</p>"),
        )
    }

    @Test
    fun `no period injected when the block already ends with terminal punctuation`() {
        assertEquals(
            "Done!\nReally?\nYes.",
            HtmlText.strip("<p>Done!</p><p>Really?</p><p>Yes.</p>"),
        )
    }

    @Test
    fun `headings list items blockquotes and br all break`() {
        assertEquals(
            "Title.\nPoint one.\nQuote.\nline a.\nline b",
            HtmlText.strip("<h1>Title</h1><ul><li>Point one</li></ul><blockquote>Quote</blockquote>line a<br>line b"),
        )
    }

    @Test
    fun `br variants are recognized`() {
        assertEquals("a.\nb.\nc", HtmlText.strip("a<br/>b<br />c"))
    }

    @Test
    fun `entities are decoded`() {
        assertEquals(
            "Fish & chips <tag> \"quoted\" 'single' spaced end",
            HtmlText.strip("Fish &amp; chips &lt;tag&gt; &quot;quoted&quot; &#39;single&#39; spaced&nbsp;end"),
        )
    }

    @Test
    fun `numeric and hex entities are decoded`() {
        assertEquals("A’s café", HtmlText.strip("A&#x2019;s caf&#233;"))
    }

    @Test
    fun `unknown or bare ampersands survive untouched`() {
        assertEquals("AT&T & &bogusentity; done", HtmlText.strip("AT&T & &bogusentity; done"))
    }

    @Test
    fun `whitespace runs collapse`() {
        assertEquals(
            "one two three.\nfour",
            HtmlText.strip("<p>one \n\t  two     three</p>   four  "),
        )
    }

    @Test
    fun `script and style content is dropped`() {
        assertEquals(
            "Before. After",
            HtmlText.strip("Before.<script type=\"text/javascript\">var x = '<p>evil</p>';</script><style>p { color: red }</style> After"),
        )
    }

    @Test
    fun `empty and tag-only input produce empty output`() {
        assertEquals("", HtmlText.strip(""))
        assertEquals("", HtmlText.strip("<p>   </p><div></div>"))
    }

    @Test
    fun `plain text without breaks passes through without added punctuation`() {
        assertEquals("just words", HtmlText.strip("just words"))
    }
}
