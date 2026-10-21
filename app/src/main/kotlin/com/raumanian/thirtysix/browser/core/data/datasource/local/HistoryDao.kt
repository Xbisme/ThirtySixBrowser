package com.raumanian.thirtysix.browser.core.data.datasource.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.raumanian.thirtysix.browser.core.data.datasource.local.entity.HistoryEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
@Suppress("TooManyFunctions") // One DAO serves the history list, Home, suggestions, Fire and retention.
interface HistoryDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entry: HistoryEntryEntity): Long

    @Query(
        """
        SELECT * FROM ${HistoryEntryEntity.TABLE_NAME}
        ORDER BY ${HistoryEntryEntity.COL_VISITED_AT} DESC, ${HistoryEntryEntity.COL_ID} DESC
        """,
    )
    fun observeAll(): Flow<List<HistoryEntryEntity>>

    /**
     * Each address's latest visit, newest first, for suggestions. With a lone `MAX()`, SQLite takes the
     * bare columns from the row holding the maximum, so one `GROUP BY` pass replaces a correlated subquery.
     */
    @Query(
        """
        SELECT ${HistoryEntryEntity.COL_ID}, ${HistoryEntryEntity.COL_URL}, ${HistoryEntryEntity.COL_TITLE},
               MAX(${HistoryEntryEntity.COL_VISITED_AT}) AS ${HistoryEntryEntity.COL_VISITED_AT}
          FROM ${HistoryEntryEntity.TABLE_NAME}
         GROUP BY ${HistoryEntryEntity.COL_URL}
         ORDER BY ${HistoryEntryEntity.COL_VISITED_AT} DESC, ${HistoryEntryEntity.COL_ID} DESC
        """,
    )
    suspend fun getLatestVisitPerUrl(): List<HistoryEntryEntity>

    @Query("SELECT DISTINCT ${HistoryEntryEntity.COL_URL} FROM ${HistoryEntryEntity.TABLE_NAME}")
    suspend fun getDistinctUrls(): List<String>

    /** Served by the `visited_at` index, so unlike [observeAll] it never reads the whole table. */
    @Query(
        """
        SELECT * FROM ${HistoryEntryEntity.TABLE_NAME}
        ORDER BY ${HistoryEntryEntity.COL_VISITED_AT} DESC, ${HistoryEntryEntity.COL_ID} DESC
        LIMIT :limit
        """,
    )
    fun observeRecent(limit: Int): Flow<List<HistoryEntryEntity>>

    @Query(
        """
        DELETE FROM ${HistoryEntryEntity.TABLE_NAME}
        WHERE ${HistoryEntryEntity.COL_VISITED_AT} BETWEEN :fromInclusive AND :toInclusive
        """,
    )
    suspend fun deleteInRange(fromInclusive: Long, toInclusive: Long): Int

    @Query("DELETE FROM ${HistoryEntryEntity.TABLE_NAME}")
    suspend fun deleteAll(): Int

    @Query("SELECT COUNT(*) FROM ${HistoryEntryEntity.TABLE_NAME}")
    suspend fun count(): Int

    @Query("DELETE FROM ${HistoryEntryEntity.TABLE_NAME} WHERE ${HistoryEntryEntity.COL_ID} = :id")
    suspend fun deleteById(id: Long): Int

    /** The page title often arrives after page-finish, so rows are recorded first and their title patched here. */
    @Query(
        "UPDATE ${HistoryEntryEntity.TABLE_NAME} SET ${HistoryEntryEntity.COL_TITLE} = :title " +
            "WHERE ${HistoryEntryEntity.COL_ID} = :id",
    )
    suspend fun updateTitle(id: Long, title: String): Int
}
