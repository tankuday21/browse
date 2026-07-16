package com.udaytank.browse.data

import com.udaytank.browse.browser.zap.ZapSelector
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Store for user-hidden ("zapped") page elements (v4.0). Reads are always allowed (re-applying
 * hidden elements is a cosmetic pref); WRITES are refused in incognito — persisting a host would
 * record that the private tab visited it.
 */
class ZapRepository(
    private val dao: ZappedElementDao,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    fun observeForHost(host: String): Flow<List<ZappedElementEntity>> = dao.observeForHost(host)
    fun countForHost(host: String): Flow<Int> = dao.countForHost(host)

    suspend fun selectorsForHost(host: String): List<String> =
        withContext(io) { dao.selectorsForHost(host) }

    /**
     * Persist a zap. Returns false (no write) when incognito, when [host] is blank, or when the
     * selector fails sanitization. [now] is passed in so the repo stays deterministic/testable.
     */
    suspend fun add(host: String, selector: String, label: String, incognito: Boolean, now: Long): Boolean {
        if (incognito || host.isBlank()) return false
        val clean = ZapSelector.sanitize(selector) ?: return false
        withContext(io) {
            dao.insert(
                ZappedElementEntity(
                    host = host,
                    selector = clean,
                    label = label.trim().take(120).ifBlank { clean.take(60) },
                    createdAt = now,
                ),
            )
        }
        return true
    }

    suspend fun remove(id: Long) = withContext(io) { dao.deleteById(id) }
    suspend fun clearForHost(host: String) = withContext(io) { dao.deleteForHost(host) }

    /** Forget every hidden element across all sites (Black Hole panic-wipe). */
    suspend fun clearAll() = withContext(io) { dao.clearAll() }
}
