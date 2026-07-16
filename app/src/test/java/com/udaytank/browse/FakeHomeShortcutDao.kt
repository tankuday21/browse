package com.udaytank.browse

import com.udaytank.browse.data.HomeShortcutDao
import com.udaytank.browse.data.HomeShortcutEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeHomeShortcutDao : HomeShortcutDao {
    val shortcuts = MutableStateFlow<List<HomeShortcutEntity>>(emptyList())
    private var nextId = 1L

    override fun observeForOrbit(orbitId: Long): Flow<List<HomeShortcutEntity>> =
        shortcuts.map { list -> list.filter { it.orbitId == orbitId }.sortedBy { it.position } }

    override suspend fun getAllForOrbit(orbitId: Long): List<HomeShortcutEntity> =
        shortcuts.value.filter { it.orbitId == orbitId }.sortedBy { it.position }

    override suspend fun getAll(): List<HomeShortcutEntity> =
        shortcuts.value.sortedBy { it.position }

    override suspend fun insert(shortcut: HomeShortcutEntity): Long {
        val id = nextId++
        shortcuts.value = shortcuts.value + shortcut.copy(id = id)
        return id
    }

    override suspend fun insertAll(shortcuts: List<HomeShortcutEntity>) {
        shortcuts.forEach { insert(it) }
    }

    override suspend fun deleteById(id: Long) {
        shortcuts.value = shortcuts.value.filterNot { it.id == id }
    }

    override suspend fun deleteForOrbit(orbitId: Long) {
        shortcuts.value = shortcuts.value.filterNot { it.orbitId == orbitId }
    }

    // replaceAllForOrbit comes from the interface's default @Transaction implementation.
}
