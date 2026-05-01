package com.raumanian.thirtysix.browser.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.raumanian.thirtysix.browser.data.local.entity.BookmarkFolderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkFolderDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(folder: BookmarkFolderEntity): Long

    @Update
    suspend fun update(folder: BookmarkFolderEntity)

    @Delete
    suspend fun delete(folder: BookmarkFolderEntity)

    @Query("SELECT * FROM ${BookmarkFolderEntity.TABLE_NAME} WHERE ${BookmarkFolderEntity.COL_ID} = :id")
    suspend fun getById(id: Long): BookmarkFolderEntity?

    /**
     * Direct children of a given folder, ordered by name ascending. Unicode/locale-aware
     * ordering is left to higher layers. Pass NULL to retrieve root-level folders.
     */
    @Query(
        """
        SELECT * FROM ${BookmarkFolderEntity.TABLE_NAME}
        WHERE (:parentId IS NULL AND ${BookmarkFolderEntity.COL_PARENT_ID} IS NULL)
           OR ${BookmarkFolderEntity.COL_PARENT_ID} = :parentId
        ORDER BY ${BookmarkFolderEntity.COL_NAME} ASC, ${BookmarkFolderEntity.COL_ID} ASC
        """,
    )
    fun observeChildren(parentId: Long?): Flow<List<BookmarkFolderEntity>>

    @Query("SELECT COUNT(*) FROM ${BookmarkFolderEntity.TABLE_NAME}")
    suspend fun count(): Int
}
