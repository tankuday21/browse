package com.udaytank.browse.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "site_settings")
data class SiteSettingsEntity(
    @PrimaryKey val host: String,
    val textZoom: Int = -1,     // -1 unset, else 50..200 (percent)
    val forceDark: Int = -1,    // -1 default, 0 off, 1 on
    val desktopMode: Int = -1,  // -1 default, 0 off, 1 on
)
