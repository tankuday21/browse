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

    override suspend fun trimTo(max: Int) {
        entries.value = entries.value
            .sortedWith(compareByDescending<ClosedTabEntity> { it.closedAt }.thenByDescending { it.id })
            .take(max)
    }

    override fun observeRecent(limit: Int): Flow<List<ClosedTabEntity>> =
        entries.map { list ->
            list.sortedWith(compareByDescending<ClosedTabEntity> { it.closedAt }.thenByDescending { it.id })
                .take(limit)
        }

    override suspend fun deleteById(id: Long) {
        entries.value = entries.value.filterNot { it.id == id }
    }

    override suspend fun clear() { entries.value = emptyList() }
}
