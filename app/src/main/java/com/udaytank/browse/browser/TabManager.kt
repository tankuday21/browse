package com.udaytank.browse.browser

import com.udaytank.browse.data.TabDao
import com.udaytank.browse.data.TabEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** In-memory authority over tabs; every mutation is written through to the DAO. */
class TabManager(private val tabDao: TabDao) {

    private val _tabs = MutableStateFlow<List<TabEntity>>(emptyList())
    val tabs: StateFlow<List<TabEntity>> = _tabs.asStateFlow()

    private val _activeTabId = MutableStateFlow<Long?>(null)
    val activeTabId: StateFlow<Long?> = _activeTabId.asStateFlow()

    suspend fun initialize(homeUrl: String) {
        val stored = tabDao.getAll()
        if (stored.isEmpty()) {
            newTab(homeUrl)
        } else {
            _tabs.value = stored
            _activeTabId.value = (stored.find { it.isActive } ?: stored.first()).id
        }
    }

    suspend fun newTab(url: String): Long {
        val position = (_tabs.value.maxOfOrNull { it.position } ?: -1) + 1
        val id = tabDao.insert(TabEntity(url = url, title = url, position = position, isActive = true))
        _tabs.value = _tabs.value.map { it.copy(isActive = false) } +
            TabEntity(id = id, url = url, title = url, position = position, isActive = true)
        _activeTabId.value = id
        tabDao.setActive(id)
        return id
    }

    suspend fun switchTo(id: Long) {
        if (_tabs.value.none { it.id == id }) return
        _tabs.value = _tabs.value.map { it.copy(isActive = it.id == id) }
        _activeTabId.value = id
        tabDao.setActive(id)
    }

    suspend fun closeTab(id: Long, homeUrl: String) {
        val next = TabClosePolicy.nextActiveId(_tabs.value, closingId = id, activeId = _activeTabId.value)
        _tabs.value = _tabs.value.filterNot { it.id == id }
        tabDao.deleteById(id)
        when {
            _tabs.value.isEmpty() -> newTab(homeUrl)
            next != null -> switchTo(next)
        }
    }

    suspend fun onContentChanged(id: Long, url: String, title: String) {
        _tabs.value = _tabs.value.map { if (it.id == id) it.copy(url = url, title = title) else it }
        tabDao.updateContent(id, url, title)
    }
}
