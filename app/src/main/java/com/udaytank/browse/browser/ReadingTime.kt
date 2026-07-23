package com.udaytank.browse.browser

import kotlin.math.ceil

/**
 * Pure reading-time estimate for reader mode (v6.11). Counts words in the extracted article HTML
 * and converts to minutes at a standard adult silent-reading rate.
 */
object ReadingTime {

    /** Standard adult silent-reading rate (words/minute); the usual 200–250 range, conservative end. */
    private const val WORDS_PER_MINUTE = 200

    private val TAG = Regex("<[^>]*>")
    private val WHITESPACE = Regex("\\s+")

    /** Word count of [html] with tags stripped and whitespace collapsed. */
    fun wordsInHtml(html: String): Int {
        val text = TAG.replace(html, " ").trim()
        if (text.isEmpty()) return 0
        return WHITESPACE.split(text).count { it.isNotEmpty() }
    }

    /** ceil(words / [WORDS_PER_MINUTE]), floored at 1 for any words; 0 for none. */
    fun minutes(words: Int): Int =
        if (words <= 0) 0 else maxOf(1, ceil(words.toDouble() / WORDS_PER_MINUTE).toInt())

    /** "N min read", or null when there are no words (no bogus estimate for an empty body). */
    fun label(html: String): String? {
        val words = wordsInHtml(html)
        return if (words == 0) null else "${minutes(words)} min read"
    }
}
