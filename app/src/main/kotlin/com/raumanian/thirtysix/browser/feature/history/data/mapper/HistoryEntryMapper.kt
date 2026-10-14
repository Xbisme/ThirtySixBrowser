package com.raumanian.thirtysix.browser.feature.history.data.mapper

import com.raumanian.thirtysix.browser.core.data.datasource.local.entity.HistoryEntryEntity
import com.raumanian.thirtysix.browser.core.domain.model.HistoryEntry
object HistoryEntryMapper {

    fun toDomain(entity: HistoryEntryEntity): HistoryEntry =
        HistoryEntry(
            id = entity.id,
            url = entity.url,
            title = entity.title,
            visitedAt = entity.visitedAt,
        )

    fun toEntity(model: HistoryEntry): HistoryEntryEntity =
        HistoryEntryEntity(
            id = model.id,
            url = model.url,
            title = model.title,
            visitedAt = model.visitedAt,
        )
}
