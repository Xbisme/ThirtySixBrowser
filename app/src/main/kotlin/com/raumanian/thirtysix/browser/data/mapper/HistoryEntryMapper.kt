package com.raumanian.thirtysix.browser.data.mapper

import com.raumanian.thirtysix.browser.data.local.entity.HistoryEntryEntity
import com.raumanian.thirtysix.browser.domain.model.HistoryEntry

/**
 * Spec 014 — Entity ↔ Domain converter for [HistoryEntryEntity] / [HistoryEntry].
 * Total round-trip: `toEntity(toDomain(e)) == e` for any valid entity. No
 * defensive validation needed — DB columns are NOT NULL.
 */
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
