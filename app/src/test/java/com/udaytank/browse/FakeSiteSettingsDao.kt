package com.udaytank.browse

import com.udaytank.browse.data.SiteSettingsDao
import com.udaytank.browse.data.SiteSettingsEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeSiteSettingsDao : SiteSettingsDao {
    val entries = MutableStateFlow<List<SiteSettingsEntity>>(emptyList())

    override suspend fun getByHost(host: String): SiteSettingsEntity? =
        entries.value.firstOrNull { it.host == host }

    override fun observeAll(): Flow<List<SiteSettingsEntity>> = entries

    override suspend fun upsert(entity: SiteSettingsEntity) {
        entries.value = entries.value.filterNot { it.host == entity.host } + entity
    }

    override suspend fun deleteByHost(host: String) {
        entries.value = entries.value.filterNot { it.host == host }
    }
}
