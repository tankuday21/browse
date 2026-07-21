package com.udaytank.browse.media

import com.udaytank.browse.data.DownloadEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerProgressPolicyTest {

    @Test
    fun `resumes at a real mid-clip position`() {
        assertTrue(PlayerProgressPolicy.shouldResume(savedMs = 30_000, durationMs = 120_000))
    }

    @Test
    fun `does not resume at the very start`() {
        assertFalse(PlayerProgressPolicy.shouldResume(savedMs = 0, durationMs = 120_000))
    }

    @Test
    fun `does not resume within the finished tail`() {
        // 95% of 120s = 114s; a saved position past that is "finished", so restart.
        assertFalse(PlayerProgressPolicy.shouldResume(savedMs = 118_000, durationMs = 120_000))
    }

    @Test
    fun `does not resume when duration is unknown`() {
        assertFalse(PlayerProgressPolicy.shouldResume(savedMs = 30_000, durationMs = 0))
        assertFalse(PlayerProgressPolicy.shouldResume(savedMs = 30_000, durationMs = -1))
    }

    @Test
    fun `finished only within the tail and never with an unknown duration`() {
        assertTrue(PlayerProgressPolicy.isFinished(positionMs = 119_000, durationMs = 120_000))
        assertFalse(PlayerProgressPolicy.isFinished(positionMs = 60_000, durationMs = 120_000))
        assertFalse(PlayerProgressPolicy.isFinished(positionMs = 60_000, durationMs = 0))
    }
}

class PlayerQueuePolicyTest {

    private fun media(id: Long, mime: String?, createdAt: Long, state: String = "DONE", path: String? = "/f/$id"): DownloadEntry =
        DownloadEntry(id = id, fileName = "f$id", url = "https://a/$id", createdAt = createdAt, state = state, filePath = path, mimeType = mime)

    @Test
    fun `top level type extraction`() {
        assertEquals("audio", PlayerQueuePolicy.topLevelType("audio/mpeg"))
        assertEquals("video", PlayerQueuePolicy.topLevelType("video/mp4"))
        assertEquals(null, PlayerQueuePolicy.topLevelType(null))
        assertEquals(null, PlayerQueuePolicy.topLevelType("garbage")) // no slash
        assertEquals(null, PlayerQueuePolicy.topLevelType("  "))
    }

    @Test
    fun `queue keeps only same-kind finished files with a path, oldest first`() {
        val items = listOf(
            media(1, "audio/mpeg", createdAt = 30),
            media(2, "video/mp4", createdAt = 20),          // wrong kind
            media(3, "audio/aac", createdAt = 10),
            media(4, "audio/mpeg", createdAt = 5, state = "RUNNING"), // not finished
            media(5, "audio/ogg", createdAt = 15, path = null),        // no file
        )
        val queue = PlayerQueuePolicy.buildQueue(items, currentId = 1, topLevel = "audio")
        assertEquals(listOf(3L, 1L), queue.map { it.id }) // createdAt 10 then 30
    }

    @Test
    fun `a lone matching item yields a single-item queue containing itself`() {
        val items = listOf(media(1, "video/mp4", createdAt = 1))
        val queue = PlayerQueuePolicy.buildQueue(items, currentId = 1, topLevel = "video")
        assertEquals(listOf(1L), queue.map { it.id })
    }
}
