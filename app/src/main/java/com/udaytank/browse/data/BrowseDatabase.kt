package com.udaytank.browse.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [HistoryEntry::class, Bookmark::class, TabEntity::class], version = 3)
abstract class BrowseDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun tabDao(): TabDao

    companion object {
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `tabs` ADD COLUMN `isIncognito` INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `tabs` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`url` TEXT NOT NULL, " +
                        "`title` TEXT NOT NULL, " +
                        "`position` INTEGER NOT NULL, " +
                        "`isActive` INTEGER NOT NULL)"
                )
            }
        }
    }
}
