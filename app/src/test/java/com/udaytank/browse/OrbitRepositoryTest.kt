package com.udaytank.browse

import com.udaytank.browse.data.OrbitRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OrbitRepositoryTest {
    private fun repo() = OrbitRepository(FakeOrbitDao(), io = kotlinx.coroutines.Dispatchers.Unconfined)

    @Test fun `create adds an orbit with a unique stable profileKey`() = runTest {
        val r = repo()
        val a = r.create("Work", 0x11, now = 1000L)
        val b = r.create("Shop", 0x22, now = 2000L)
        assertEquals("Work", a.name)
        assertNotEquals(a.profileKey, b.profileKey)
        assertTrue(a.profileKey.startsWith("orbit_"))
    }

    @Test fun `delete refuses to remove the last orbit`() = runTest {
        val r = repo()
        val only = r.create("Personal", 0x1, now = 1L)
        assertFalse(r.delete(only.id))            // last one -> refused
        assertNotNull(r.get(only.id))
        val second = r.create("Work", 0x2, now = 2L)
        assertTrue(r.delete(second.id))           // now allowed
        assertNull(r.get(second.id))
    }

    @Test fun `ensureDefault creates Personal only when empty`() = runTest {
        val r = repo()
        val first = r.ensureDefault(now = 5L)
        assertEquals("Personal", first.name)
        val again = r.ensureDefault(now = 6L)
        assertEquals(first.id, again.id)          // no duplicate
    }

    @Test fun `create never reuses a profileKey after a delete, even with identical now`() = runTest {
        val r = repo()
        val a = r.create("A", 0x1, now = 1000L)
        val b = r.create("B", 0x2, now = 1000L)   // same `now` as A, would collide under the old count()-based key
        assertTrue(r.delete(b.id))                // frees up dao.count() back to what it was before B existed

        val c = r.create("C", 0x3, now = 1000L)   // same `now` again, after a delete dropped count()

        // C must not collide with A's still-live key, nor inherit B's now-deleted key.
        assertNotEquals(a.profileKey, c.profileKey)
        assertNotEquals(b.profileKey, c.profileKey)

        // All profileKeys ever handed out must be unique across the Orbit's lifetime.
        val allKeys = listOf(a.profileKey, b.profileKey, c.profileKey)
        assertEquals(allKeys.size, allKeys.toSet().size)
    }
}
