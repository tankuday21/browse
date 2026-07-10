package com.udaytank.browse.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [HistoryEntry::class, Bookmark::class], version = 1)
abstract class BrowseDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
    abstract fun bookmarkDao(): BookmarkDao
}
