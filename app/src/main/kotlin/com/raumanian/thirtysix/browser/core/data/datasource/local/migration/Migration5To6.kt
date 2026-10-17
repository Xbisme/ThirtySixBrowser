package com.raumanian.thirtysix.browser.core.data.datasource.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.raumanian.thirtysix.browser.core.data.datasource.local.entity.TabEntity
import com.raumanian.thirtysix.browser.core.data.datasource.local.entity.TabSessionEntity

/**
 * Adds `tab_sessions`. Strictly additive: it only creates a table, so existing data cannot be
 * touched by construction.
 */
object Migration5To6 {
    private const val FROM_VERSION = 5
    private const val TO_VERSION = 6

    val MIGRATION_5_6: Migration = object : Migration(FROM_VERSION, TO_VERSION) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `${TabSessionEntity.TABLE_NAME}` (" +
                    "`${TabSessionEntity.COL_TAB_ID}` INTEGER NOT NULL, " +
                    "`${TabSessionEntity.COL_STATE}` BLOB NOT NULL, " +
                    "`${TabSessionEntity.COL_SAVED_AT}` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`${TabSessionEntity.COL_TAB_ID}`), " +
                    "FOREIGN KEY(`${TabSessionEntity.COL_TAB_ID}`) " +
                    "REFERENCES `${TabEntity.TABLE_NAME}`(`${TabEntity.COL_ID}`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
        }
    }
}
