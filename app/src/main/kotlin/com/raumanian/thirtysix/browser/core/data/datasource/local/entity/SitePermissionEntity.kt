package com.raumanian.thirtysix.browser.core.data.datasource.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

/**
 * Spec 021 data-model §1 — one remembered decision for one site and one capability.
 *
 * Its table moves `AppDatabase.SCHEMA_VERSION` from 2 to 3, strictly additively: it references
 * no existing table. Normal tabs only — an incognito answer is never written (INV-1) — and https
 * origins only (INV-2). "Allow this time" is never stored (FR-003).
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

    /** Epoch millis of the last change. */
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
