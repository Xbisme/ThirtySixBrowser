package com.raumanian.thirtysix.browser.core.data.datasource.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.raumanian.thirtysix.browser.core.data.datasource.local.entity.DownloadRecordEntity
/**
 * Adds `download_records`. Strictly additive: it names no existing table, so user data cannot be
 * touched. The DDL must match what Room generates for [DownloadRecordEntity], or Room's schema
 * check fails on open; mirror any change to that entity here.
 */
object Migration1To2 {

    val MIGRATION_1_2: Migration = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `${DownloadRecordEntity.TABLE_NAME}` (" +
                    "`${DownloadRecordEntity.COL_ID}` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`${DownloadRecordEntity.COL_SOURCE_URL}` TEXT NOT NULL, " +
                    "`${DownloadRecordEntity.COL_FILE_NAME}` TEXT NOT NULL, " +
                    "`${DownloadRecordEntity.COL_MIME_TYPE}` TEXT NOT NULL, " +
                    "`${DownloadRecordEntity.COL_CREATED_AT}` INTEGER NOT NULL, " +
                    "`${DownloadRecordEntity.COL_TRANSFER_HANDLE}` INTEGER NOT NULL, " +
                    "`${DownloadRecordEntity.COL_LOCAL_URI}` TEXT)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `${DownloadRecordEntity.INDEX_CREATED_AT}` " +
                    "ON `${DownloadRecordEntity.TABLE_NAME}` (`${DownloadRecordEntity.COL_CREATED_AT}`)",
            )
        }
    }
}
