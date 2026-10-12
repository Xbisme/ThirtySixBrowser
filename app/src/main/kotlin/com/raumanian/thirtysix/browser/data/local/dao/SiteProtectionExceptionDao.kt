package com.raumanian.thirtysix.browser.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.raumanian.thirtysix.browser.data.local.entity.SiteProtectionExceptionEntity
import kotlinx.coroutines.flow.Flow

/** Spec 023 — sites without protection. One row per site; writing again replaces it. */
@Dao
interface SiteProtectionExceptionDao {
    @Upsert
    suspend fun upsert(entity: SiteProtectionExceptionEntity)

    @Query(
        "SELECT * FROM ${SiteProtectionExceptionEntity.TABLE_NAME} " +
            "ORDER BY ${SiteProtectionExceptionEntity.COL_ADDED_AT} DESC",
    )
    fun observeAll(): Flow<List<SiteProtectionExceptionEntity>>

    @Query(
        "DELETE FROM ${SiteProtectionExceptionEntity.TABLE_NAME} " +
            "WHERE ${SiteProtectionExceptionEntity.COL_SITE} = :site",
    )
    suspend fun delete(site: String): Int

    @Query("DELETE FROM ${SiteProtectionExceptionEntity.TABLE_NAME}")
    suspend fun deleteAll(): Int
}
