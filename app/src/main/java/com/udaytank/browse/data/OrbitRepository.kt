package com.udaytank.browse.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class OrbitRepository(
    private val dao: OrbitDao,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    fun observeAll(): Flow<List<OrbitEntity>> = dao.observeAll()
    suspend fun get(id: Long): OrbitEntity? = withContext(io) { dao.getById(id) }

    suspend fun ensureDefault(now: Long): OrbitEntity = withContext(io) {
        dao.getAll().firstOrNull() ?: create("Personal", BrowseDatabase.DEFAULT_ORBIT_COLOR, now)
    }

    suspend fun create(name: String, colorArgb: Int, now: Long): OrbitEntity = withContext(io) {
        val count = dao.count()
        val key = "orbit_${now}_$count"
        val row = OrbitEntity(
            name = name.trim().ifBlank { "Orbit" }.take(30),
            colorArgb = colorArgb,
            position = count,
            profileKey = key,
        )
        row.copy(id = dao.insert(row))
    }

    suspend fun rename(id: Long, name: String) = withContext(io) {
        dao.getById(id)?.let { dao.update(it.copy(name = name.trim().ifBlank { it.name }.take(30))) }
    }

    suspend fun setColor(id: Long, colorArgb: Int) = withContext(io) {
        dao.getById(id)?.let { dao.update(it.copy(colorArgb = colorArgb)) }
    }

    /** Returns false (no-op) if this is the last Orbit — at least one must always remain. */
    suspend fun delete(id: Long): Boolean = withContext(io) {
        if (dao.count() <= 1) return@withContext false
        dao.deleteById(id)
        true
    }
}
