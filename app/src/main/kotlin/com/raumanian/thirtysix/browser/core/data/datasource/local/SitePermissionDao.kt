package com.raumanian.thirtysix.browser.core.data.datasource.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.raumanian.thirtysix.browser.core.data.datasource.local.entity.SitePermissionEntity
import kotlinx.coroutines.flow.Flow

/** Remembered site decisions. One row per `(origin, capability)`; writing again replaces it. */
@Dao
interface SitePermissionDao {
    @Upsert
    suspend fun upsert(entity: SitePermissionEntity)

    @Query(
        "SELECT * FROM ${SitePermissionEntity.TABLE_NAME} " +
            "ORDER BY ${SitePermissionEntity.COL_ORIGIN}, ${SitePermissionEntity.COL_CAPABILITY}",
    )
    fun observeAll(): Flow<List<SitePermissionEntity>>

    @Query("SELECT * FROM ${SitePermissionEntity.TABLE_NAME} WHERE ${SitePermissionEntity.COL_ORIGIN} = :origin")
    suspend fun forOrigin(origin: String): List<SitePermissionEntity>

    @Query("DELETE FROM ${SitePermissionEntity.TABLE_NAME} WHERE ${SitePermissionEntity.COL_ORIGIN} = :origin")
    suspend fun deleteOrigin(origin: String): Int

    @Query("DELETE FROM ${SitePermissionEntity.TABLE_NAME}")
    suspend fun deleteAll(): Int
}
