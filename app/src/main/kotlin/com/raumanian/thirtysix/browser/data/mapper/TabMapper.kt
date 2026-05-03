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
 *
 * Spec 012 invariant: [TabEntity.toDomain] always emits `isIncognito = false`
 * because Room is the persistence-of-record only for normal tabs. Incognito
 * tabs live entirely in `IncognitoTabRepository`'s in-memory state and never
 * round-trip through this mapper. [Tab.toEntity] defensively rejects an
 * `isIncognito = true` input via [require] — a guard against the regression
 * scenario where a future caller accidentally tries to persist an incognito tab
 * (FR-002 protection).
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
        "Spec 012 FR-002 violation: incognito Tab must never be persisted to TabEntity"
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
