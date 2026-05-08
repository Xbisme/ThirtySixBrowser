package com.raumanian.thirtysix.browser.data.mapper

import com.raumanian.thirtysix.browser.data.local.entity.HistoryEntryEntity
import com.raumanian.thirtysix.browser.domain.model.HistoryEntry
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryEntryMapperTest {

    @Test
    fun `toDomain copies all fields verbatim`() {
        val entity = HistoryEntryEntity(
            id = 42L,
            url = "https://example.com/page",
            title = "Example",
            visitedAt = 1_700_000_000_000L,
        )
        val domain = HistoryEntryMapper.toDomain(entity)
        assertEquals(42L, domain.id)
        assertEquals("https://example.com/page", domain.url)
        assertEquals("Example", domain.title)
        assertEquals(1_700_000_000_000L, domain.visitedAt)
    }

    @Test
    fun `toEntity copies all fields verbatim`() {
        val domain = HistoryEntry(
            id = 7L,
            url = "https://example.com/q?x=1",
            title = "Q",
            visitedAt = 1L,
        )
        val entity = HistoryEntryMapper.toEntity(domain)
        assertEquals(7L, entity.id)
        assertEquals("https://example.com/q?x=1", entity.url)
        assertEquals("Q", entity.title)
        assertEquals(1L, entity.visitedAt)
    }

    @Test
    fun `round-trip preserves entity for non-zero id`() {
        val entity = HistoryEntryEntity(99L, "https://round.example", "R", 1234L)
        assertEquals(entity, HistoryEntryMapper.toEntity(HistoryEntryMapper.toDomain(entity)))
    }

    @Test
    fun `empty title is preserved`() {
        val domain = HistoryEntry(0L, "https://no-title.example", "", 1L)
        assertEquals("", HistoryEntryMapper.toEntity(domain).title)
    }

    @Test
    fun `un-persisted id zero is preserved`() {
        val domain = HistoryEntry(0L, "https://new.example", "N", 1L)
        assertEquals(0L, HistoryEntryMapper.toEntity(domain).id)
    }
}
