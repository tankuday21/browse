package com.udaytank.browse

import com.udaytank.browse.data.DownloadEntry
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Documents the v6.8 atomic-claim contract that DownloadService.handleStart relies on: a start may
 * only claim RUNNING for a row that hasn't been cancelled or finished. Mirrors the Room query; the
 * authoritative SQL check runs on-device in BrowseDatabaseTest.downloadDao_markRunningIfLive.
 */
class FakeDownloadDaoClaimTest {

    @Test
    fun `markRunningIfLive claims a live row and rejects cancelled, done, and missing`() = runTest {
        val dao = FakeDownloadDao()
        val live = dao.insertReturning(DownloadEntry(fileName = "a", url = "https://a", createdAt = 1, state = "PENDING"))
        val cancelled = dao.insertReturning(DownloadEntry(fileName = "b", url = "https://b", createdAt = 2, state = "CANCELLED"))
        val done = dao.insertReturning(DownloadEntry(fileName = "c", url = "https://c", createdAt = 3, state = "DONE"))

        assertEquals(1, dao.markRunningIfLive(live))
        assertEquals("RUNNING", dao.getById(live)?.state)

        assertEquals(0, dao.markRunningIfLive(cancelled))
        assertEquals("CANCELLED", dao.getById(cancelled)?.state)

        assertEquals(0, dao.markRunningIfLive(done))
        assertEquals("DONE", dao.getById(done)?.state)

        assertEquals(0, dao.markRunningIfLive(999_999L))
    }
}
