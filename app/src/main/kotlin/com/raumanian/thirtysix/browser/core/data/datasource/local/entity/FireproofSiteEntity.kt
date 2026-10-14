package com.raumanian.thirtysix.browser.core.data.datasource.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Spec 024 data-model — one site the person fireproofed in normal browsing.
 *
 * Its table moves `AppDatabase.SCHEMA_VERSION` from 4 to 5, strictly additively. Incognito
 * never fireproofs a site (FR-011).
 */
@Entity(tableName = FireproofSiteEntity.TABLE_NAME)
data class FireproofSiteEntity(
    /** Registrable domain, lower-case ASCII (IDN as punycode). */
    @PrimaryKey
    @ColumnInfo(name = COL_SITE)
    val site: String,

    /** Epoch millis when the site was fireproofed. */
    @ColumnInfo(name = COL_ADDED_AT)
    val addedAt: Long,
) {
    companion object {
        const val TABLE_NAME = "fireproof_sites"
        const val COL_SITE = "site"
        const val COL_ADDED_AT = "added_at"
    }
}
