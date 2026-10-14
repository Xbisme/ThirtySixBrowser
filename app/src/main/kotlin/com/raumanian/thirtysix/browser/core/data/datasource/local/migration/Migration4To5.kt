package com.raumanian.thirtysix.browser.core.data.datasource.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.raumanian.thirtysix.browser.core.data.datasource.local.entity.FireproofSiteEntity
/**
 * Spec 024 research R12 — adds `fireproof_sites`. Strictly additive: it names no existing table,
 * so existing data cannot be touched by construction.
 */
object Migration4To5 {
    private const val FROM_VERSION = 4
    private const val TO_VERSION = 5

    val MIGRATION_4_5: Migration = object : Migration(FROM_VERSION, TO_VERSION) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `${FireproofSiteEntity.TABLE_NAME}` (" +
                    "`${FireproofSiteEntity.COL_SITE}` TEXT NOT NULL, " +
                    "`${FireproofSiteEntity.COL_ADDED_AT}` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`${FireproofSiteEntity.COL_SITE}`))",
            )
        }
    }
}
