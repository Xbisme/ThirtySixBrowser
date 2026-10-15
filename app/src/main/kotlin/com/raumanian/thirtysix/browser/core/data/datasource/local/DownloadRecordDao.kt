package com.raumanian.thirtysix.browser.core.data.datasource.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.raumanian.thirtysix.browser.core.data.datasource.local.entity.DownloadRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadRecordDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(record: DownloadRecordEntity): Long

    /** Newest first; `id` breaks same-millisecond ties so the list never reshuffles between emissions. */
    @Query(
        """
        SELECT * FROM ${DownloadRecordEntity.TABLE_NAME}
        ORDER BY ${DownloadRecordEntity.COL_CREATED_AT} DESC, ${DownloadRecordEntity.COL_ID} DESC
        """,
    )
    fun observeAll(): Flow<List<DownloadRecordEntity>>

    @Query("SELECT * FROM ${DownloadRecordEntity.TABLE_NAME} WHERE ${DownloadRecordEntity.COL_ID} = :id")
    suspend fun getById(id: Long): DownloadRecordEntity?

    /** Stores where the finished file landed and its final name: the platform renames colliding files. */
    @Query(
        "UPDATE ${DownloadRecordEntity.TABLE_NAME} SET ${DownloadRecordEntity.COL_LOCAL_URI} = :localUri, " +
            "${DownloadRecordEntity.COL_FILE_NAME} = :fileName WHERE ${DownloadRecordEntity.COL_ID} = :id",
    )
    suspend fun updateCompletionMetadata(id: Long, localUri: String, fileName: String): Int

    /** Removes the record only; the downloaded file stays. */
    @Query("DELETE FROM ${DownloadRecordEntity.TABLE_NAME} WHERE ${DownloadRecordEntity.COL_ID} = :id")
    suspend fun deleteById(id: Long): Int

    @Query("SELECT COUNT(*) FROM ${DownloadRecordEntity.TABLE_NAME}")
    suspend fun count(): Int
}
