package com.raumanian.thirtysix.browser.data.local.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.raumanian.thirtysix.browser.data.local.entity.DownloadRecordEntity

/**
 * Spec 015 FR-016 — the project's **first** schema migration: v1 (Spec 005) → v2.
 *
 * Strictly additive. It creates `download_records` and its `created_at` index, and
 * **names no pre-existing table**: not `bookmarks`, not `bookmark_folders`, not
 * `history_entries`, not `tabs`. That property is not a stylistic preference — it is what
 * makes it structurally impossible for this migration to alter or lose existing user data,
 * which is exactly what Spec 005's strict-no-destructive policy was written to guarantee.
 *
 * The DDL below must match byte-for-byte what Room generates for
 * [DownloadRecordEntity], or `MigrationTestHelper` will fail the schema-identity check.
 * Any change to that entity must be mirrored here.
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
