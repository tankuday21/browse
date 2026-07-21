package com.udaytank.browse

import com.udaytank.browse.data.PlayerProgressDao
import com.udaytank.browse.data.PlayerProgressEntity
import kotlinx.coroutines.flow.MutableStateFlow

class FakePlayerProgressDao : PlayerProgressDao {
    val rows = MutableStateFlow<Map<String, PlayerProgressEntity>>(emptyMap())

    override suspend fun get(filePath: String): PlayerProgressEntity? = rows.value[filePath]

    override suspend fun upsert(entry: PlayerProgressEntity) {
        rows.value = rows.value + (entry.filePath to entry)
    }

    override suspend fun delete(filePath: String) {
        rows.value = rows.value - filePath
    }

    override suspend fun clearAll() {
        rows.value = emptyMap()
    }
}
