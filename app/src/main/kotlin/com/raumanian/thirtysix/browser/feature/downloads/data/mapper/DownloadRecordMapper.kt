package com.raumanian.thirtysix.browser.feature.downloads.data.mapper

import com.raumanian.thirtysix.browser.core.data.datasource.local.entity.DownloadRecordEntity
import com.raumanian.thirtysix.browser.core.domain.model.DownloadRecord
// A null `local_uri` means "still in flight", so it is carried through rather than coerced.
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
