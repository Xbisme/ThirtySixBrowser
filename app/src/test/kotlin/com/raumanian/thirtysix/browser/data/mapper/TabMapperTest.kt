package com.raumanian.thirtysix.browser.data.mapper

import com.raumanian.thirtysix.browser.data.local.entity.TabEntity
import com.raumanian.thirtysix.browser.domain.model.Tab
import org.junit.Assert.assertEquals
import org.junit.Test

class TabMapperTest {

    @Test
    fun `entity toDomain preserves all fields`() {
        val entity = TabEntity(
            id = 7L,
            url = "https://example.org/",
            title = "Example",
            position = 3,
            createdAt = 1_700_000_000_000L,
            lastActiveAt = 1_700_000_500_000L,
        )

        val tab = entity.toDomain()

        assertEquals(7L, tab.id)
        assertEquals("https://example.org/", tab.url)
        assertEquals("Example", tab.title)
        assertEquals(3, tab.position)
        assertEquals(1_700_000_000_000L, tab.createdAt)
        assertEquals(1_700_000_500_000L, tab.lastActiveAt)
    }

    @Test
    fun `domain toEntity preserves all fields`() {
        val tab = Tab(
            id = 9L,
            url = "https://duckduckgo.com/",
            title = "DDG",
            position = 0,
            createdAt = 1_700_001_000_000L,
            lastActiveAt = 1_700_001_500_000L,
        )

        val entity = tab.toEntity()

        assertEquals(9L, entity.id)
        assertEquals("https://duckduckgo.com/", entity.url)
        assertEquals("DDG", entity.title)
        assertEquals(0, entity.position)
        assertEquals(1_700_001_000_000L, entity.createdAt)
        assertEquals(1_700_001_500_000L, entity.lastActiveAt)
    }

    @Test
    fun `entity to domain back to entity round-trip is identity`() {
        val original = TabEntity(
            id = 42L,
            url = "https://www.google.com/",
            title = "Google",
            position = 1,
            createdAt = 1_700_002_000_000L,
            lastActiveAt = 1_700_002_500_000L,
        )

        assertEquals(original, original.toDomain().toEntity())
    }

    @Test
    fun `domain to entity back to domain round-trip is identity`() {
        val original = Tab(
            id = 11L,
            url = "https://www.bing.com/",
            title = "Bing",
            position = 2,
            createdAt = 1_700_003_000_000L,
            lastActiveAt = 1_700_003_500_000L,
        )

        assertEquals(original, original.toEntity().toDomain())
    }
}
