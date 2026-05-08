package com.raumanian.thirtysix.browser.data.mapper

import com.raumanian.thirtysix.browser.data.local.entity.BookmarkFolderEntity
import com.raumanian.thirtysix.browser.domain.model.BookmarkFolder

/**
 * Spec 013 — Entity ↔ Domain converter for [BookmarkFolderEntity] / [BookmarkFolder].
 */
object BookmarkFolderMapper {

    fun toDomain(entity: BookmarkFolderEntity): BookmarkFolder =
        BookmarkFolder(
            id = entity.id,
            name = entity.name,
            parentId = entity.parentId,
            createdAt = entity.createdAt,
        )

    fun toEntity(model: BookmarkFolder): BookmarkFolderEntity =
        BookmarkFolderEntity(
            id = model.id,
            name = model.name,
            parentId = model.parentId,
            createdAt = model.createdAt,
        )
}
