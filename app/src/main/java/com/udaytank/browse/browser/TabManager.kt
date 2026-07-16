package com.udaytank.browse.browser

import com.udaytank.browse.data.ClosedTabDao
import com.udaytank.browse.data.ClosedTabEntity
import com.udaytank.browse.data.TabDao
import com.udaytank.browse.data.TabEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** In-memory authority over tabs; every mutation is written through to the DAO. */
class TabManager(
    private val tabDao: TabDao,
    private val closedTabDao: ClosedTabDao,
    private val now: () -> Long = System::currentTimeMillis,
) {

    private val _tabs = MutableStateFlow<List<TabEntity>>(emptyList())
    val tabs: StateFlow<List<TabEntity>> = _tabs.asStateFlow()

    private val _activeTabId = MutableStateFlow<Long?>(null)
    val activeTabId: StateFlow<Long?> = _activeTabId.asStateFlow()

    // Incognito tabs get negative ids and never touch the DAO:
    // process death erases them by construction.
    private var nextIncognitoId = -1L

    private fun isIncognitoId(id: Long) = id < 0

    suspend fun initialize(homeUrl: String) {
        val stored = tabDao.getAll()
        if (stored.isEmpty()) {
            newTab(homeUrl)
        } else {
            _tabs.value = stored
            _activeTabId.value = (stored.find { it.isActive } ?: stored.first()).id
        }
    }

    /**
     * [orbitId] is the Orbit a new non-incognito tab should persist as belonging to. Incognito
     * tabs never carry an Orbit (they're ephemeral and isolated by construction), so [orbitId] is
     * forced to null for them regardless of what's passed in.
     */
    suspend fun newTab(
        url: String,
        incognito: Boolean = false,
        groupId: Long? = null,
        orbitId: Long? = null,
    ): Long {
        val position = (_tabs.value.maxOfOrNull { it.position } ?: -1) + 1
        val effectiveOrbitId = if (incognito) null else orbitId
        val id = if (incognito) {
            nextIncognitoId--
        } else {
            tabDao.insert(
                TabEntity(
                    url = url, title = url, position = position, isActive = true,
                    groupId = groupId, orbitId = effectiveOrbitId,
                )
            )
        }
        _tabs.value = _tabs.value.map { it.copy(isActive = false) } +
            TabEntity(
                id = id, url = url, title = url, position = position, isActive = true,
                isIncognito = incognito, groupId = groupId, orbitId = effectiveOrbitId,
            )
        _activeTabId.value = id
        if (!incognito) tabDao.setActive(id)
        return id
    }

    suspend fun switchTo(id: Long) {
        if (_tabs.value.none { it.id == id }) return
        _tabs.value = _tabs.value.map { it.copy(isActive = it.id == id) }
        _activeTabId.value = id
        if (!isIncognitoId(id)) tabDao.setActive(id)
    }

    suspend fun closeTab(id: Long, homeUrl: String) {
        val next = TabClosePolicy.nextActiveId(_tabs.value, closingId = id, activeId = _activeTabId.value)
        val closing = _tabs.value.find { it.id == id }
        if (closing != null && !isIncognitoId(id)) {
            closedTabDao.insert(ClosedTabEntity(url = closing.url, title = closing.title, closedAt = now()))
            closedTabDao.trimTo(100)
        }
        _tabs.value = _tabs.value.filterNot { it.id == id }
        if (!isIncognitoId(id)) tabDao.deleteById(id)
        when {
            _tabs.value.isEmpty() -> newTab(homeUrl)
            next != null -> switchTo(next)
        }
    }

    suspend fun onContentChanged(id: Long, url: String, title: String) {
        _tabs.value = _tabs.value.map { if (it.id == id) it.copy(url = url, title = title) else it }
        if (!isIncognitoId(id)) tabDao.updateContent(id, url, title)
    }

    suspend fun setGroup(id: Long, groupId: Long?) {
        _tabs.value = _tabs.value.map { if (it.id == id) it.copy(groupId = groupId) else it }
        if (!isIncognitoId(id)) tabDao.setGroup(id, groupId)
    }

    suspend fun setPinned(id: Long, pinned: Boolean) {
        _tabs.value = _tabs.value.map { if (it.id == id) it.copy(pinned = pinned) else it }
        if (!isIncognitoId(id)) tabDao.setPinned(id, pinned)
    }

    suspend fun setLocked(id: Long, locked: Boolean) {
        _tabs.value = _tabs.value.map { if (it.id == id) it.copy(locked = locked) else it }
        if (!isIncognitoId(id)) tabDao.setLocked(id, locked)
    }
}
