package com.raumanian.thirtysix.browser.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = BookmarkFolderEntity.TABLE_NAME,
    foreignKeys = [
        ForeignKey(
            entity = BookmarkFolderEntity::class,
            parentColumns = [BookmarkFolderEntity.COL_ID],
            childColumns = [BookmarkFolderEntity.COL_PARENT_ID],
            onDelete = ForeignKey.SET_NULL,
            onUpdate = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [
        Index(value = [BookmarkFolderEntity.COL_PARENT_ID], name = BookmarkFolderEntity.INDEX_PARENT_ID),
    ],
)
data class BookmarkFolderEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = COL_ID)
    val id: Long = 0L,

    @ColumnInfo(name = COL_NAME)
    val name: String,

    @ColumnInfo(name = COL_PARENT_ID)
    val parentId: Long? = null,

    @ColumnInfo(name = COL_CREATED_AT)
    val createdAt: Long,
) {
    companion object {
        const val TABLE_NAME = "bookmark_folders"
        const val COL_ID = "id"
        const val COL_NAME = "name"
        const val COL_PARENT_ID = "parent_id"
        const val COL_CREATED_AT = "created_at"
        const val INDEX_PARENT_ID = "index_bookmark_folders_parent_id"
    }
}
