package com.udaytank.browse

import com.udaytank.browse.data.ClosedTabDao
import com.udaytank.browse.data.ClosedTabEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Semantics mirror the real SQL — including SQL's NULL behaviour, which is the point: `WHERE
 * orbitId = :orbitId` never matches a NULL row, so neither does this fake.
 *
 * **Timing is NOT modelled.** `observeRecent` maps a [MutableStateFlow], so re-subscription emits
 * synchronously; real Room emits only after running the query on its executor. Passing tests
 * therefore prove nothing about the Orbit-switch window (that is why `recentlyClosed` carries an
 * explicit `onStart { emit(emptyList()) }` rather than relying on test evidence).
 */
class FakeClosedTabDao : ClosedTabDao {
    val entries = MutableStateFlow<List<ClosedTabEntity>>(emptyList())
    /** Every (orbitId, max) the caller trimmed with — lets a test pin the ARGUMENT, not just the effect. */
    val trimCalls = mutableListOf<Pair<Long, Int>>()
    private var nextId = 1L

    override suspend fun insert(entry: ClosedTabEntity) {
        // NOTE: unlike Room's autoGenerate, this always overwrites an explicitly supplied id.
        entries.value = entries.value + entry.copy(id = nextId++)
    }

    // Mirrors the real SQL: the trim is PER ORBIT, and rows of other Orbits are untouched.
    override suspend fun trimTo(orbitId: Long, max: Int) {
        trimCalls += orbitId to max
        val mine = entries.value.filter { it.orbitId == orbitId }
        val keep = mine
            .sortedWith(compareByDescending<ClosedTabEntity> { it.closedAt }.thenByDescending { it.id })
            .take(max)
            .map { it.id }
            .toSet()
        entries.value = entries.value.filter { it.orbitId != orbitId || it.id in keep }
    }

    // `WHERE orbitId = :orbitId` never matches a NULL row in SQL, so the fake must not either.
    override fun observeRecent(orbitId: Long, limit: Int): Flow<List<ClosedTabEntity>> =
        entries.map { list ->
            list.filter { it.orbitId == orbitId }
                .sortedWith(compareByDescending<ClosedTabEntity> { it.closedAt }.thenByDescending { it.id })
                .take(limit)
        }

    override suspend fun deleteForOrbit(orbitId: Long) {
        entries.value = entries.value.filterNot { it.orbitId == orbitId }
    }

    // Mirrors the real SQL: deletes exactly ONE row — the newest matching (orbitId, url).
    override suspend fun deleteNewestForUrl(orbitId: Long, url: String) {
        val target = entries.value
            .filter { it.orbitId == orbitId && it.url == url }
            .maxWithOrNull(compareBy({ it.closedAt }, { it.id }))
            ?: return
        entries.value = entries.value.filterNot { it.id == target.id }
    }

    override suspend fun deleteById(id: Long) {
        entries.value = entries.value.filterNot { it.id == id }
    }

    override suspend fun clear() { entries.value = emptyList() }
}
