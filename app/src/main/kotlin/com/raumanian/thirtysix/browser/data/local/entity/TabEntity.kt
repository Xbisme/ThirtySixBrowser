package com.raumanian.thirtysix.browser.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = TabEntity.TABLE_NAME,
    indices = [
        Index(value = [TabEntity.COL_POSITION], name = TabEntity.INDEX_POSITION),
    ],
)
data class TabEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = COL_ID)
    val id: Long = 0L,

    @ColumnInfo(name = COL_URL)
    val url: String,

    @ColumnInfo(name = COL_TITLE)
    val title: String,

    @ColumnInfo(name = COL_POSITION)
    val position: Int,

    @ColumnInfo(name = COL_CREATED_AT)
    val createdAt: Long,

    @ColumnInfo(name = COL_LAST_ACTIVE_AT)
    val lastActiveAt: Long,
) {
    companion object {
        const val TABLE_NAME = "tabs"
        const val COL_ID = "id"
        const val COL_URL = "url"
        const val COL_TITLE = "title"
        const val COL_POSITION = "position"
        const val COL_CREATED_AT = "created_at"
        const val COL_LAST_ACTIVE_AT = "last_active_at"
        const val INDEX_POSITION = "index_tabs_position"
    }
}
