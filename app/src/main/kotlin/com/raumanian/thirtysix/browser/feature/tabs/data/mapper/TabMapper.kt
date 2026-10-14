package com.raumanian.thirtysix.browser.feature.tabs.data.mapper

import com.raumanian.thirtysix.browser.core.data.datasource.local.entity.TabEntity
import com.raumanian.thirtysix.browser.core.domain.model.Tab
/**
 * Room holds only normal tabs, so [TabEntity.toDomain] always yields `isIncognito = false` and
 * [Tab.toEntity] rejects an incognito tab rather than persist it.
 */
fun TabEntity.toDomain(): Tab = Tab(
    id = id,
    url = url,
    title = title,
    position = position,
    createdAt = createdAt,
    lastActiveAt = lastActiveAt,
    isIncognito = false,
)

fun Tab.toEntity(): TabEntity {
    require(!isIncognito) {
        "An incognito tab must never be persisted"
    }
    return TabEntity(
        id = id,
        url = url,
        title = title,
        position = position,
        createdAt = createdAt,
        lastActiveAt = lastActiveAt,
    )
}
