package com.raumanian.thirtysix.browser.core.data.datasource.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.raumanian.thirtysix.browser.core.data.datasource.local.entity.FireproofSiteEntity
import kotlinx.coroutines.flow.Flow

/** Fireproof sites. One row per site; writing again replaces it. */
@Dao
interface FireproofSiteDao {
    @Upsert
    suspend fun upsert(entity: FireproofSiteEntity)

    @Query(
        "SELECT * FROM ${FireproofSiteEntity.TABLE_NAME} " +
            "ORDER BY ${FireproofSiteEntity.COL_ADDED_AT} DESC",
    )
    fun observeAll(): Flow<List<FireproofSiteEntity>>

    @Query("SELECT * FROM ${FireproofSiteEntity.TABLE_NAME}")
    suspend fun getAll(): List<FireproofSiteEntity>

    @Query(
        "DELETE FROM ${FireproofSiteEntity.TABLE_NAME} " +
            "WHERE ${FireproofSiteEntity.COL_SITE} = :site",
    )
    suspend fun delete(site: String): Int

    @Query("DELETE FROM ${FireproofSiteEntity.TABLE_NAME}")
    suspend fun deleteAll(): Int
}
