package com.udaytank.browse.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsQueueTest {

    private fun item(id: Long) = TtsQueue.Item(id, "Article $id")

    @Test
    fun `walks items in the given order`() {
        val queue = TtsQueue(listOf(item(1), item(2), item(3)))
        assertEquals(1L, queue.current?.id)
        assertTrue(queue.hasNext)
        assertEquals(2L, queue.next()?.id)
        assertEquals(2L, queue.current?.id)
        assertTrue(queue.hasNext)
        assertEquals(3L, queue.next()?.id)
        assertFalse(queue.hasNext)
    }

    @Test
    fun `exhaustion makes current null and stays null`() {
        val queue = TtsQueue(listOf(item(1)))
        assertNull(queue.next())
        assertNull(queue.current)
        assertNull(queue.next()) // repeated next() past the end is harmless
        assertNull(queue.current)
        assertFalse(queue.hasNext)
    }

    @Test
    fun `single item queue has no next`() {
        val queue = TtsQueue(listOf(item(7)))
        assertEquals(7L, queue.current?.id)
        assertFalse(queue.hasNext)
    }

    @Test
    fun `empty queue starts exhausted`() {
        val queue = TtsQueue(emptyList())
        assertNull(queue.current)
        assertFalse(queue.hasNext)
        assertNull(queue.next())
    }
}
