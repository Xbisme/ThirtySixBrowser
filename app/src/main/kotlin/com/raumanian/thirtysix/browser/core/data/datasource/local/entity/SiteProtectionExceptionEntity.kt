package com.raumanian.thirtysix.browser.core.data.datasource.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** A site whose protection was turned off in normal browsing; incognito exceptions are never stored. */
@Entity(tableName = SiteProtectionExceptionEntity.TABLE_NAME)
data class SiteProtectionExceptionEntity(
    /** Registrable domain, lower-case ASCII (IDN as punycode). */
    @PrimaryKey
    @ColumnInfo(name = COL_SITE)
    val site: String,

    /** Epoch millis when protection was turned off. */
    @ColumnInfo(name = COL_ADDED_AT)
    val addedAt: Long,
) {
    companion object {
        const val TABLE_NAME = "site_protection_exceptions"
        const val COL_SITE = "site"
        const val COL_ADDED_AT = "added_at"
    }
}
