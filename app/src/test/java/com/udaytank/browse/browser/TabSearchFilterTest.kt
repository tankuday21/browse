package com.udaytank.browse.browser

import com.udaytank.browse.data.TabEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class TabSearchFilterTest {
    private fun tab(id: Long, url: String, title: String) =
        TabEntity(id = id, url = url, title = title, position = id.toInt(), isActive = false)

    private val tabs = listOf(
        tab(1, "https://kotlinlang.org/docs", "Kotlin Docs"),
        tab(2, "https://news.ycombinator.com", "Hacker News"),
        tab(3, "https://developer.android.com", "Android Developers"),
    )

    @Test fun `blank query returns everything`() {
        assertEquals(tabs, TabSearchFilter.filter(tabs, "  "))
    }

    @Test fun `matches title case-insensitively`() {
        assertEquals(listOf(1L), TabSearchFilter.filter(tabs, "kotlin d").map { it.id })
    }

    @Test fun `matches url when title does not`() {
        assertEquals(listOf(2L), TabSearchFilter.filter(tabs, "ycombinator").map { it.id })
    }

    @Test fun `no match returns empty`() {
        assertEquals(emptyList<TabEntity>(), TabSearchFilter.filter(tabs, "zzz"))
    }
}
