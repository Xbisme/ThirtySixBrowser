package com.raumanian.thirtysix.browser.data.local.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.raumanian.thirtysix.browser.data.local.entity.SiteProtectionExceptionEntity

/**
 * Spec 023 research R11 — adds `site_protection_exceptions`. Strictly additive: it names no
 * existing table, so existing data cannot be touched by construction.
 */
object Migration3To4 {
    private const val FROM_VERSION = 3
    private const val TO_VERSION = 4

    val MIGRATION_3_4: Migration = object : Migration(FROM_VERSION, TO_VERSION) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `${SiteProtectionExceptionEntity.TABLE_NAME}` (" +
                    "`${SiteProtectionExceptionEntity.COL_SITE}` TEXT NOT NULL, " +
                    "`${SiteProtectionExceptionEntity.COL_ADDED_AT}` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`${SiteProtectionExceptionEntity.COL_SITE}`))",
            )
        }
    }
}
