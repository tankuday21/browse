package com.udaytank.browse.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * v3.1 home-customization prefs (Task 5): showGreeting / showHomeStats / shortcutDensity /
 * homeWallpaper. Exercises the real [DataStoreSettingsRepository] against a temp-file-backed
 * DataStore so the null-safe enum parsing under test is the actual production code path.
 */
class HomePrefsTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun newStore(): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        produceFile = { tempFolder.newFile("home_prefs_${System.nanoTime()}.preferences_pb") },
    )

    @Test
    fun defaultsAreFalseFalseFewAndEmptyWallpaper() = runTest {
        val repo = DataStoreSettingsRepository(newStore())

        assertEquals(false, repo.showGreeting.first())
        assertEquals(false, repo.showHomeStats.first())
        assertEquals(ShortcutDensity.FEW, repo.shortcutDensity.first())
        assertEquals("", repo.homeWallpaper.first())
    }

    @Test
    fun settersRoundTripThroughTheFlows() = runTest {
        val repo = DataStoreSettingsRepository(newStore())

        repo.setShowGreeting(true)
        repo.setShowHomeStats(true)
        repo.setShortcutDensity(ShortcutDensity.MORE)
        repo.setHomeWallpaper("aurora")

        assertEquals(true, repo.showGreeting.first())
        assertEquals(true, repo.showHomeStats.first())
        assertEquals(ShortcutDensity.MORE, repo.shortcutDensity.first())
        assertEquals("aurora", repo.homeWallpaper.first())

        // And back the other way, to prove it's a real round-trip, not a one-shot default.
        repo.setShowGreeting(false)
        repo.setShortcutDensity(ShortcutDensity.FEW)
        repo.setHomeWallpaper("")

        assertEquals(false, repo.showGreeting.first())
        assertEquals(ShortcutDensity.FEW, repo.shortcutDensity.first())
        assertEquals("", repo.homeWallpaper.first())
    }

    @Test
    fun unknownPersistedDensityFallsBackToFew() = runTest {
        val store = newStore()
        val repo = DataStoreSettingsRepository(store)
        // Simulate a value from a future/corrupted install: write directly under the same
        // key name the repository uses ("shortcut_density"), bypassing the enum setter.
        val rawKey = stringPreferencesKey("shortcut_density")
        store.edit { it[rawKey] = "SOME_FUTURE_VALUE" }

        assertEquals(ShortcutDensity.FEW, repo.shortcutDensity.first())
    }
}
