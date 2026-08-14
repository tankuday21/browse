package com.udaytank.browse

import com.udaytank.browse.data.ClosedTabDao
import com.udaytank.browse.data.ClosedTabEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeClosedTabDao : ClosedTabDao {
    val entries = MutableStateFlow<List<ClosedTabEntity>>(emptyList())
    private var nextId = 1L

    override suspend fun insert(entry: ClosedTabEntity) {
        entries.value = entries.value + entry.copy(id = nextId++)
    }

    // Mirrors the real SQL: the trim is PER ORBIT, and rows of other Orbits are untouched.
    override suspend fun trimTo(orbitId: Long, max: Int) {
        val mine = entries.value.filter { it.orbitId == orbitId }
        val keep = mine
            .sortedWith(compareByDescending<ClosedTabEntity> { it.closedAt }.thenByDescending { it.id })
            .take(max)
            .map { it.id }
            .toSet()
        entries.value = entries.value.filter { it.orbitId != orbitId || it.id in keep }
    }

    // `WHERE orbitId = :orbitId` never matches a NULL row in SQL, so the fake must not either.
    override fun observeRecent(orbitId: Long, limit: Int): Flow<List<ClosedTabEntity>> =
        entries.map { list ->
            list.filter { it.orbitId == orbitId }
                .sortedWith(compareByDescending<ClosedTabEntity> { it.closedAt }.thenByDescending { it.id })
                .take(limit)
        }

    override suspend fun deleteForOrbit(orbitId: Long) {
        entries.value = entries.value.filterNot { it.orbitId == orbitId }
    }

    override suspend fun deleteById(id: Long) {
        entries.value = entries.value.filterNot { it.id == id }
    }

    override suspend fun clear() { entries.value = emptyList() }
}
