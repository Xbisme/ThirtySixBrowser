package com.raumanian.thirtysix.browser.di

import android.content.Context
import androidx.room.Room
import com.raumanian.thirtysix.browser.data.local.dao.BookmarkDao
import com.raumanian.thirtysix.browser.data.local.dao.BookmarkFolderDao
import com.raumanian.thirtysix.browser.data.local.dao.HistoryDao
import com.raumanian.thirtysix.browser.data.local.dao.TabDao
import com.raumanian.thirtysix.browser.data.local.database.AppDatabase
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
            // No fallbackToDestructiveMigration*: strict-no-destructive policy (FR-009).
            // No setJournalMode: rely on Room's WRITE_AHEAD_LOGGING default (FR-007 / R8).
            .build()

    @Provides
    fun provideBookmarkDao(db: AppDatabase): BookmarkDao = db.bookmarkDao()

    @Provides
    fun provideBookmarkFolderDao(db: AppDatabase): BookmarkFolderDao = db.bookmarkFolderDao()

    @Provides
    fun provideHistoryDao(db: AppDatabase): HistoryDao = db.historyDao()

    @Provides
    fun provideTabDao(db: AppDatabase): TabDao = db.tabDao()
}
