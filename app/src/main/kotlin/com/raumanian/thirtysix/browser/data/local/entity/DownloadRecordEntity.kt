package com.raumanian.thirtysix.browser.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Spec 015 FR-014 — persisted identity + metadata for one download.
 *
 * The table this entity introduces is the reason `AppDatabase.SCHEMA_VERSION` moves from
 * 1 to 2 — the project's first real migration. It is strictly additive: no existing table
 * is referenced, so existing user data cannot be affected by construction.
 *
 * Deliberate omissions, each load-bearing:
 * - **No live transfer state** (progress, byte counts, running/paused). Read from the
 *   platform at display time and never written down (FR-015, FR-024c).
 * - **No incognito column** (FR-014a). Incognito downloads are recorded identically.
 * - **No unique constraint on [transferHandle]** — the platform allocates it, and a stale
 *   value from a service that reset its counters must never make an insert fail and lose
 *   the user's download.
 * - **No foreign keys.** A download is not owned by a tab, bookmark or history entry, and
 *   outlives all of them.
 * - **No index on [transferHandle]** — lookups run the other way (records drive queries
 *   to the platform), and at the ~500-record envelope an index would cost writes to save
 *   nothing measurable.
 */
@Entity(
    tableName = DownloadRecordEntity.TABLE_NAME,
    indices = [
        Index(value = [DownloadRecordEntity.COL_CREATED_AT], name = DownloadRecordEntity.INDEX_CREATED_AT),
    ],
)
data class DownloadRecordEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = COL_ID)
    val id: Long = 0L,

    @ColumnInfo(name = COL_SOURCE_URL)
    val sourceUrl: String,

    @ColumnInfo(name = COL_FILE_NAME)
    val fileName: String,

    @ColumnInfo(name = COL_MIME_TYPE)
    val mimeType: String,

    @ColumnInfo(name = COL_CREATED_AT)
    val createdAt: Long,

    @ColumnInfo(name = COL_TRANSFER_HANDLE)
    val transferHandle: Long,

    /** Null while the transfer is in flight; set once the file has landed. */
    @ColumnInfo(name = COL_LOCAL_URI)
    val localUri: String?,
) {
    companion object {
        const val TABLE_NAME = "download_records"
        const val COL_ID = "id"
        const val COL_SOURCE_URL = "source_url"
        const val COL_FILE_NAME = "file_name"
        const val COL_MIME_TYPE = "mime_type"
        const val COL_CREATED_AT = "created_at"
        const val COL_TRANSFER_HANDLE = "transfer_handle"
        const val COL_LOCAL_URI = "local_uri"
        const val INDEX_CREATED_AT = "index_download_records_created_at"
    }
}
