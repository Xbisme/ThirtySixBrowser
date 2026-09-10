package com.raumanian.thirtysix.browser.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.raumanian.thirtysix.browser.data.local.dao.BookmarkDao
import com.raumanian.thirtysix.browser.data.local.dao.BookmarkFolderDao
import com.raumanian.thirtysix.browser.data.local.dao.DownloadRecordDao
import com.raumanian.thirtysix.browser.data.local.dao.HistoryDao
import com.raumanian.thirtysix.browser.data.local.dao.TabDao
import com.raumanian.thirtysix.browser.data.local.entity.BookmarkEntity
import com.raumanian.thirtysix.browser.data.local.entity.BookmarkFolderEntity
import com.raumanian.thirtysix.browser.data.local.entity.DownloadRecordEntity
import com.raumanian.thirtysix.browser.data.local.entity.HistoryEntryEntity
import com.raumanian.thirtysix.browser.data.local.entity.TabEntity

@Database(
    entities = [
        BookmarkEntity::class,
        BookmarkFolderEntity::class,
        DownloadRecordEntity::class,
        HistoryEntryEntity::class,
        TabEntity::class,
    ],
    version = AppDatabase.SCHEMA_VERSION,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun bookmarkDao(): BookmarkDao
    abstract fun bookmarkFolderDao(): BookmarkFolderDao

    abstract fun downloadRecordDao(): DownloadRecordDao
    abstract fun historyDao(): HistoryDao
    abstract fun tabDao(): TabDao

    companion object {
        const val DATABASE_NAME = "thirtysix_browser.db"

        /**
         * Spec 015 raised this from 1 to 2 by adding `download_records` — the project's
         * first real migration. Any further bump MUST ship a registered [androidx.room.migration.Migration]
         * and a committed schema export; `fallbackToDestructiveMigration*` remains banned.
         */
        const val SCHEMA_VERSION = 2
    }
}
