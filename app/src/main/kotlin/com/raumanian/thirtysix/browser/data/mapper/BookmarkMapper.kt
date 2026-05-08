package com.raumanian.thirtysix.browser.data.mapper

import com.raumanian.thirtysix.browser.data.local.entity.BookmarkEntity
import com.raumanian.thirtysix.browser.domain.model.Bookmark

/**
 * Spec 013 — Entity ↔ Domain converter for [BookmarkEntity] / [Bookmark].
 * Total round-trip: `toEntity(toDomain(e)) == e` for any valid entity.
 */
object BookmarkMapper {

    fun toDomain(entity: BookmarkEntity): Bookmark =
        Bookmark(
            id = entity.id,
            title = entity.title,
            url = entity.url,
            parentFolderId = entity.parentFolderId,
            createdAt = entity.createdAt,
            sortOrder = entity.sortOrder,
        )

    fun toEntity(model: Bookmark): BookmarkEntity =
        BookmarkEntity(
            id = model.id,
            title = model.title,
            url = model.url,
            parentFolderId = model.parentFolderId,
            createdAt = model.createdAt,
            sortOrder = model.sortOrder,
        )
}
