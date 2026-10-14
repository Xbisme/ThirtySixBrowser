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

        // Every bump needs a registered additive Migration and a committed schema export;
        // destructive fallback is never used, so user data always survives an upgrade.
        const val SCHEMA_VERSION = 5
    }
}
