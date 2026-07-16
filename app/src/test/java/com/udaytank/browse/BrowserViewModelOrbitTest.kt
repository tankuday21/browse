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
    ) = BrowserViewModel(
        FakeHistoryDao(),
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
    fun `onDeleteOrbit emits the deleted orbit's profileKey after tabs are closed`() = runTest {
        val vm = vm()
        advanceUntilIdle()

        vm.onCreateOrbit("Work", 0x1)
        advanceUntilIdle()
        val work = vm.orbits.value.first { it.name == "Work" }

        val emitted = mutableListOf<String>()
        val job = launch { vm.orbitProfileToDelete.collect { emitted.add(it) } }
        advanceUntilIdle()

        vm.onDeleteOrbit(work.id)
        advanceUntilIdle()

        assertEquals(listOf(work.profileKey), emitted)
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
