package com.raumanian.thirtysix.browser.domain.usecase

import com.raumanian.thirtysix.browser.domain.model.ClearBrowsingDataCategory
import com.raumanian.thirtysix.browser.domain.model.SiteDataClearOutcome
import com.raumanian.thirtysix.browser.testdoubles.FakeCookieJarSnapshotManager
import com.raumanian.thirtysix.browser.testdoubles.FakeFaviconCache
import com.raumanian.thirtysix.browser.testdoubles.FakeHistoryRepository
import com.raumanian.thirtysix.browser.testdoubles.FakeScreenshotCache
import com.raumanian.thirtysix.browser.testdoubles.FakeWebDataCleaner
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Spec 016 T068 — ⚠️ non-negotiable privacy test for [ClearBrowsingDataUseCase].
 *
 * Every fake writes into one shared call log, so ordering across seams is observable. The first
 * case is the one that must never regress: the incognito set-aside is discarded **before** the
 * cookie jar is wiped, otherwise closing the last incognito tab could write the cleared cookies
 * back (FR-030, research R7).
 */
class ClearBrowsingDataUseCaseTest {

    private val log = mutableListOf<String>()
    private val history = FakeHistoryRepository(log)
    private val web = FakeWebDataCleaner(log)
    private val cookies = FakeCookieJarSnapshotManager(log)
    private val favicons = FakeFaviconCache(log)
    private val screenshots = FakeScreenshotCache(log)
    private val useCase = ClearBrowsingDataUseCase(history, web, cookies, favicons, screenshots)

    @Test
    fun `the incognito set-aside is discarded before the cookie jar is wiped`() = runTest {
        useCase(setOf(ClearBrowsingDataCategory.CookiesAndSiteData))

        assertEquals(
            "FR-030 / R7 — discard MUST precede the wipe",
            listOf(FakeCookieJarSnapshotManager.CALL_DISCARD, FakeWebDataCleaner.CALL_CLEAR_SITE_DATA),
            log,
        )
    }

    @Test
    fun `all three categories run in the fixed order`() = runTest {
        val result = useCase(ClearBrowsingDataCategory.entries.toSet())

        assertEquals(
            listOf(
                FakeHistoryRepository.CALL_CLEAR_ALL,
                FakeCookieJarSnapshotManager.CALL_DISCARD,
                FakeWebDataCleaner.CALL_CLEAR_SITE_DATA,
                FakeFaviconCache.CALL_CLEAR_ALL,
                FakeScreenshotCache.CALL_CLEAR_ALL,
            ),
            log,
        )
        assertEquals(ClearBrowsingDataCategory.entries.toSet(), result.requested)
        assertTrue(result.failed.isEmpty())
    }

    @Test
    fun `history alone touches no other seam`() = runTest {
        val result = useCase(setOf(ClearBrowsingDataCategory.History))

        assertEquals(listOf(FakeHistoryRepository.CALL_CLEAR_ALL), log)
        assertTrue(result.failed.isEmpty())
    }

    @Test
    fun `cache alone clears the web cache and both app caches`() = runTest {
        useCase(setOf(ClearBrowsingDataCategory.CachedImagesAndFiles))

        assertEquals(
            listOf(
                FakeWebDataCleaner.CALL_CLEAR_WEB_CACHE,
                FakeFaviconCache.CALL_CLEAR_ALL,
                FakeScreenshotCache.CALL_CLEAR_ALL,
            ),
            log,
        )
    }

    @Test
    fun `a Complete site-data clear skips the web cache step`() = runTest {
        web.siteDataOutcome = SiteDataClearOutcome.Complete

        useCase(setOf(ClearBrowsingDataCategory.CookiesAndSiteData, ClearBrowsingDataCategory.CachedImagesAndFiles))

        assertFalse("spec A16 — the engine already emptied it", FakeWebDataCleaner.CALL_CLEAR_WEB_CACHE in log)
        assertTrue(FakeFaviconCache.CALL_CLEAR_ALL in log)
        assertTrue(FakeScreenshotCache.CALL_CLEAR_ALL in log)
    }

