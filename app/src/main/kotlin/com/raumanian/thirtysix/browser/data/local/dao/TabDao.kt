package com.raumanian.thirtysix.browser.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.raumanian.thirtysix.browser.data.local.entity.TabEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TabDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(tab: TabEntity): Long

    @Update
    suspend fun update(tab: TabEntity)

    @Delete
    suspend fun delete(tab: TabEntity)

    @Query("SELECT * FROM ${TabEntity.TABLE_NAME} WHERE ${TabEntity.COL_ID} = :id")
    suspend fun getById(id: Long): TabEntity?

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
