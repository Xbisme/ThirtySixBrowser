package com.raumanian.thirtysix.browser.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = BookmarkEntity.TABLE_NAME,
    foreignKeys = [
        ForeignKey(
            entity = BookmarkFolderEntity::class,
            parentColumns = [BookmarkFolderEntity.COL_ID],
            childColumns = [BookmarkEntity.COL_PARENT_FOLDER_ID],
            onDelete = ForeignKey.SET_NULL,
            onUpdate = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [
        Index(value = [BookmarkEntity.COL_PARENT_FOLDER_ID], name = BookmarkEntity.INDEX_PARENT_FOLDER_ID),
    ],
)
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = COL_ID)
    val id: Long = 0L,

    @ColumnInfo(name = COL_TITLE)
    val title: String,

    @ColumnInfo(name = COL_URL)
    val url: String,

    @ColumnInfo(name = COL_PARENT_FOLDER_ID)
    val parentFolderId: Long? = null,

    @ColumnInfo(name = COL_CREATED_AT)
    val createdAt: Long,

    @ColumnInfo(name = COL_SORT_ORDER)
    val sortOrder: Long,
) {
    companion object {
        const val TABLE_NAME = "bookmarks"
        const val COL_ID = "id"
        const val COL_TITLE = "title"
        const val COL_URL = "url"
        const val COL_PARENT_FOLDER_ID = "parent_folder_id"
        const val COL_CREATED_AT = "created_at"
        const val COL_SORT_ORDER = "sort_order"
        const val INDEX_PARENT_FOLDER_ID = "index_bookmarks_parent_folder_id"
    }
}
