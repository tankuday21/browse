package com.udaytank.browse.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ZapRepositoryTest {
    private class FakeDao : ZappedElementDao {
        val items = mutableListOf<ZappedElementEntity>()
        override fun observeForHost(host: String): Flow<List<ZappedElementEntity>> =
            flowOf(items.filter { it.host == host })
        override suspend fun selectorsForHost(host: String): List<String> =
            items.filter { it.host == host }.map { it.selector }
        override fun countForHost(host: String): Flow<Int> = flowOf(items.count { it.host == host })
        override suspend fun insert(element: ZappedElementEntity): Long {
            items.add(element.copy(id = items.size + 1L)); return items.size.toLong()
        }
        override suspend fun deleteById(id: Long) { items.removeAll { it.id == id } }
        override suspend fun deleteForHost(host: String) { items.removeAll { it.host == host } }
    }

    private fun repo() = ZapRepository(FakeDao(), Dispatchers.Unconfined)

    @Test fun incognitoNeverPersists() = runTest {
        val r = repo()
        assertFalse(r.add("news.com", "div.ad", "ad", incognito = true, now = 1L))
        assertTrue(r.selectorsForHost("news.com").isEmpty())
    }

    @Test fun persistsAndSanitizesWhenNotIncognito() = runTest {
        val r = repo()
        assertTrue(r.add("news.com", "  div.ad-banner ", "Ad banner", incognito = false, now = 1L))
        assertEquals(listOf("div.ad-banner"), r.selectorsForHost("news.com"))
    }

    @Test fun rejectsUnsafeSelector() = runTest {
        val r = repo()
        assertFalse(r.add("news.com", "div<script>", "x", incognito = false, now = 1L))
        assertFalse(r.add("", "div.ad", "x", incognito = false, now = 1L))
    }
}
