package com.raumanian.thirtysix.browser.core.data.datasource.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.raumanian.thirtysix.browser.core.data.datasource.local.entity.TabSessionEntity

/** Normal tabs' saved back and forward. A row goes with its tab (foreign key cascade). */
@Dao
interface TabSessionDao {
    @Upsert
    suspend fun upsert(entity: TabSessionEntity)

    @Query("SELECT * FROM ${TabSessionEntity.TABLE_NAME} WHERE ${TabSessionEntity.COL_TAB_ID} = :tabId")
    suspend fun get(tabId: Long): TabSessionEntity?

    @Query("DELETE FROM ${TabSessionEntity.TABLE_NAME} WHERE ${TabSessionEntity.COL_TAB_ID} = :tabId")
    suspend fun delete(tabId: Long): Int

    @Query("DELETE FROM ${TabSessionEntity.TABLE_NAME} WHERE ${TabSessionEntity.COL_TAB_ID} NOT IN (:tabIds)")
    suspend fun deleteNotIn(tabIds: List<Long>): Int

    @Query("DELETE FROM ${TabSessionEntity.TABLE_NAME}")
    suspend fun deleteAll(): Int
}
