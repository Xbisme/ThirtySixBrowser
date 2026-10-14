package com.raumanian.thirtysix.browser.core.data.datasource.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

/**
 * One remembered decision for one https origin and capability. Incognito answers and
 * "Allow this time" are never stored.
 */
@Entity(
    tableName = SitePermissionEntity.TABLE_NAME,
    primaryKeys = [SitePermissionEntity.COL_ORIGIN, SitePermissionEntity.COL_CAPABILITY],
)
data class SitePermissionEntity(
    /** `scheme://host[:port]`, lower-cased, default port omitted. */
    @ColumnInfo(name = COL_ORIGIN)
    val origin: String,

    /** `camera` | `microphone` | `location`. */
    @ColumnInfo(name = COL_CAPABILITY)
    val capability: String,

    /** `allow` | `deny`. */
    @ColumnInfo(name = COL_DECISION)
    val decision: String,

    @ColumnInfo(name = COL_DECIDED_AT)
    val decidedAt: Long,
) {
    companion object {
        const val TABLE_NAME = "site_permissions"
        const val COL_ORIGIN = "origin"
        const val COL_CAPABILITY = "capability"
        const val COL_DECISION = "decision"
        const val COL_DECIDED_AT = "decided_at"
    }
}
