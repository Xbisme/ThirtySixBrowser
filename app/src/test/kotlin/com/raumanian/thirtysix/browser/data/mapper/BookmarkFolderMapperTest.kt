package com.raumanian.thirtysix.browser.data.mapper

import com.raumanian.thirtysix.browser.data.local.entity.BookmarkFolderEntity
import com.raumanian.thirtysix.browser.domain.model.BookmarkFolder
import org.junit.Assert.assertEquals
import org.junit.Test

class BookmarkFolderMapperTest {

    @Test
    fun `entity round-trips with parent`() {
        val entity = BookmarkFolderEntity(
            id = 3L,
            name = "Work",
            parentId = 1L,
            createdAt = 100L,
        )
        val back = BookmarkFolderMapper.toEntity(BookmarkFolderMapper.toDomain(entity))
        assertEquals(entity, back)
    }

    @Test
    fun `root folder (null parent) round-trips`() {
        val entity = BookmarkFolderEntity(
            id = 1L,
            name = "Root",
            parentId = null,
            createdAt = 50L,
        )
        val back = BookmarkFolderMapper.toEntity(BookmarkFolderMapper.toDomain(entity))
        assertEquals(entity, back)
    }

    @Test
    fun `domain round-trips`() {
        val domain = BookmarkFolder(id = 9L, name = "X", parentId = 4L, createdAt = 1L)
        val back = BookmarkFolderMapper.toDomain(BookmarkFolderMapper.toEntity(domain))
        assertEquals(domain, back)
    }
}
