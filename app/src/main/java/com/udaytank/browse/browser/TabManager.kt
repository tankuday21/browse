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

    /**
     * Synchronous id source for NORMAL tabs (v5.6): seeded at [initialize] to max(stored)+1;
     * every persisted tab inserts with an EXPLICIT id from here (SQLite accepts explicit
     * primary keys and advances its sequence past them). This is what lets a popup's WebView
     * be created under its REAL tab id inside the synchronous onCreateWindow callback — no
     * provisional ids, no rebinding, no closure mis-attribution.
     */
    private val nextTabId = java.util.concurrent.atomic.AtomicLong(1L)

    /**
     * Completed once [initialize] has seeded [nextTabId] past every stored id. Tab creation
     * awaits this: a cold-start external VIEW intent reaches [newTab] BEFORE initialize's DB
     * read finishes, and allocating from the unseeded counter would insert id 1 over an
     * existing row (Room ABORT → crash on every link tap from another app).
     */
    private val initialized = kotlinx.coroutines.CompletableDeferred<Unit>()

    private fun isIncognitoId(id: Long) = id < 0

    /**
     * Reserves a tab id without touching the DB — safe inside synchronous engine callbacks
     * (popup adoption). Callable pre-initialize only for incognito; normal allocation is
     * reached exclusively through [newTab]'s gate or popups (which require an initialized
     * tabs list to have a parent at all).
     */
    fun allocateTabId(incognito: Boolean): Long =
        if (incognito) nextIncognitoId-- else nextTabId.getAndIncrement()

    /**
     * The caller's (BrowserViewModel's) current notion of the active Orbit. Every fallback/
     * initial home tab TabManager auto-creates on its own initiative — [initialize]'s empty-DB
     * tab and [closeTab]'s empty-list fallback — is stamped with this id, so those tabs are never
     * left with a null Orbit (a null-orbit tab won't match any Orbit's activeOrbitId filter and
     * becomes invisible). Explicit [newTab] callers that already pass their own orbitId are
     * unaffected. Incognito is irrelevant here — these fallback tabs are always normal tabs.
     */
    var defaultOrbitId: Long? = null

    /**
     * [orbitId] is threaded into the home tab created when the DAO is empty (fresh install or
     * a wipe), so that tab is never left with a null Orbit — see [newTab]'s docs for why that
     * matters (an orbitId == null tab won't match any Orbit's activeOrbitId filter). Falls back
     * to [defaultOrbitId] when the caller doesn't pass one explicitly.
     */
    suspend fun initialize(homeUrl: String, orbitId: Long? = null) {
        val stored = tabDao.getAll()
        // Seed the synchronous allocator past every persisted id (v5.6), THEN open the gate —
        // the empty-DB newTab below goes through the gate itself, so order matters.
        nextTabId.set((stored.maxOfOrNull { it.id } ?: 0L) + 1L)
        initialized.complete(Unit)
        if (stored.isEmpty()) {
            newTab(homeUrl, orbitId = orbitId ?: defaultOrbitId)
        } else {
            _tabs.value = stored
            _activeTabId.value = (stored.find { it.isActive } ?: stored.first()).id
        }
    }

    /**
     * [orbitId] is the Orbit a new non-incognito tab should persist as belonging to. Incognito
     * tabs never carry an Orbit (they're ephemeral and isolated by construction), so [orbitId] is
     * forced to null for them regardless of what's passed in.
     *
     * [foreground] = false adds the tab WITHOUT activating it (v5.0 popups whose parent is no
     * longer the tab the user is looking at — activating a tab from another Orbit/mode would
     * break the active-tab ↔ active-Orbit invariant the tab switcher relies on).
     */
    suspend fun newTab(
        url: String,
        incognito: Boolean = false,
        groupId: Long? = null,
        orbitId: Long? = null,
        foreground: Boolean = true,
    ): Long {
        initialized.await() // cold-start gate — see [initialized]
        val id = allocateTabId(incognito)
        registerTabInMemory(id, url, incognito, groupId, orbitId, foreground)
        persistRegisteredTab(id)
        return id
    }

    /**
     * SYNCHRONOUS in-memory registration under a PRE-ALLOCATED id (v5.6 popup adoption). The
     * engine starts the popup's transport load the instant the WebView is handed over, and
     * onPageStarted/onContentChanged must FIND the tab — a suspend-then-register would drop
     * the first URL and mis-attribute history to the active Orbit. Callers follow up with
     * [persistRegisteredTab]; [newTab] composes both behind the init gate.
     */
    fun registerTabInMemory(
        id: Long,
        url: String,
        incognito: Boolean = false,
        groupId: Long? = null,
        orbitId: Long? = null,
        foreground: Boolean = true,
    ) {
        val position = (_tabs.value.maxOfOrNull { it.position } ?: -1) + 1
        val effectiveOrbitId = if (incognito) null else orbitId
        val entity = TabEntity(
            id = id, url = url, title = url, position = position, isActive = foreground,
            isIncognito = incognito, groupId = groupId, orbitId = effectiveOrbitId,
        )
        _tabs.value = if (foreground) {
            _tabs.value.map { it.copy(isActive = false) } + entity
        } else {
            _tabs.value + entity
        }
        if (foreground) _activeTabId.value = id
    }

    /**
     * Persists a tab registered via [registerTabInMemory]: inserts the row with its EXPLICIT
     * id (Room passes it through; SQLite advances its sequence past it). Incognito ids stay
     * in-memory as always; a tab already closed again is a no-op.
     */
    suspend fun persistRegisteredTab(id: Long) {
        if (isIncognitoId(id)) return
        val entity = _tabs.value.find { it.id == id } ?: return
        tabDao.insert(entity)
        if (_activeTabId.value == id) tabDao.setActive(id)
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
            _tabs.value.isEmpty() -> newTab(homeUrl, orbitId = defaultOrbitId)
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
