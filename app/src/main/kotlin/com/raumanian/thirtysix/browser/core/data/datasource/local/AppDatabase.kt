package com.raumanian.thirtysix.browser.core.data.datasource.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.raumanian.thirtysix.browser.core.data.datasource.local.entity.BookmarkEntity
import com.raumanian.thirtysix.browser.core.data.datasource.local.entity.BookmarkFolderEntity
import com.raumanian.thirtysix.browser.core.data.datasource.local.entity.DownloadRecordEntity
import com.raumanian.thirtysix.browser.core.data.datasource.local.entity.FireproofSiteEntity
import com.raumanian.thirtysix.browser.core.data.datasource.local.entity.HistoryEntryEntity
import com.raumanian.thirtysix.browser.core.data.datasource.local.entity.SitePermissionEntity
import com.raumanian.thirtysix.browser.core.data.datasource.local.entity.SiteProtectionExceptionEntity
import com.raumanian.thirtysix.browser.core.data.datasource.local.entity.TabEntity
@Database(
    entities = [
        BookmarkEntity::class,
        BookmarkFolderEntity::class,
        DownloadRecordEntity::class,
        FireproofSiteEntity::class,
        HistoryEntryEntity::class,
        SitePermissionEntity::class,
        SiteProtectionExceptionEntity::class,
        TabEntity::class,
    ],
    version = AppDatabase.SCHEMA_VERSION,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun bookmarkDao(): BookmarkDao
    abstract fun bookmarkFolderDao(): BookmarkFolderDao

    abstract fun downloadRecordDao(): DownloadRecordDao
    abstract fun fireproofSiteDao(): FireproofSiteDao
    abstract fun historyDao(): HistoryDao
    abstract fun sitePermissionDao(): SitePermissionDao
    abstract fun siteProtectionExceptionDao(): SiteProtectionExceptionDao
    abstract fun tabDao(): TabDao

    companion object {
        const val DATABASE_NAME = "thirtysix_browser.db"

        /**
         * Spec 015 raised this from 1 to 2 by adding `download_records` — the project's
         * first real migration. Any further bump MUST ship a registered [androidx.room.migration.Migration]
         * and a committed schema export; `fallbackToDestructiveMigration*` remains banned.
         * Spec 021 raised it to 3 by adding `site_permissions` (Migration2To3).
         * Spec 023 raised it to 4 by adding `site_protection_exceptions` (Migration3To4).
         * Spec 024 raised it to 5 by adding `fireproof_sites` (Migration4To5).
         */
        const val SCHEMA_VERSION = 5
    }
}
