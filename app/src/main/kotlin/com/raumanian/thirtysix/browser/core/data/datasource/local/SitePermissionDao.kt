package com.raumanian.thirtysix.browser.core.data.datasource.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
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

    /** All or none: chunked under SQLite's 999 bound-argument limit of older Android versions. */
    @Transaction
    suspend fun deleteOrigins(origins: List<String>): Int =
        origins.chunked(ORIGIN_DELETE_CHUNK).sumOf { chunk -> deleteOriginChunk(chunk) }

    @Query("DELETE FROM ${SitePermissionEntity.TABLE_NAME} WHERE ${SitePermissionEntity.COL_ORIGIN} IN (:origins)")
    suspend fun deleteOriginChunk(origins: List<String>): Int

    @Query("DELETE FROM ${SitePermissionEntity.TABLE_NAME}")
    suspend fun deleteAll(): Int
}

private const val ORIGIN_DELETE_CHUNK = 500
