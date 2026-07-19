package com.udaytank.browse

import com.udaytank.browse.data.TabDao
import com.udaytank.browse.data.TabEntity

class FakeTabDao : TabDao {
    val stored = mutableListOf<TabEntity>()
    private var nextId = 1L

    override suspend fun getAll(): List<TabEntity> = stored.sortedBy { it.position }

    override suspend fun insert(tab: TabEntity): Long {
        // Mirror Room/SQLite: an explicit non-zero id is honored (v5.6 pre-allocated ids) and
        // advances the sequence; id == 0 auto-assigns. Duplicates THROW like Room's default
        // OnConflictStrategy.ABORT would — an allocator/seeding regression must fail tests.
        val id = if (tab.id != 0L) tab.id else nextId++
        require(stored.none { it.id == id }) { "duplicate tab id $id (Room ABORT would throw)" }
        if (id >= nextId) nextId = id + 1
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

    override suspend fun setGroup(id: Long, groupId: Long?) {
        stored.replaceAll { if (it.id == id) it.copy(groupId = groupId) else it }
    }

    override suspend fun setPinned(id: Long, pinned: Boolean) {
        stored.replaceAll { if (it.id == id) it.copy(pinned = pinned) else it }
    }

    override suspend fun setLocked(id: Long, locked: Boolean) {
        stored.replaceAll { if (it.id == id) it.copy(locked = locked) else it }
    }

    override suspend fun clearGroup(groupId: Long) {
        stored.replaceAll { if (it.groupId == groupId) it.copy(groupId = null) else it }
    }

    override suspend fun clearAll() {
        stored.clear()
    }
}
