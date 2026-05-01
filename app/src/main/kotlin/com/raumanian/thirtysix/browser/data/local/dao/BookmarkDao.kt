package com.raumanian.thirtysix.browser.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.raumanian.thirtysix.browser.data.local.entity.BookmarkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(bookmark: BookmarkEntity): Long

    @Update
    suspend fun update(bookmark: BookmarkEntity)

    @Delete
    suspend fun delete(bookmark: BookmarkEntity)

    @Query("SELECT * FROM ${BookmarkEntity.TABLE_NAME} WHERE ${BookmarkEntity.COL_ID} = :id")
    suspend fun getById(id: Long): BookmarkEntity?

    /**
     * All bookmarks under a given folder, ordered by sort_order ascending.
     * Pass NULL to retrieve root-level bookmarks (parent_folder_id IS NULL).
     */
    @Query(
        """
        SELECT * FROM ${BookmarkEntity.TABLE_NAME}
        WHERE (:folderId IS NULL AND ${BookmarkEntity.COL_PARENT_FOLDER_ID} IS NULL)
           OR ${BookmarkEntity.COL_PARENT_FOLDER_ID} = :folderId
        ORDER BY ${BookmarkEntity.COL_SORT_ORDER} ASC, ${BookmarkEntity.COL_ID} ASC
        """,
    )
    fun observeByFolder(folderId: Long?): Flow<List<BookmarkEntity>>

    @Query("SELECT COUNT(*) FROM ${BookmarkEntity.TABLE_NAME}")
    suspend fun count(): Int
}
