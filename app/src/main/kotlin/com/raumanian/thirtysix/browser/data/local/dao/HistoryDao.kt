package com.raumanian.thirtysix.browser.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.raumanian.thirtysix.browser.data.local.entity.HistoryEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entry: HistoryEntryEntity): Long

    @Delete
    suspend fun delete(entry: HistoryEntryEntity)

    @Query("SELECT * FROM ${HistoryEntryEntity.TABLE_NAME} WHERE ${HistoryEntryEntity.COL_ID} = :id")
    suspend fun getById(id: Long): HistoryEntryEntity?

    /**
     * Most-recent-first paginated history list (relies on `index_history_entries_visited_at`).
     */
    @Query(
        """
        SELECT * FROM ${HistoryEntryEntity.TABLE_NAME}
        ORDER BY ${HistoryEntryEntity.COL_VISITED_AT} DESC, ${HistoryEntryEntity.COL_ID} DESC
        LIMIT :limit OFFSET :offset
        """,
    )
    suspend fun getRecent(limit: Int, offset: Int): List<HistoryEntryEntity>

    @Query(
        """
        SELECT * FROM ${HistoryEntryEntity.TABLE_NAME}
        ORDER BY ${HistoryEntryEntity.COL_VISITED_AT} DESC, ${HistoryEntryEntity.COL_ID} DESC
        """,
    )
    fun observeAll(): Flow<List<HistoryEntryEntity>>

    /**
     * Range delete (for "Clear last hour / today / all"). Pass MIN/MAX to clear all.
     */
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

    /**
     * Spec 014 FR-020 — single-row delete by id. Cleaner ergonomics than the
     * `getById(id) → delete(entity)` round-trip the existing [delete] needs.
     * Returns the number of rows removed: 0 if id no longer present, 1 if removed.
     */
    @Query("DELETE FROM ${HistoryEntryEntity.TABLE_NAME} WHERE ${HistoryEntryEntity.COL_ID} = :id")
    suspend fun deleteById(id: Long): Int

    /**
     * Spec 014 FR-001 (title backfill) — patch the title of an already-recorded row.
     *
     * `WebChromeClient.onReceivedTitle` frequently arrives *after*
     * `WebViewClient.onPageFinished`, so the row is inserted with whatever title was
     * known at page-finish (often empty) and corrected here the moment the real title
     * lands. Returns rows updated: 0 if the row was deleted in between, 1 otherwise.
     */
    @Query(
        "UPDATE ${HistoryEntryEntity.TABLE_NAME} SET ${HistoryEntryEntity.COL_TITLE} = :title " +
            "WHERE ${HistoryEntryEntity.COL_ID} = :id",
    )
    suspend fun updateTitle(id: Long, title: String): Int
}
