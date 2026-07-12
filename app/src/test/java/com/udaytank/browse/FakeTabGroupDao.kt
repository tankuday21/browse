package com.udaytank.browse

import com.udaytank.browse.data.TabGroupDao
import com.udaytank.browse.data.TabGroupEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeTabGroupDao : TabGroupDao {
    val groups = MutableStateFlow<List<TabGroupEntity>>(emptyList())
    private var nextId = 1L

    override suspend fun insert(group: TabGroupEntity): Long {
        val id = nextId++
        groups.value = groups.value + group.copy(id = id)
        return id
    }

    override suspend fun rename(id: Long, name: String) {
        groups.value = groups.value.map { if (it.id == id) it.copy(name = name) else it }
    }

    override suspend fun deleteById(id: Long) {
        groups.value = groups.value.filterNot { it.id == id }
    }

    override fun observeAll(): Flow<List<TabGroupEntity>> = groups

    override suspend fun getAll(): List<TabGroupEntity> = groups.value
}
