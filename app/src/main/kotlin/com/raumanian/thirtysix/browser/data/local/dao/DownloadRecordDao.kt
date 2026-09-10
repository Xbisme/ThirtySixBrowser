package com.raumanian.thirtysix.browser.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.raumanian.thirtysix.browser.data.local.entity.DownloadRecordEntity
import kotlinx.coroutines.flow.Flow

/**
 * Spec 015 — DAO for the `download_records` table introduced at schema v2.
 */
@Dao
interface DownloadRecordDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(record: DownloadRecordEntity): Long

    /**
     * Spec 015 FR-019 — newest first. `id DESC` is the tiebreaker so two records created
     * in the same millisecond still have a stable, deterministic order; without it the
     * list could reshuffle between emissions and Compose would animate a phantom move.
     */
    @Query(
        """
        SELECT * FROM ${DownloadRecordEntity.TABLE_NAME}
        ORDER BY ${DownloadRecordEntity.COL_CREATED_AT} DESC, ${DownloadRecordEntity.COL_ID} DESC
        """,
    )
    fun observeAll(): Flow<List<DownloadRecordEntity>>

    @Query("SELECT * FROM ${DownloadRecordEntity.TABLE_NAME} WHERE ${DownloadRecordEntity.COL_ID} = :id")
    suspend fun getById(id: Long): DownloadRecordEntity?

    /**
     * Spec 015 FR-014 — record where the finished file landed **and what it is actually
     * called**, once the transfer completes.
     *
     * Both are durable metadata, not live transfer state, so this does not violate FR-015.
     * The name is written alongside the URI because the platform de-duplicates colliding
     * filenames (FR-007): three downloads of `dup.txt` land as `dup.txt`, `dup-1.txt` and
     * `dup-2.txt`, and until the resolved name is stored every one of those rows both
     * displays and probes for the same wrong file.
     *
     * @return rows updated: 0 if the record was removed in between, 1 otherwise.
     */
    @Query(
        "UPDATE ${DownloadRecordEntity.TABLE_NAME} SET ${DownloadRecordEntity.COL_LOCAL_URI} = :localUri, " +
            "${DownloadRecordEntity.COL_FILE_NAME} = :fileName WHERE ${DownloadRecordEntity.COL_ID} = :id",
    )
    suspend fun updateCompletionMetadata(id: Long, localUri: String, fileName: String): Int

    /**
     * Spec 015 FR-031 — remove the record only. The downloaded file is untouched.
     *
     * @return rows removed: 0 if the id was already gone, 1 otherwise.
     */
    @Query("DELETE FROM ${DownloadRecordEntity.TABLE_NAME} WHERE ${DownloadRecordEntity.COL_ID} = :id")
    suspend fun deleteById(id: Long): Int

    @Query("SELECT COUNT(*) FROM ${DownloadRecordEntity.TABLE_NAME}")
    suspend fun count(): Int
}
