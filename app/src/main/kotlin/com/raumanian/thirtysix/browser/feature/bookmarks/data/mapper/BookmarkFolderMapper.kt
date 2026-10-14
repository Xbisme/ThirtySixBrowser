package com.raumanian.thirtysix.browser.feature.bookmarks.data.mapper

import com.raumanian.thirtysix.browser.core.data.datasource.local.entity.BookmarkFolderEntity
import com.raumanian.thirtysix.browser.core.domain.model.BookmarkFolder

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
