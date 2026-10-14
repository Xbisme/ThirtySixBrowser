package com.raumanian.thirtysix.browser.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.raumanian.thirtysix.browser.data.local.entity.HistoryEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
@Suppress("TooManyFunctions") // Spec 019 added observeRecent for the Home page on top of Specs 005/014.
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
     * Spec 022 data-model §4 — one row per address, the one from its most recent visit, newest
     * first: the history half of the address-suggestion snapshot.
     *
     * One pass with `GROUP BY` instead of a correlated subquery, which would scan the table once
     * per row. With a lone `MAX()` aggregate, SQLite takes the other columns from the row that
     * holds the maximum (documented "bare column" behaviour). No user text reaches this query, so
     * there is no `LIKE` anywhere on the suggestion path (FR-017).
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

    /** Spec 024 data-model step 1 — every distinct recorded address, for Fire's host collection. */
    @Query("SELECT DISTINCT ${HistoryEntryEntity.COL_URL} FROM ${HistoryEntryEntity.TABLE_NAME}")
    suspend fun getDistinctUrls(): List<String>

    /**
     * Spec 019 (data-model §4) — the newest [limit] visits for the Home page. Served by the
     * existing `visited_at` index, so it never scans the whole table the way [observeAll] does.
     */
    @Query(
        """
        SELECT * FROM ${HistoryEntryEntity.TABLE_NAME}
        ORDER BY ${HistoryEntryEntity.COL_VISITED_AT} DESC, ${HistoryEntryEntity.COL_ID} DESC
        LIMIT :limit
        """,
    )
    fun observeRecent(limit: Int): Flow<List<HistoryEntryEntity>>

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
