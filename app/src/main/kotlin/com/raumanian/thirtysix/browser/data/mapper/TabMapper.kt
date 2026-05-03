package com.raumanian.thirtysix.browser.data.mapper

import com.raumanian.thirtysix.browser.data.local.entity.TabEntity
import com.raumanian.thirtysix.browser.domain.model.Tab

/**
 * Spec 011 — maps between Spec 005's [TabEntity] (Room) and the domain
 * [Tab] model.
 *
 * Sibling to `SettingsMapper.kt` introduced by Spec 006. Both files live in
 * the established `data/mapper/` package; the top-level extension-function
 * shape here is chosen because the mapping is a pure 1-to-1 field copy with
 * no injected state needed — simpler than the class-with-`@Inject`-constructor
 * shape `SettingsMapper` uses (which exists because `SettingsMapper` may grow
 * to depend on injected formatters / locale services later).
 */
fun TabEntity.toDomain(): Tab = Tab(
    id = id,
    url = url,
    title = title,
    position = position,
    createdAt = createdAt,
    lastActiveAt = lastActiveAt,
)

fun Tab.toEntity(): TabEntity = TabEntity(
    id = id,
    url = url,
    title = title,
    position = position,
    createdAt = createdAt,
    lastActiveAt = lastActiveAt,
)