    @Test
    fun `a Partial site-data clear still clears the web cache and is never reported as failed`() = runTest {
        web.siteDataOutcome = SiteDataClearOutcome.Partial

        val result = useCase(ClearBrowsingDataCategory.entries.toSet())

        assertTrue(FakeWebDataCleaner.CALL_CLEAR_WEB_CACHE in log)
        assertTrue("spec A17 — Partial counts as cleared", result.failed.isEmpty())
    }

    @Test
    fun `a Failed site-data outcome marks only cookies and later categories still run`() = runTest {
        web.siteDataOutcome = SiteDataClearOutcome.Failed

        val result = useCase(ClearBrowsingDataCategory.entries.toSet())

        assertEquals(setOf(ClearBrowsingDataCategory.CookiesAndSiteData), result.failed)
        assertTrue(FakeWebDataCleaner.CALL_CLEAR_WEB_CACHE in log)
        assertTrue(FakeFaviconCache.CALL_CLEAR_ALL in log)
        assertTrue(FakeScreenshotCache.CALL_CLEAR_ALL in log)
    }

    @Test
    fun `a thrown site-data step marks only cookies`() = runTest {
        web.siteDataError = IllegalStateException("engine gone")

        val result = useCase(ClearBrowsingDataCategory.entries.toSet())

        assertEquals(setOf(ClearBrowsingDataCategory.CookiesAndSiteData), result.failed)
        assertTrue(FakeScreenshotCache.CALL_CLEAR_ALL in log)
    }

    @Test
    fun `a failing history clear does not stop the other categories`() = runTest {
        history.clearAllError = IOException("disk")

        val result = useCase(ClearBrowsingDataCategory.entries.toSet())

        assertEquals(setOf(ClearBrowsingDataCategory.History), result.failed)
        assertTrue(FakeCookieJarSnapshotManager.CALL_DISCARD in log)
        assertTrue(FakeScreenshotCache.CALL_CLEAR_ALL in log)
    }

    @Test
    fun `a false web cache result fails the cache category but both app caches are still cleared`() = runTest {
        web.webCacheResult = false

        val result = useCase(setOf(ClearBrowsingDataCategory.CachedImagesAndFiles))

        assertEquals(setOf(ClearBrowsingDataCategory.CachedImagesAndFiles), result.failed)
        assertTrue(FakeFaviconCache.CALL_CLEAR_ALL in log)
        assertTrue(FakeScreenshotCache.CALL_CLEAR_ALL in log)
    }

    @Test
    fun `a thrown web cache step still clears both app caches`() = runTest {
        web.webCacheError = IllegalStateException("no webview")

        val result = useCase(setOf(ClearBrowsingDataCategory.CachedImagesAndFiles))

        assertEquals(setOf(ClearBrowsingDataCategory.CachedImagesAndFiles), result.failed)
        assertTrue(FakeFaviconCache.CALL_CLEAR_ALL in log)
        assertTrue(FakeScreenshotCache.CALL_CLEAR_ALL in log)
    }

    @Test
    fun `a failing icon cache still lets the preview cache run`() = runTest {
        favicons.clearAllError = IOException("disk")

        val result = useCase(setOf(ClearBrowsingDataCategory.CachedImagesAndFiles))

        assertEquals(setOf(ClearBrowsingDataCategory.CachedImagesAndFiles), result.failed)
        assertTrue(FakeScreenshotCache.CALL_CLEAR_ALL in log)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `an empty selection is a programming error`() = runTest {
        useCase(emptySet())
    }

    @Test
    fun `cancellation propagates instead of being reported as a failure`() = runTest {
        web.siteDataError = CancellationException("cancelled")

        val thrown = runCatching { useCase(setOf(ClearBrowsingDataCategory.CookiesAndSiteData)) }.exceptionOrNull()

        assertTrue("got $thrown", thrown is CancellationException)
    }
}
