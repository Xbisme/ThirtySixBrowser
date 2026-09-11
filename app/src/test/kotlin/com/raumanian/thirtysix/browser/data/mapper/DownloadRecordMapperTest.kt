package com.raumanian.thirtysix.browser.data.mapper

import com.raumanian.thirtysix.browser.data.local.entity.DownloadRecordEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Spec 015 — round-trip totality for [DownloadRecordMapper].
 */
class DownloadRecordMapperTest {

    private val entity = DownloadRecordEntity(
        id = 7L,
        sourceUrl = "https://example.com/report.pdf",
        fileName = "report.pdf",
        mimeType = "application/pdf",
        createdAt = 1_700_000_000_000L,
        transferHandle = 4242L,
        localUri = "content://downloads/all_downloads/4242",
    )

    @Test
    fun `entity to domain and back is the identity`() {
        assertEquals(entity, DownloadRecordMapper.toEntity(DownloadRecordMapper.toDomain(entity)))
    }

    @Test
    fun `null localUri survives the round trip as null`() {
        val inFlight = entity.copy(localUri = null)
        val domain = DownloadRecordMapper.toDomain(inFlight)
        assertNull("null localUri means still in flight and must not be coerced", domain.localUri)
        assertEquals(inFlight, DownloadRecordMapper.toEntity(domain))
    }

    @Test
    fun `empty mimeType stays an empty string and is never turned into null`() {
        val unknownType = entity.copy(mimeType = "")
        val domain = DownloadRecordMapper.toDomain(unknownType)
        assertEquals("", domain.mimeType)
        assertEquals(unknownType, DownloadRecordMapper.toEntity(domain))
    }

    @Test
    fun `an unpersisted record maps with a zero id`() {
        val fresh = entity.copy(id = 0L)
        assertEquals(0L, DownloadRecordMapper.toDomain(fresh).id)
    }

    @Test
    fun `every field is carried across - no silent drops`() {
        val domain = DownloadRecordMapper.toDomain(entity)
        assertEquals(entity.id, domain.id)
        assertEquals(entity.sourceUrl, domain.sourceUrl)
        assertEquals(entity.fileName, domain.fileName)
        assertEquals(entity.mimeType, domain.mimeType)
        assertEquals(entity.createdAt, domain.createdAt)
        assertEquals(entity.transferHandle, domain.transferHandle)
        assertEquals(entity.localUri, domain.localUri)
    }
}
