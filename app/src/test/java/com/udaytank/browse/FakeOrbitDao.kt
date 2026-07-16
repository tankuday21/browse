package com.udaytank.browse

import com.udaytank.browse.data.OrbitDao
import com.udaytank.browse.data.OrbitEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeOrbitDao : OrbitDao {
    val orbits = MutableStateFlow<List<OrbitEntity>>(emptyList())
    private var nextId = 1L

    override fun observeAll(): Flow<List<OrbitEntity>> = orbits

    override suspend fun getAll(): List<OrbitEntity> =
        orbits.value.sortedWith(compareBy({ it.position }, { it.id }))

    override suspend fun getById(id: Long): OrbitEntity? = orbits.value.find { it.id == id }

    override suspend fun count(): Int = orbits.value.size

    override suspend fun insert(orbit: OrbitEntity): Long {
        val id = nextId++
        orbits.value = orbits.value + orbit.copy(id = id)
        return id
    }

    override suspend fun update(orbit: OrbitEntity) {
        orbits.value = orbits.value.map { if (it.id == orbit.id) orbit else it }
    }

    override suspend fun deleteById(id: Long) {
        orbits.value = orbits.value.filterNot { it.id == id }
    }

    override suspend fun clearAll() {
        orbits.value = emptyList()
    }
}
