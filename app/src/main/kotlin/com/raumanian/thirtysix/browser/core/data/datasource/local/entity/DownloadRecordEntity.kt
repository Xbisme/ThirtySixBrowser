package com.raumanian.thirtysix.browser.core.data.datasource.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One download's identity and metadata; live transfer state is read from the platform, never stored.
 * [transferHandle] is not unique: a download service that reset its counters must never make an
 * insert fail and lose the download. Incognito downloads are recorded the same way.
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
