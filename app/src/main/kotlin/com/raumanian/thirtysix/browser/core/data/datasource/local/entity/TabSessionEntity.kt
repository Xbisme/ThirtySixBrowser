package com.raumanian.thirtysix.browser.core.data.datasource.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = TabSessionEntity.TABLE_NAME,
    foreignKeys = [
        ForeignKey(
            entity = TabEntity::class,
            parentColumns = [TabEntity.COL_ID],
            childColumns = [TabSessionEntity.COL_TAB_ID],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
class TabSessionEntity(
    @PrimaryKey
    @ColumnInfo(name = COL_TAB_ID)
    val tabId: Long,

    @ColumnInfo(name = COL_STATE, typeAffinity = ColumnInfo.BLOB)
    val state: ByteArray,

    @ColumnInfo(name = COL_SAVED_AT)
    val savedAt: Long,
) {
    companion object {
        const val TABLE_NAME = "tab_sessions"
        const val COL_TAB_ID = "tab_id"
        const val COL_STATE = "state"
        const val COL_SAVED_AT = "saved_at"
    }
}
