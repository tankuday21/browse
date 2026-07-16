package com.udaytank.browse

import com.udaytank.browse.data.OrbitRepository
import com.udaytank.browse.reading.ArticleStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.io.path.createTempDirectory

class BrowserViewModelOrbitTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun vm(
        tabDao: FakeTabDao = FakeTabDao(),
        settings: FakeSettingsRepository = FakeSettingsRepository(),
        orbitRepository: OrbitRepository = OrbitRepository(FakeOrbitDao(), io = Dispatchers.Unconfined),
        historyDao: FakeHistoryDao = FakeHistoryDao(),
    ) = BrowserViewModel(
        historyDao,
        FakeBookmarkDao(),
        tabDao,
        settings,
        FakeDownloadDao(),
        FakeClosedTabDao(),
        FakeTabGroupDao(),
        FakeReadingListDao(),
        ArticleStore(createTempDirectory("reading").toFile()),
        FakeSiteSettingsDao(),
        FakeHomeShortcutDao(),
        RecordingDownloadController(),
        ioDispatcher = Dispatchers.Unconfined,
        orbitRepository = orbitRepository,
    )

    @Test
    fun `onCreateOrbit adds an orbit and it appears in orbits flow`() = runTest {
        val vm = vm()
        advanceUntilIdle()
        val before = vm.orbits.value.size

        vm.onCreateOrbit("Work", 0x11223344)
        advanceUntilIdle()

        assertEquals(before + 1, vm.orbits.value.size)
        assertTrue(vm.orbits.value.any { it.name == "Work" })
    }

    @Test
    fun `onSwitchOrbit updates activeOrbitId`() = runTest {
        val vm = vm()
        advanceUntilIdle()

        vm.onCreateOrbit("Work", 0x1)
        advanceUntilIdle()
        val work = vm.orbits.value.first { it.name == "Work" }

        vm.onSwitchOrbit(work.id)
        advanceUntilIdle()

        assertEquals(work.id, vm.activeOrbitId.value)
    }

    @Test
    fun `new non-incognito tab inherits the active orbit`() = runTest {
        val vm = vm()
        advanceUntilIdle()
        val activeId = vm.activeOrbitId.value

        vm.onNewTab()
        advanceUntilIdle()

        val newTab = vm.tabs.value.last()
        assertEquals(activeId, newTab.orbitId)
    }

    @Test
    fun `onDeleteOrbit closes that orbit's tabs and switches active away if it was active`() = runTest {
        val vm = vm()
        advanceUntilIdle()
        val personalId = vm.activeOrbitId.value

        vm.onCreateOrbit("Work", 0x1)
        advanceUntilIdle()
        val work = vm.orbits.value.first { it.name == "Work" }

        // Switching to an orbit with no tabs yet opens one in it (asserted implicitly below).
        vm.onSwitchOrbit(work.id)
        advanceUntilIdle()
        assertTrue(vm.tabs.value.any { it.orbitId == work.id })

        vm.onDeleteOrbit(work.id)
        advanceUntilIdle()

        assertTrue(vm.tabs.value.none { it.orbitId == work.id })
        assertTrue(vm.orbits.value.none { it.id == work.id })
        assertEquals(personalId, vm.activeOrbitId.value)
    }

    @Test
    fun `a page finishing is recorded against its own tab's orbit, not the active one`() = runTest {
        // The headline Phase 2 invariant: a late/background onPageFinished for a tab that lives in
        // Orbit A must land in A's history even if the user has since switched active to Orbit B.
        val history = FakeHistoryDao()
        val vm = vm(historyDao = history)
        advanceUntilIdle()
        val personalId = vm.activeOrbitId.value
        val personalTabId = vm.tabs.value.single().id

        vm.onCreateOrbit("Work", 0x1)
        advanceUntilIdle()
        val work = vm.orbits.value.first { it.name == "Work" }
        vm.onSwitchOrbit(work.id) // active is now Work; the Personal tab still belongs to Personal
        advanceUntilIdle()
        assertEquals(work.id, vm.activeOrbitId.value)

        // Fire the Personal tab's page-finish while Work is active.
        vm.onPageFinished(personalTabId, "https://personal-only.com", "Personal Only")
        advanceUntilIdle()

        val recorded = history.entries.value.single { it.url == "https://personal-only.com" }
        assertEquals(personalId, recorded.orbitId)
        // And it must be invisible to Work: isolation holds across the switch.
        assertTrue(history.entries.value.none { it.orbitId == work.id })
    }

    @Test
    fun `deleting an orbit purges its history rows`() = runTest {
        val history = FakeHistoryDao()
        val vm = vm(historyDao = history)
        advanceUntilIdle()
        val personalId = vm.activeOrbitId.value
        val personalTabId = vm.tabs.value.single().id
        vm.onPageFinished(personalTabId, "https://keep.com", "Keep")
        advanceUntilIdle()

        vm.onCreateOrbit("Work", 0x1)
        advanceUntilIdle()
        val work = vm.orbits.value.first { it.name == "Work" }
        vm.onSwitchOrbit(work.id)
        advanceUntilIdle()
        val workTabId = vm.tabs.value.first { it.orbitId == work.id }.id
        vm.onPageFinished(workTabId, "https://work-secret.com", "Work Secret")
        advanceUntilIdle()
        assertTrue(history.entries.value.any { it.orbitId == work.id })

        vm.onDeleteOrbit(work.id)
        advanceUntilIdle()

        // Work's history is gone; Personal's survives.
        assertTrue(history.entries.value.none { it.orbitId == work.id })
        assertTrue(history.entries.value.any { it.orbitId == personalId && it.url == "https://keep.com" })
    }

    @Test
    fun `first launch resolves activeOrbitId to the seeded Personal orbit (ensureDefault)`() = runTest {
        val vm = vm()
        advanceUntilIdle()

        assertEquals(1, vm.orbits.value.size)
        assertEquals("Personal", vm.orbits.value.first().name)
        assertEquals(vm.orbits.value.first().id, vm.activeOrbitId.value)
    }

    @Test
    fun `profileKeyForTab resolves via the tab's orbit`() = runTest {
        val vm = vm()
        advanceUntilIdle()
        val personal = vm.orbits.value.first()

        vm.onNewTab()
        advanceUntilIdle()
        val tabId = vm.tabs.value.last().id

        assertEquals(personal.profileKey, vm.profileKeyForTab(tabId))
    }

    @Test
    fun `onDeleteOrbit emits the deleted orbit's profileKey and tab ids after tabs are closed`() = runTest {
        val vm = vm()
        advanceUntilIdle()

        vm.onCreateOrbit("Work", 0x1)
        advanceUntilIdle()
        val work = vm.orbits.value.first { it.name == "Work" }

        // Switching to Work (no tabs yet) opens one there — this is the tab whose WebView
        // MainActivity must destroy before it can delete Work's profile.
        vm.onSwitchOrbit(work.id)
        advanceUntilIdle()
        val workTabIds = vm.tabs.value.filter { it.orbitId == work.id }.map { it.id }
        assertTrue(workTabIds.isNotEmpty())

        val emitted = mutableListOf<BrowserViewModel.OrbitDeletion>()
        val job = launch { vm.orbitProfileToDelete.collect { emitted.add(it) } }
        advanceUntilIdle()

        vm.onDeleteOrbit(work.id)
        advanceUntilIdle()

        assertEquals(1, emitted.size)
        assertEquals(work.profileKey, emitted.single().profileKey)
        assertEquals(workTabIds, emitted.single().tabIds)
        // The emission must land after the deleted orbit's tabs are actually gone (ordering).
        assertTrue(vm.tabs.value.none { it.orbitId == work.id })
        job.cancel()
    }

    @Test
    fun `onDeleteOrbit refuses to remove the last orbit`() = runTest {
        val vm = vm()
        advanceUntilIdle()
        val personal = vm.orbits.value.first()

        vm.onDeleteOrbit(personal.id)
        advanceUntilIdle()

        assertEquals(1, vm.orbits.value.size)
        assertNotNull(vm.orbits.value.find { it.id == personal.id })
    }

    @Test
    fun `deleting an orbit that holds every open tab leaves the new active orbit with a tab, no orphans`() = runTest {
        val vm = vm()
        advanceUntilIdle()
        val personalId = vm.activeOrbitId.value
        val personalTab = vm.tabs.value.first()

        vm.onCreateOrbit("Work", 0x1)
        advanceUntilIdle()
        val work = vm.orbits.value.first { it.name == "Work" }

        // Switching to Work (no tabs yet) opens one there.
        vm.onSwitchOrbit(work.id)
        advanceUntilIdle()

        // Close the Personal tab so Work now holds EVERY open tab.
        vm.onCloseTab(personalTab.id)
        advanceUntilIdle()
        assertTrue(vm.tabs.value.isNotEmpty())
        assertTrue(vm.tabs.value.all { it.orbitId == work.id })

        // Deleting Work must not let TabManager's empty-list fallback create a null-orbit tab,
        // and must leave the new active Orbit (Personal) with at least one visible tab.
        vm.onDeleteOrbit(work.id)
        advanceUntilIdle()

        assertEquals(personalId, vm.activeOrbitId.value)
        assertTrue(vm.tabs.value.any { it.orbitId == personalId })
        assertTrue(vm.tabs.value.none { it.orbitId == work.id })
        assertTrue(vm.tabs.value.none { !it.isIncognito && it.orbitId == null })
    }

    @Test
    fun `onDeleteOrbit with 3 orbits switches the active tab to the surviving active orbit, not whatever the close policy picks`() = runTest {
        val vm = vm()
        advanceUntilIdle()
        val personal = vm.orbits.value.first { it.name == "Personal" }
        // Personal already has a tab (position 0) from first-launch seeding.

        vm.onCreateOrbit("Play", 0x1)
        advanceUntilIdle()
        val play = vm.orbits.value.first { it.name == "Play" }
        vm.onSwitchOrbit(play.id) // creates Play's tab at position 1, makes Play active.
        advanceUntilIdle()

        vm.onCreateOrbit("Work", 0x2)
        advanceUntilIdle()
        val work = vm.orbits.value.first { it.name == "Work" }
        vm.onSwitchOrbit(work.id) // creates Work's tab at position 2, makes Work active.
        advanceUntilIdle()
        assertEquals(work.id, vm.activeOrbitId.value)

        // Delete the active orbit (Work). TabClosePolicy is position-based and Orbit-unaware:
        // absent the fix, closing Work's tab would hand the active tab to Play (the next
        // position-wise), not to Personal (the new active orbit).
        vm.onDeleteOrbit(work.id)
        advanceUntilIdle()

        // Personal is the first surviving orbit (created first, excluding the deleted Work).
        assertEquals(personal.id, vm.activeOrbitId.value)

        val activeTab = vm.tabs.value.first { it.id == vm.activeTabId.value }
        assertEquals(personal.id, activeTab.orbitId)
        assertTrue(vm.tabs.value.none { !it.isIncognito && it.orbitId == null })
    }

    @Test
    fun `fresh install init assigns the first tab to the resolved active orbit, never null`() = runTest {
        val vm = vm()
        advanceUntilIdle()

        val resolvedActiveOrbitId = vm.activeOrbitId.value
        val initialTab = vm.tabs.value.single()

        assertNotNull(initialTab.orbitId)
        assertEquals(resolvedActiveOrbitId, initialTab.orbitId)
    }

    @Test
    fun `closing the last tab in the active orbit assigns the auto-created home tab to that orbit, never null`() = runTest {
        val vm = vm()
        advanceUntilIdle()
        val activeId = vm.activeOrbitId.value
        val onlyTab = vm.tabs.value.single()
        assertEquals(activeId, onlyTab.orbitId)

        vm.onCloseTab(onlyTab.id)
        advanceUntilIdle()

        val replacementTab = vm.tabs.value.single()
        assertEquals(activeId, vm.activeOrbitId.value)
        assertNotNull(replacementTab.orbitId)
        assertEquals(activeId, replacementTab.orbitId)
        assertTrue(vm.tabs.value.none { !it.isIncognito && it.orbitId == null })
    }
}
