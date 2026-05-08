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
@Suppress("TooManyFunctions") // Spec 013 added 6 query/mutation methods on top of Spec 005 baseline.
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

    /**
     * Spec 013 — bookmarks whose URL matches exactly. Powers
     * `IsUrlBookmarkedUseCase` (FR-002 star fill state) reactively.
     */
    @Query("SELECT * FROM ${BookmarkEntity.TABLE_NAME} WHERE ${BookmarkEntity.COL_URL} = :url")
    fun observeByUrl(url: String): Flow<List<BookmarkEntity>>

    /** Spec 013 — synchronous count gate inside `ToggleBookmarkUseCase` (R9). */
    @Query("SELECT COUNT(*) FROM ${BookmarkEntity.TABLE_NAME} WHERE ${BookmarkEntity.COL_URL} = :url")
    suspend fun countByUrl(url: String): Int

    /**
     * Spec 013 — deletes the most recently created bookmark with the given URL.
     * Used by the Q4 star-toggle "remove most recent" semantic. Returns the
     * number of rows deleted (0 or 1).
     */
    @Query(
        """
        DELETE FROM ${BookmarkEntity.TABLE_NAME}
         WHERE ${BookmarkEntity.COL_URL} = :url
           AND ${BookmarkEntity.COL_ID} = (
             SELECT ${BookmarkEntity.COL_ID} FROM ${BookmarkEntity.TABLE_NAME}
              WHERE ${BookmarkEntity.COL_URL} = :url
              ORDER BY ${BookmarkEntity.COL_CREATED_AT} DESC, ${BookmarkEntity.COL_ID} DESC
              LIMIT 1
           )
        """,
    )
    suspend fun deleteMostRecentByUrl(url: String): Int

    /** Spec 013 — leaf delete used by the cascade-delete walk in R7. */
    @Query("DELETE FROM ${BookmarkEntity.TABLE_NAME} WHERE ${BookmarkEntity.COL_PARENT_FOLDER_ID} = :folderId")
    suspend fun deleteByParentFolder(folderId: Long): Int

    /** Spec 013 — substring search across title + url, case-insensitive. */
    @Query(
        """
        SELECT * FROM ${BookmarkEntity.TABLE_NAME}
         WHERE LOWER(${BookmarkEntity.COL_TITLE}) LIKE '%' || LOWER(:query) || '%'
            OR LOWER(${BookmarkEntity.COL_URL})   LIKE '%' || LOWER(:query) || '%'
         ORDER BY ${BookmarkEntity.COL_CREATED_AT} DESC, ${BookmarkEntity.COL_ID} DESC
        """,
    )
    fun searchByTitleOrUrl(query: String): Flow<List<BookmarkEntity>>

    /** Spec 013 — count of bookmarks under a folder. Powers descendant tally (R8). */
    @Query("SELECT COUNT(*) FROM ${BookmarkEntity.TABLE_NAME} WHERE ${BookmarkEntity.COL_PARENT_FOLDER_ID} = :folderId")
    suspend fun countByFolder(folderId: Long): Int
}
