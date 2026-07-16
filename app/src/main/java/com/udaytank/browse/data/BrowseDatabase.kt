package com.udaytank.browse.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.udaytank.browse.data.feed.FeedDao
import com.udaytank.browse.data.feed.FeedItemEntity
import com.udaytank.browse.data.feed.RssSourceEntity

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
        FeedItemEntity::class,
        RssSourceEntity::class,
        ZappedElementEntity::class,
        FaviconEntity::class,
        OrbitEntity::class,
    ],
    version = 15,
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
    abstract fun feedDao(): FeedDao
    abstract fun zappedElementDao(): ZappedElementDao
    abstract fun faviconDao(): FaviconDao
    abstract fun orbitDao(): OrbitDao

    companion object {
        /** Orbit accent blue — default color for the seeded "Personal" Orbit. */
        const val DEFAULT_ORBIT_COLOR = 0xFF2C5BE6.toInt()

        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // v4.2 Orbit identity icons: each Orbit gets an icon key (default 'person').
                db.execSQL("ALTER TABLE orbits ADD COLUMN iconKey TEXT NOT NULL DEFAULT 'person'")
            }
        }

        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `orbits` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`colorArgb` INTEGER NOT NULL, " +
                        "`position` INTEGER NOT NULL, " +
                        "`profileKey` TEXT NOT NULL)"
                )
                db.execSQL("ALTER TABLE tabs ADD COLUMN orbitId INTEGER")
                // Seed the default "Personal" Orbit and assign all existing (non-incognito) tabs to it.
                db.execSQL(
                    "INSERT INTO orbits (name, colorArgb, position, profileKey) " +
                        "VALUES ('Personal', " + DEFAULT_ORBIT_COLOR + ", 0, 'orbit_personal_default')"
                )
                db.execSQL(
                    "UPDATE tabs SET orbitId = (SELECT id FROM orbits WHERE profileKey = 'orbit_personal_default') " +
                        "WHERE isIncognito = 0"
                )
            }
        }

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `favicons` (" +
                        "`host` TEXT NOT NULL, " +
                        "`iconUrl` TEXT, " +
                        "`iconBytes` BLOB, " +
                        "`updatedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`host`))"
                )
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE feed_items ADD COLUMN description TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `zapped_elements` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`host` TEXT NOT NULL, " +
                        "`selector` TEXT NOT NULL, " +
                        "`label` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_zapped_elements_host` ON `zapped_elements` (`host`)"
                )
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `feed_items` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`sourceId` TEXT NOT NULL, " +
                        "`title` TEXT NOT NULL, " +
                        "`link` TEXT NOT NULL, " +
                        "`publishedAt` INTEGER NOT NULL, " +
                        "`thumbnailUrl` TEXT, " +
                        "`category` TEXT NOT NULL)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_feed_items_link` ON `feed_items` (`link`)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `rss_sources` (" +
                        "`id` TEXT NOT NULL, " +
                        "`title` TEXT NOT NULL, " +
                        "`url` TEXT NOT NULL, " +
                        "`category` TEXT NOT NULL, " +
                        "`enabled` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`))"
                )
            }
        }

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
