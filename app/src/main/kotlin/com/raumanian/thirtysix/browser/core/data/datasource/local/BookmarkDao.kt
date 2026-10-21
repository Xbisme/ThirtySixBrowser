package com.raumanian.thirtysix.browser.core.data.datasource.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.raumanian.thirtysix.browser.core.data.datasource.local.entity.BookmarkEntity
import kotlinx.coroutines.flow.Flow

@Dao
@Suppress("TooManyFunctions") // One DAO serves the folder tree, search, the star toggle and Home.
interface BookmarkDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(bookmark: BookmarkEntity): Long

    @Update
    suspend fun update(bookmark: BookmarkEntity)

    @Delete
    suspend fun delete(bookmark: BookmarkEntity)

    @Query("SELECT * FROM ${BookmarkEntity.TABLE_NAME} WHERE ${BookmarkEntity.COL_ID} = :id")
    suspend fun getById(id: Long): BookmarkEntity?

    /** Bookmarks in [folderId] by sort order; `null` gives the root level. */
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

    @Query(
        """
        SELECT * FROM ${BookmarkEntity.TABLE_NAME}
        ORDER BY ${BookmarkEntity.COL_CREATED_AT} DESC, ${BookmarkEntity.COL_ID} DESC
        LIMIT :limit
        """,
    )
    fun observeRecent(limit: Int): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM ${BookmarkEntity.TABLE_NAME} WHERE ${BookmarkEntity.COL_URL} = :url")
    fun observeByUrl(url: String): Flow<List<BookmarkEntity>>

    /** For address suggestions, which filter in memory: no user text ever reaches a `LIKE` query. */
    @Query(
        """
        SELECT * FROM ${BookmarkEntity.TABLE_NAME}
        ORDER BY ${BookmarkEntity.COL_CREATED_AT} DESC, ${BookmarkEntity.COL_ID} DESC
        """,
    )
    suspend fun getAllNewestFirst(): List<BookmarkEntity>

    @Query("SELECT COUNT(*) FROM ${BookmarkEntity.TABLE_NAME} WHERE ${BookmarkEntity.COL_URL} = :url")
    suspend fun countByUrl(url: String): Int

    /** Un-starring removes only the newest bookmark with [url]; older copies stay. */
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

    @Query("DELETE FROM ${BookmarkEntity.TABLE_NAME} WHERE ${BookmarkEntity.COL_PARENT_FOLDER_ID} = :folderId")
    suspend fun deleteByParentFolder(folderId: Long): Int

    /** Case-insensitive; `%` and `_` in [query] act as wildcards. */
    @Query(
        """
        SELECT * FROM ${BookmarkEntity.TABLE_NAME}
         WHERE LOWER(${BookmarkEntity.COL_TITLE}) LIKE '%' || LOWER(:query) || '%'
            OR LOWER(${BookmarkEntity.COL_URL})   LIKE '%' || LOWER(:query) || '%'
         ORDER BY ${BookmarkEntity.COL_CREATED_AT} DESC, ${BookmarkEntity.COL_ID} DESC
         LIMIT :limit
        """,
    )
    fun searchByTitleOrUrl(query: String, limit: Int): Flow<List<BookmarkEntity>>

    @Query("SELECT COUNT(*) FROM ${BookmarkEntity.TABLE_NAME} WHERE ${BookmarkEntity.COL_PARENT_FOLDER_ID} = :folderId")
    suspend fun countByFolder(folderId: Long): Int
}
