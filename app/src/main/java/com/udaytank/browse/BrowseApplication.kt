package com.udaytank.browse

import android.app.Application
import androidx.room.Room
import com.udaytank.browse.data.BrowseDatabase

class BrowseApplication : Application() {
    val database: BrowseDatabase by lazy {
        Room.databaseBuilder(this, BrowseDatabase::class.java, "browse.db")
            .addMigrations(BrowseDatabase.MIGRATION_1_2)
            .build()
    }
}
