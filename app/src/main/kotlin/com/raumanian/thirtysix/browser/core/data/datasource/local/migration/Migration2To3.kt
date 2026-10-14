package com.raumanian.thirtysix.browser.core.data.datasource.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.raumanian.thirtysix.browser.core.data.datasource.local.entity.SitePermissionEntity
/**
 * Spec 021 research R8 — adds `site_permissions`. Strictly additive: it names no existing table,
 * so existing data cannot be touched by construction.
 */
object Migration2To3 {
    private const val FROM_VERSION = 2
    private const val TO_VERSION = 3

    val MIGRATION_2_3: Migration = object : Migration(FROM_VERSION, TO_VERSION) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `${SitePermissionEntity.TABLE_NAME}` (" +
                    "`${SitePermissionEntity.COL_ORIGIN}` TEXT NOT NULL, " +
                    "`${SitePermissionEntity.COL_CAPABILITY}` TEXT NOT NULL, " +
                    "`${SitePermissionEntity.COL_DECISION}` TEXT NOT NULL, " +
                    "`${SitePermissionEntity.COL_DECIDED_AT}` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`${SitePermissionEntity.COL_ORIGIN}`, `${SitePermissionEntity.COL_CAPABILITY}`))",
            )
        }
    }
}
