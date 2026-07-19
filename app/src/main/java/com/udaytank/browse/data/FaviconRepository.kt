package com.udaytank.browse.data

import android.graphics.Bitmap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Cache of site icons captured from the WebView as you browse (v4.1). Reads are a Flow of the
 * whole table (small). Writes come from the WebView's icon callbacks and are ALREADY incognito-
 * guarded by the caller — a private tab never reaches here.
 *
 * A declared touch-icon URL always wins over a decoded favicon bitmap (higher resolution), and we
 * never downgrade an existing URL entry to a bitmap.
 */
class FaviconRepository(
    private val dao: FaviconDao,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    fun observeAll(): Flow<List<FaviconEntity>> = dao.observeAll()

    /** One host's cached entry (v5.7 launcher pins use the bytes for the shortcut icon). */
    suspend fun get(host: String): FaviconEntity? = withContext(io) { dao.get(host) }

    /** Drop the entire icon cache (Black Hole panic-wipe). */
    suspend fun clearAll() = withContext(io) { dao.clearAll() }

    /** Preferred, high-res: the apple-touch-icon URL the page declared. */
    suspend fun saveTouchIcon(host: String, url: String, now: Long) {
        if (host.isBlank() || url.isBlank()) return
        withContext(io) {
            val existing = dao.get(host)
            if (existing?.iconUrl == url) return@withContext // unchanged, skip the write
            dao.upsert(FaviconEntity(host = host, iconUrl = url, iconBytes = null, updatedAt = now))
        }
    }

    /** Fallback: the decoded favicon bitmap, stored only if we don't already have a better URL. */
    suspend fun saveBitmap(host: String, bitmap: Bitmap, now: Long) {
        if (host.isBlank()) return
        withContext(io) {
            val existing = dao.get(host)
            if (existing?.iconUrl != null) return@withContext // keep the higher-res source
            val png = ByteArrayOutputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                out.toByteArray()
            }
            if (png.isEmpty()) return@withContext
            dao.upsert(FaviconEntity(host = host, iconUrl = null, iconBytes = png, updatedAt = now))
        }
    }
}
