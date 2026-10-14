package com.raumanian.thirtysix.browser.core.data.datasource.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** A site fireproofed in normal browsing; incognito never fireproofs a site. */
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
