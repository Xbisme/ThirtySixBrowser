package com.raumanian.thirtysix.browser.data.mapper

import com.raumanian.thirtysix.browser.data.local.entity.DownloadRecordEntity
import com.raumanian.thirtysix.browser.domain.model.DownloadRecord

/**
 * Spec 015 — Entity ↔ Domain converter for [DownloadRecordEntity] / [DownloadRecord].
 *
 * Total round-trip: `toEntity(toDomain(e)) == e` for any valid entity. No defensive
 * validation is needed — every column except `local_uri` is NOT NULL, and `local_uri`'s
 * nullability is meaningful (null = still in flight) rather than a data-quality accident,
 * so it is carried through unchanged rather than coerced.
 */
object DownloadRecordMapper {

    fun toDomain(entity: DownloadRecordEntity): DownloadRecord =
        DownloadRecord(
            id = entity.id,
            sourceUrl = entity.sourceUrl,
            fileName = entity.fileName,
            mimeType = entity.mimeType,
            createdAt = entity.createdAt,
            transferHandle = entity.transferHandle,
            localUri = entity.localUri,
        )

    fun toEntity(model: DownloadRecord): DownloadRecordEntity =
        DownloadRecordEntity(
            id = model.id,
            sourceUrl = model.sourceUrl,
            fileName = model.fileName,
            mimeType = model.mimeType,
            createdAt = model.createdAt,
            transferHandle = model.transferHandle,
            localUri = model.localUri,
        )
}
