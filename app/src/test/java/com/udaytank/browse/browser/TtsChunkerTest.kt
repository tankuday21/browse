package com.udaytank.browse.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsChunkerTest {

    @Test
    fun `short text fits in one chunk`() {
        assertEquals(listOf("Hello world."), TtsChunker.chunk("Hello world.", 100))
    }

    @Test
    fun `empty input yields empty list`() {
        assertEquals(emptyList<String>(), TtsChunker.chunk("", 100))
        assertEquals(emptyList<String>(), TtsChunker.chunk("   \n  \n ", 100))
    }

    @Test
    fun `sentences pack together while they fit`() {
        val chunks = TtsChunker.chunk("One. Two. Three. Four.", 12)
        // "One. Two." is 9 chars, adding " Three." would make 16 > 12.
        assertEquals(listOf("One. Two.", "Three. Four."), chunks)
    }

    @Test
    fun `sentence of exactly maxLen stays whole`() {
        val sentence = "a".repeat(19) + "." // length 20
        val chunks = TtsChunker.chunk("$sentence $sentence", 20)
        assertEquals(listOf(sentence, sentence), chunks)
    }

    @Test
    fun `newlines are sentence boundaries`() {
        assertEquals(listOf("alpha beta", "gamma"), TtsChunker.chunk("alpha\nbeta\ngamma", 10))
    }

    @Test
    fun `giant sentence hard-splits at word boundaries`() {
        val words = (1..30).joinToString(" ") { "word$it" } // one long "sentence", no periods
        val chunks = TtsChunker.chunk(words, 25)
        assertTrue(chunks.size > 1)
        assertTrue(chunks.all { it.length <= 25 })
        assertTrue(chunks.none { it.isBlank() })
        // No word may be split: rejoining with spaces reproduces the input.
        assertEquals(words, chunks.joinToString(" "))
    }

    @Test
    fun `word longer than maxLen splits mid-word as last resort`() {
        val giant = "x".repeat(23)
        val chunks = TtsChunker.chunk("tiny $giant end", 10)
        assertTrue(chunks.all { it.length <= 10 })
        assertEquals("tiny${giant}end", chunks.joinToString("").replace(" ", ""))
    }

    @Test
    fun `no blank chunks and all content preserved`() {
        val text = "First sentence here. Second one!   Third?\n\n\nFourth."
        val chunks = TtsChunker.chunk(text, 15)
        assertTrue(chunks.none { it.isBlank() })
        assertTrue(chunks.all { it.length <= 15 })
        val squashed = chunks.joinToString(" ").replace(Regex("\\s+"), "")
        assertEquals(text.replace(Regex("\\s+"), ""), squashed)
    }
}
