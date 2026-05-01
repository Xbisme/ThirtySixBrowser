package com.raumanian.thirtysix.browser.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = HistoryEntryEntity.TABLE_NAME,
    indices = [
        Index(value = [HistoryEntryEntity.COL_VISITED_AT], name = HistoryEntryEntity.INDEX_VISITED_AT),
    ],
)
data class HistoryEntryEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = COL_ID)
    val id: Long = 0L,

    @ColumnInfo(name = COL_URL)
    val url: String,

    @ColumnInfo(name = COL_TITLE)
    val title: String,

    @ColumnInfo(name = COL_VISITED_AT)
    val visitedAt: Long,
) {
    companion object {
        const val TABLE_NAME = "history_entries"
        const val COL_ID = "id"
        const val COL_URL = "url"
        const val COL_TITLE = "title"
        const val COL_VISITED_AT = "visited_at"
        const val INDEX_VISITED_AT = "index_history_entries_visited_at"
    }
}
