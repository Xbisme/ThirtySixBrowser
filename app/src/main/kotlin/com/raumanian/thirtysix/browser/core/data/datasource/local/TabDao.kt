package com.raumanian.thirtysix.browser.core.data.datasource.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.raumanian.thirtysix.browser.core.data.datasource.local.entity.TabEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TabDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(tab: TabEntity): Long

    @Delete
    suspend fun delete(tab: TabEntity)

    @Query("SELECT * FROM ${TabEntity.TABLE_NAME} WHERE ${TabEntity.COL_ID} = :id")
    suspend fun getById(id: Long): TabEntity?

    @Query("UPDATE ${TabEntity.TABLE_NAME} SET ${TabEntity.COL_LAST_ACTIVE_AT} = :at WHERE ${TabEntity.COL_ID} = :id")
    suspend fun setLastActiveAt(id: Long, at: Long)

    // An unchanged row is not written, so observers are not woken for nothing.
    @Query(
        """
        UPDATE ${TabEntity.TABLE_NAME} SET ${TabEntity.COL_URL} = :url, ${TabEntity.COL_TITLE} = :title
        WHERE ${TabEntity.COL_ID} = :id AND (${TabEntity.COL_URL} != :url OR ${TabEntity.COL_TITLE} != :title)
        """,
    )
    suspend fun setUrlAndTitle(id: Long, url: String, title: String)

    @Query(
        """
        SELECT * FROM ${TabEntity.TABLE_NAME}
        ORDER BY ${TabEntity.COL_POSITION} ASC, ${TabEntity.COL_ID} ASC
        """,
    )
    fun observeAll(): Flow<List<TabEntity>>

    @Query(
        """
        SELECT * FROM ${TabEntity.TABLE_NAME}
        ORDER BY ${TabEntity.COL_POSITION} ASC, ${TabEntity.COL_ID} ASC
        """,
    )
    suspend fun getAll(): List<TabEntity>

    @Query("SELECT COUNT(*) FROM ${TabEntity.TABLE_NAME}")
    suspend fun count(): Int

    @Query("SELECT MAX(${TabEntity.COL_POSITION}) FROM ${TabEntity.TABLE_NAME}")
    suspend fun maxPosition(): Int?

    @Query("DELETE FROM ${TabEntity.TABLE_NAME}")
    suspend fun deleteAll(): Int
}
