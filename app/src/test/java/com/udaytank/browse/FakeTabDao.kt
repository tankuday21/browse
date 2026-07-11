package com.udaytank.browse

import com.udaytank.browse.data.TabDao
import com.udaytank.browse.data.TabEntity

class FakeTabDao : TabDao {
    val stored = mutableListOf<TabEntity>()
    private var nextId = 1L

    override suspend fun getAll(): List<TabEntity> = stored.sortedBy { it.position }

    override suspend fun insert(tab: TabEntity): Long {
        val id = nextId++
        stored += tab.copy(id = id)
        return id
    }

    override suspend fun deleteById(id: Long) {
        stored.removeAll { it.id == id }
    }

    override suspend fun setActive(id: Long) {
        stored.replaceAll { it.copy(isActive = it.id == id) }
    }

    override suspend fun updateContent(id: Long, url: String, title: String) {
        stored.replaceAll { if (it.id == id) it.copy(url = url, title = title) else it }
    }
}
