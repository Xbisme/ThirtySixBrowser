package com.raumanian.thirtysix.browser.core.di

import android.content.Context
import androidx.room.Room
import com.raumanian.thirtysix.browser.core.data.datasource.local.AppDatabase
import com.raumanian.thirtysix.browser.core.data.datasource.local.BookmarkDao
import com.raumanian.thirtysix.browser.core.data.datasource.local.BookmarkFolderDao
import com.raumanian.thirtysix.browser.core.data.datasource.local.DownloadRecordDao
import com.raumanian.thirtysix.browser.core.data.datasource.local.FireproofSiteDao
import com.raumanian.thirtysix.browser.core.data.datasource.local.HistoryDao
import com.raumanian.thirtysix.browser.core.data.datasource.local.SitePermissionDao
import com.raumanian.thirtysix.browser.core.data.datasource.local.SiteProtectionExceptionDao
import com.raumanian.thirtysix.browser.core.data.datasource.local.TabDao
import com.raumanian.thirtysix.browser.core.data.datasource.local.migration.Migration1To2
import com.raumanian.thirtysix.browser.core.data.datasource.local.migration.Migration2To3
import com.raumanian.thirtysix.browser.core.data.datasource.local.migration.Migration3To4
import com.raumanian.thirtysix.browser.core.data.datasource.local.migration.Migration4To5
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.DATABASE_NAME)
            // No destructive fallback: a missing migration makes Room throw instead of wiping
            // user data. WAL comes from Room's default journal mode.
            .addMigrations(
                Migration1To2.MIGRATION_1_2,
                Migration2To3.MIGRATION_2_3,
                Migration3To4.MIGRATION_3_4,
                Migration4To5.MIGRATION_4_5,
            )
            .build()

    @Provides
    fun provideBookmarkDao(db: AppDatabase): BookmarkDao = db.bookmarkDao()

    @Provides
    fun provideBookmarkFolderDao(db: AppDatabase): BookmarkFolderDao = db.bookmarkFolderDao()

    @Provides
    fun provideDownloadRecordDao(db: AppDatabase): DownloadRecordDao = db.downloadRecordDao()

    @Provides
    fun provideFireproofSiteDao(db: AppDatabase): FireproofSiteDao = db.fireproofSiteDao()

    @Provides
    fun provideHistoryDao(db: AppDatabase): HistoryDao = db.historyDao()

    @Provides
    fun provideSitePermissionDao(db: AppDatabase): SitePermissionDao = db.sitePermissionDao()

    @Provides
    fun provideSiteProtectionExceptionDao(db: AppDatabase): SiteProtectionExceptionDao =
        db.siteProtectionExceptionDao()

    @Provides
    fun provideTabDao(db: AppDatabase): TabDao = db.tabDao()
}
