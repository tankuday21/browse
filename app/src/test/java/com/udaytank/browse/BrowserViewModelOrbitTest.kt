package com.udaytank.browse

import com.udaytank.browse.data.HistoryEntry
import com.udaytank.browse.data.OrbitRepository
import com.udaytank.browse.reading.ArticleStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
        bookmarkDao: FakeBookmarkDao = FakeBookmarkDao(),
        homeShortcutDao: FakeHomeShortcutDao = FakeHomeShortcutDao(),
        downloadDao: FakeDownloadDao = FakeDownloadDao(),
        closedTabDao: FakeClosedTabDao = FakeClosedTabDao(),
        tabGroupDao: FakeTabGroupDao = FakeTabGroupDao(),
        readingListDao: FakeReadingListDao = FakeReadingListDao(),
        siteSettingsDao: FakeSiteSettingsDao = FakeSiteSettingsDao(),
        articleStore: ArticleStore = ArticleStore(createTempDirectory("reading").toFile()),
        credentialRepository: com.udaytank.browse.data.CredentialRepository =
            com.udaytank.browse.data.CredentialRepository(
                FakeCredentialDao(), FakeCredentialCipher(), io = Dispatchers.Unconfined,
            ),
    ) = BrowserViewModel(
        historyDao,
        bookmarkDao,
        tabDao,
        settings,
        downloadDao,
        closedTabDao,
        tabGroupDao,
        readingListDao,
        articleStore,
        siteSettingsDao,
        homeShortcutDao,
        RecordingDownloadController(),
        ioDispatcher = Dispatchers.Unconfined,
        orbitRepository = orbitRepository,
        credentialRepository = credentialRepository,
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
    fun `a bookmark and shortcut are created against the active orbit`() = runTest {
        val bookmarks = FakeBookmarkDao()
        val shortcuts = FakeHomeShortcutDao()
        val vm = vm(bookmarkDao = bookmarks, homeShortcutDao = shortcuts)
        advanceUntilIdle()
        val personalId = vm.activeOrbitId.value
        val tabId = vm.activeTabId.value!!
        vm.onPageStarted(tabId, "https://p.com")
        advanceUntilIdle()

        vm.onToggleBookmark()
        vm.onAddShortcut("https://p-tile.com", "P")
        advanceUntilIdle()

        assertEquals(personalId, bookmarks.bookmarks.value.single { it.url == "https://p.com" }.orbitId)
        assertEquals(personalId, shortcuts.shortcuts.value.single { it.url == "https://p-tile.com" }.orbitId)

        // Switching to a new Orbit shows none of Personal's saved data.
        vm.onCreateOrbit("Work", 0x1)
        advanceUntilIdle()
        val work = vm.orbits.value.first { it.name == "Work" }
        vm.onSwitchOrbit(work.id)
        advanceUntilIdle()
        assertTrue(bookmarks.bookmarks.value.none { it.orbitId == work.id })
        assertTrue(shortcuts.shortcuts.value.none { it.orbitId == work.id })
    }

    @Test
    fun `deleting an orbit purges its bookmarks and shortcuts`() = runTest {
        val bookmarks = FakeBookmarkDao()
        val shortcuts = FakeHomeShortcutDao()
        val vm = vm(bookmarkDao = bookmarks, homeShortcutDao = shortcuts)
        advanceUntilIdle()
        val personalId = vm.activeOrbitId.value
        bookmarks.bookmarks.value = listOf(
            com.udaytank.browse.data.Bookmark(url = "https://keep.com", title = "K", createdAt = 1, orbitId = personalId)
        )

        vm.onCreateOrbit("Work", 0x1)
        advanceUntilIdle()
        val work = vm.orbits.value.first { it.name == "Work" }
        vm.onSwitchOrbit(work.id)
        advanceUntilIdle()
        val workTabId = vm.tabs.value.first { it.orbitId == work.id }.id
        vm.onPageStarted(workTabId, "https://work.com")
        advanceUntilIdle()
        vm.onToggleBookmark()
        vm.onAddShortcut("https://work-tile.com", "W")
        advanceUntilIdle()
        assertTrue(bookmarks.bookmarks.value.any { it.orbitId == work.id })
        assertTrue(shortcuts.shortcuts.value.any { it.orbitId == work.id })

        vm.onDeleteOrbit(work.id)
        advanceUntilIdle()

        assertTrue(bookmarks.bookmarks.value.none { it.orbitId == work.id })
        assertTrue(shortcuts.shortcuts.value.none { it.orbitId == work.id })
        assertTrue(bookmarks.bookmarks.value.any { it.url == "https://keep.com" })
    }

    @Test
    fun `onBlackHole wipes every store and file, resets trace prefs, and signals all profile keys`() = runTest {
        val history = FakeHistoryDao()
        val bookmarks = FakeBookmarkDao()
        val shortcuts = FakeHomeShortcutDao()
        val downloads = FakeDownloadDao()
        val closedTabs = FakeClosedTabDao()
        val tabGroups = FakeTabGroupDao()
        val readingList = FakeReadingListDao()
        val siteSettings = FakeSiteSettingsDao()
        val settings = FakeSettingsRepository()
        val articleStore = ArticleStore(createTempDirectory("reading").toFile())
        val vm = vm(
            historyDao = history, bookmarkDao = bookmarks, homeShortcutDao = shortcuts,
            downloadDao = downloads, closedTabDao = closedTabs, tabGroupDao = tabGroups,
            readingListDao = readingList, siteSettingsDao = siteSettings, settings = settings,
            articleStore = articleStore,
        )
        advanceUntilIdle()
        val personal = vm.orbits.value.single()

        // A real on-disk download file + a saved article file, to prove file deletion.
        val downloadFile = java.io.File.createTempFile("blackhole", ".bin").apply { writeText("secret") }
        val articlePath = articleStore.save(1, "<p>secret</p>")
        assertTrue(downloadFile.exists())
        assertNotNull(articleStore.load(articlePath))

        // Seed every wired store.
        history.entries.value = listOf(HistoryEntry(1, "https://a.com", "A", 1, personal.id))
        bookmarks.bookmarks.value = listOf(
            com.udaytank.browse.data.Bookmark(url = "https://b.com", title = "B", createdAt = 1, orbitId = personal.id)
        )
        shortcuts.shortcuts.value = listOf(
            com.udaytank.browse.data.HomeShortcutEntity(url = "https://c.com", title = "C", position = 0, orbitId = personal.id)
        )
        downloads.insert(
            com.udaytank.browse.data.DownloadEntry(fileName = "f.bin", url = "https://d.com/f", createdAt = 1, filePath = downloadFile.absolutePath)
        )
        closedTabs.insert(com.udaytank.browse.data.ClosedTabEntity(url = "https://e.com", title = "E", closedAt = 1))
        tabGroups.insert(com.udaytank.browse.data.TabGroupEntity(name = "G", color = 1, position = 0))
        readingList.insert(com.udaytank.browse.data.ReadingListEntry(url = "https://f.com", title = "F", addedAt = 1))
        siteSettings.upsert(com.udaytank.browse.data.SiteSettingsEntity(host = "g.com"))
        settings.setWeatherCity("Mumbai")
        settings.addBlockedCount(500)
        settings.toggleAdAllowedSite("leak.com")

        val emitted = mutableListOf<List<String>>()
        val job = launch { vm.blackHoleReady.collect { emitted.add(it) } }
        advanceUntilIdle()

        vm.onBlackHole()
        advanceUntilIdle()

        // Every Room store emptied.
        assertTrue(history.entries.value.isEmpty())
        assertTrue(bookmarks.bookmarks.value.isEmpty())
        assertTrue(shortcuts.shortcuts.value.isEmpty())
        assertTrue(downloads.entries.value.isEmpty())
        assertTrue(closedTabs.entries.value.isEmpty())
        assertTrue(tabGroups.groups.value.isEmpty())
        assertTrue(readingList.entries.value.isEmpty())
        assertTrue(siteSettings.entries.value.isEmpty())
        assertTrue(vm.orbits.value.isEmpty())

        // On-disk files gone.
        assertTrue(!downloadFile.exists())
        assertNull(articleStore.load(articlePath))

        // Trace prefs reset.
        assertEquals(0L, settings.activeOrbitId.value)
        assertEquals("", settings.weatherCity.value)
        assertEquals(0L, settings.lifetimeBlocked.value)
        assertTrue(settings.adAllowedSites.value.isEmpty())

        // The teardown signal carries the (former) Orbit's profile key + the incognito profile.
        assertEquals(1, emitted.size)
        assertTrue(emitted.single().contains(personal.profileKey))
        assertTrue(emitted.single().contains(BrowserViewModel.INCOGNITO_PROFILE_KEY))
        job.cancel()
        downloadFile.delete()
    }

    @Test
    fun `password save is orbit-scoped, gated in incognito, and fill emits the decrypted login`() = runTest {
        val credDao = FakeCredentialDao()
        val repo = com.udaytank.browse.data.CredentialRepository(
            credDao, FakeCredentialCipher(), io = Dispatchers.Unconfined,
        )
        val vm = vm(credentialRepository = repo)
        advanceUntilIdle()
        val personal = vm.orbits.value.single()
        val tabId = vm.activeTabId.value!!

        // The tab must be on an HTTPS page (capture/fill are HTTPS-only).
        vm.onPageStarted(tabId, "https://example.com/login")
        advanceUntilIdle()

        // Submit a login on the active (non-incognito) tab → save prompt, then store.
        vm.onLoginSubmitted(tabId, "example.com", "alice", "s3cret")
        advanceUntilIdle()
        assertNotNull(vm.saveCredentialPrompt.value)
        vm.onSaveCredential()
        advanceUntilIdle()
        assertNull(vm.saveCredentialPrompt.value)
        assertEquals(1, credDao.items.value.size)
        assertEquals(personal.id, credDao.items.value.single().orbitId)
        assertEquals("alice", credDao.items.value.single().username)

        // Finishing a page on that host offers to fill; tapping emits the decrypted credential.
        val filled = mutableListOf<BrowserViewModel.FillCredentialAction>()
        val job = launch { vm.fillCredentialRequest.collect { filled.add(it) } }
        advanceUntilIdle()
        vm.onPageFinished(tabId, "https://example.com/login", "Login")
        advanceUntilIdle()
        assertEquals(listOf("alice"), vm.fillPrompt.value?.usernames)
        vm.onFillCredential(tabId, personal.id, "example.com", "alice")
        advanceUntilIdle()
        assertEquals(1, filled.size)
        assertEquals("alice", filled.single().username)
        assertEquals("s3cret", filled.single().password)
        job.cancel()

        // Incognito never prompts to save.
        vm.onNewIncognitoTab()
        advanceUntilIdle()
        val incId = vm.activeTabId.value!!
        assertTrue(incId < 0)
        vm.onLoginSubmitted(incId, "example.com", "bob", "nope")
        advanceUntilIdle()
        assertNull(vm.saveCredentialPrompt.value)
    }

    @Test
    fun `login captured on a mixed-case host still offers to fill on the canonical host`() = runTest {
        val credDao = FakeCredentialDao()
        val repo = com.udaytank.browse.data.CredentialRepository(
            credDao, FakeCredentialCipher(), io = Dispatchers.Unconfined,
        )
        val vm = vm(credentialRepository = repo)
        advanceUntilIdle()
        val tabId = vm.activeTabId.value!!

        // Capture on a MIXED-CASE host — the save must normalize via UrlHosts so fill can find it.
        vm.onPageStarted(tabId, "https://EXAMPLE.com/login"); advanceUntilIdle()
        vm.onLoginSubmitted(tabId, "EXAMPLE.com", "alice", "pw"); advanceUntilIdle()
        vm.onSaveCredential(); advanceUntilIdle()
        assertEquals("example.com", credDao.items.value.single().host)

        // Fill offer appears on the canonical lowercase host.
        vm.onPageFinished(tabId, "https://example.com/", "Home"); advanceUntilIdle()
        assertEquals(listOf("alice"), vm.fillPrompt.value?.usernames)
    }

    @Test
    fun `deleting an orbit and Black Hole both purge credentials`() = runTest {
        val credDao = FakeCredentialDao()
        val repo = com.udaytank.browse.data.CredentialRepository(
            credDao, FakeCredentialCipher(), io = Dispatchers.Unconfined,
        )
        val vm = vm(credentialRepository = repo)
        advanceUntilIdle()
        val personalTab = vm.activeTabId.value!!
        vm.onPageStarted(personalTab, "https://keep.com/login"); advanceUntilIdle()
        vm.onLoginSubmitted(personalTab, "keep.com", "me", "pw"); advanceUntilIdle()
        vm.onSaveCredential(); advanceUntilIdle()

        vm.onCreateOrbit("Work", 0x1); advanceUntilIdle()
        val work = vm.orbits.value.first { it.name == "Work" }
        vm.onSwitchOrbit(work.id); advanceUntilIdle()
        val workTab = vm.tabs.value.first { it.orbitId == work.id }.id
        vm.onPageStarted(workTab, "https://work.com/login"); advanceUntilIdle()
        vm.onLoginSubmitted(workTab, "work.com", "me", "pw"); advanceUntilIdle()
        vm.onSaveCredential(); advanceUntilIdle()
        assertEquals(2, credDao.items.value.size)

        // Deleting Work purges only its credential.
        vm.onDeleteOrbit(work.id); advanceUntilIdle()
        assertTrue(credDao.items.value.none { it.orbitId == work.id })
        assertEquals(1, credDao.items.value.size)

        // Black Hole purges everything.
        vm.onBlackHole(); advanceUntilIdle()
        assertTrue(credDao.items.value.isEmpty())
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
