package com.raumanian.thirtysix.browser.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.raumanian.thirtysix.browser.data.local.dao.BookmarkDao
import com.raumanian.thirtysix.browser.data.local.dao.BookmarkFolderDao
import com.raumanian.thirtysix.browser.data.local.dao.HistoryDao
import com.raumanian.thirtysix.browser.data.local.dao.TabDao
import com.raumanian.thirtysix.browser.data.local.entity.BookmarkEntity
import com.raumanian.thirtysix.browser.data.local.entity.BookmarkFolderEntity
import com.raumanian.thirtysix.browser.data.local.entity.HistoryEntryEntity
import com.raumanian.thirtysix.browser.data.local.entity.TabEntity

@Database(
    entities = [
        BookmarkEntity::class,
        BookmarkFolderEntity::class,
        HistoryEntryEntity::class,
        TabEntity::class,
    ],
    version = AppDatabase.SCHEMA_VERSION,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun bookmarkDao(): BookmarkDao
    abstract fun bookmarkFolderDao(): BookmarkFolderDao
    abstract fun historyDao(): HistoryDao
    abstract fun tabDao(): TabDao

    companion object {
        const val DATABASE_NAME = "thirtysix_browser.db"
        const val SCHEMA_VERSION = 1
    }
}
