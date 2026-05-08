package com.raumanian.thirtysix.browser.data.mapper

import com.raumanian.thirtysix.browser.data.local.entity.BookmarkEntity
import com.raumanian.thirtysix.browser.domain.model.Bookmark
import org.junit.Assert.assertEquals
import org.junit.Test

class BookmarkMapperTest {

    @Test
    fun `entity to domain to entity round-trips with parent folder`() {
        val entity = BookmarkEntity(
            id = 42L,
            title = "Example",
            url = "https://example.com",
            parentFolderId = 7L,
            createdAt = 1_700_000_000_000L,
            sortOrder = 1_700_000_000_000L,
        )
        val domain = BookmarkMapper.toDomain(entity)
        val back = BookmarkMapper.toEntity(domain)
        assertEquals(entity, back)
    }

    @Test
    fun `null parentFolderId is preserved across round-trip (root bookmark)`() {
        val entity = BookmarkEntity(
            id = 1L,
            title = "Root bm",
            url = "https://root.example.com",
            parentFolderId = null,
            createdAt = 1L,
            sortOrder = 1L,
        )
        val back = BookmarkMapper.toEntity(BookmarkMapper.toDomain(entity))
        assertEquals(entity, back)
    }

    @Test
    fun `domain to entity to domain also round-trips`() {
        val domain = Bookmark(
            id = 5L,
            title = "Domain side",
            url = "https://domain.example.com",
            parentFolderId = null,
            createdAt = 100L,
            sortOrder = 100L,
        )
        val back = BookmarkMapper.toDomain(BookmarkMapper.toEntity(domain))
        assertEquals(domain, back)
    }
}
