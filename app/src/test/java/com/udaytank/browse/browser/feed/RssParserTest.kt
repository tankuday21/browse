package com.udaytank.browse.browser.feed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RssParserTest {

    private val rss = """
        <?xml version="1.0" encoding="UTF-8"?>
        <rss version="2.0" xmlns:media="http://search.yahoo.com/mrss/" xmlns:dc="http://purl.org/dc/elements/1.1/">
          <channel>
            <title>Sample News</title>
            <item>
              <title>  First headline  </title>
              <link>https://example.com/a</link>
              <pubDate>Wed, 15 Jul 2026 08:30:00 GMT</pubDate>
              <description>&lt;p&gt;A short&amp;nbsp;summary with &amp;amp; an entity.&lt;/p&gt;</description>
              <enclosure url="https://example.com/a.jpg" type="image/jpeg" length="1234"/>
            </item>
            <item>
              <title>Second headline</title>
              <link>https://example.com/b</link>
              <pubDate>Thu, 16 Jul 2026 09:00:00 +0000</pubDate>
              <media:thumbnail url="https://example.com/b-thumb.jpg"/>
            </item>
          </channel>
        </rss>
    """.trimIndent()

    private val atom = """
        <?xml version="1.0" encoding="UTF-8"?>
        <feed xmlns="http://www.w3.org/2005/Atom">
          <title>Sample Atom</title>
          <entry>
            <title>Atom entry</title>
            <link rel="alternate" href="https://example.org/entry1"/>
            <published>2026-07-15T08:30:00Z</published>
            <summary type="html">&lt;b&gt;Atom&lt;/b&gt; summary text</summary>
          </entry>
        </feed>
    """.trimIndent()

    @Test
    fun parsesRssItems() {
        val items = RssParser.parse(rss, "sample", FeedCategory.NEWS)
        assertEquals(2, items.size)

        val first = items[0]
        assertEquals("First headline", first.title)
        assertEquals("https://example.com/a", first.link)
        assertEquals("https://example.com/a.jpg", first.thumbnailUrl)
        assertEquals(FeedCategory.NEWS, first.category)
        assertEquals("sample", first.sourceId)
        assertTrue("pubDate should parse to epoch millis", first.publishedAt > 0L)
        // <description> is stripped of HTML tags, entities unescaped, whitespace collapsed.
        assertEquals("A short summary with & an entity.", first.description)

        val second = items[1]
        assertEquals("Second headline", second.title)
        assertEquals("https://example.com/b-thumb.jpg", second.thumbnailUrl)
        assertTrue(second.publishedAt > 0L)
    }

    @Test
    fun parsesAtomEntry() {
        val items = RssParser.parse(atom, "atom-src", FeedCategory.SPORTS)
        assertEquals(1, items.size)
        val e = items[0]
        assertEquals("Atom entry", e.title)
        assertEquals("https://example.org/entry1", e.link)
        assertEquals(FeedCategory.SPORTS, e.category)
        assertNull(e.thumbnailUrl)
        assertTrue(e.publishedAt > 0L)
        assertEquals("Atom summary text", e.description)
    }

    @Test
    fun malformedReturnsEmpty() {
        assertEquals(emptyList<FeedItem>(), RssParser.parse("<not valid <<<", "x", FeedCategory.NEWS))
        assertEquals(emptyList<FeedItem>(), RssParser.parse("", "x", FeedCategory.NEWS))
    }

    @Test
    fun skipsBlankTitleOrLink() {
        val xml = """
            <rss version="2.0"><channel>
              <item><title></title><link>https://example.com/x</link></item>
              <item><title>Has title</title><link>https://example.com/y</link></item>
            </channel></rss>
        """.trimIndent()
        val items = RssParser.parse(xml, "s", FeedCategory.NEWS)
        assertEquals(1, items.size)
        assertNotNull(items.firstOrNull { it.title == "Has title" })
    }
}
