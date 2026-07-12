package com.udaytank.browse.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        HistoryEntry::class,
        Bookmark::class,
        TabEntity::class,
        DownloadEntry::class,
        TabGroupEntity::class,
        ClosedTabEntity::class,
        ReadingListEntry::class,
        SiteSettingsEntity::class,
        HomeShortcutEntity::class,
    ],
    version = 9,
)
abstract class BrowseDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun tabDao(): TabDao
    abstract fun downloadDao(): DownloadDao
    abstract fun tabGroupDao(): TabGroupDao
    abstract fun closedTabDao(): ClosedTabDao
    abstract fun readingListDao(): ReadingListDao
    abstract fun siteSettingsDao(): SiteSettingsDao
    abstract fun homeShortcutDao(): HomeShortcutDao

    companion object {
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `home_shortcuts` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`url` TEXT NOT NULL, " +
                        "`title` TEXT NOT NULL, " +
                        "`position` INTEGER NOT NULL)"
                )
                // Seed the grid from what the home page displayed before v9: the 8 newest
                // bookmarks (BookmarkDao.observeAll orders createdAt DESC; HomePage took 8).
                // position = how many bookmarks are newer, so the top-8 get ranks 0..7.
                // COUNT-based rank instead of ROW_NUMBER(): minSdk 26 predates SQLite 3.25.
                db.execSQL(
                    "INSERT INTO home_shortcuts (url, title, position) " +
                        "SELECT b.url, b.title, " +
                        "(SELECT COUNT(*) FROM bookmarks n WHERE n.createdAt > b.createdAt " +
                        "OR (n.createdAt = b.createdAt AND n.id < b.id)) " +
                        "FROM bookmarks b " +
                        "ORDER BY b.createdAt DESC, b.id ASC " +
                        "LIMIT 8"
                )
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `reading_list` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`url` TEXT NOT NULL, " +
                        "`title` TEXT NOT NULL, " +
                        "`addedAt` INTEGER NOT NULL, " +
                        "`readAt` INTEGER, " +
                        "`filePath` TEXT)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `site_settings` (" +
                        "`host` TEXT NOT NULL, " +
                        "`textZoom` INTEGER NOT NULL, " +
                        "`forceDark` INTEGER NOT NULL, " +
                        "`desktopMode` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`host`))"
                )
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `downloads` ADD COLUMN `totalBytes` INTEGER NOT NULL DEFAULT -1")
                db.execSQL("ALTER TABLE `downloads` ADD COLUMN `downloadedBytes` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `downloads` ADD COLUMN `state` TEXT NOT NULL DEFAULT 'DONE'")
                db.execSQL("ALTER TABLE `downloads` ADD COLUMN `filePath` TEXT")
                db.execSQL("ALTER TABLE `downloads` ADD COLUMN `mimeType` TEXT")
                db.execSQL("ALTER TABLE `downloads` ADD COLUMN `etag` TEXT")
                db.execSQL("ALTER TABLE `downloads` ADD COLUMN `segments` INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE `downloads` ADD COLUMN `segmentState` TEXT")
                db.execSQL("ALTER TABLE `downloads` ADD COLUMN `error` TEXT")
                db.execSQL("ALTER TABLE downloads ADD COLUMN attempts INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `tabs` ADD COLUMN `groupId` INTEGER")
                db.execSQL("ALTER TABLE `tabs` ADD COLUMN `pinned` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `tabs` ADD COLUMN `locked` INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `tab_groups` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, `color` INTEGER NOT NULL, `position` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `closed_tabs` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`url` TEXT NOT NULL, `title` TEXT NOT NULL, `closedAt` INTEGER NOT NULL)"
                )
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `bookmarks` ADD COLUMN `folder` TEXT")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `downloads` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`downloadId` INTEGER NOT NULL, " +
                        "`fileName` TEXT NOT NULL, " +
                        "`url` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL)"
                )
            }
        }

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
