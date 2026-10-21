package com.raumanian.thirtysix.browser.feature.settings.domain.usecase

import com.raumanian.thirtysix.browser.core.domain.model.SiteCapability
import com.raumanian.thirtysix.browser.core.domain.model.SiteDataClearOutcome
import com.raumanian.thirtysix.browser.core.domain.model.SiteDecision
import com.raumanian.thirtysix.browser.core.domain.model.TabSession
import com.raumanian.thirtysix.browser.core.domain.usecase.ClearBrowsingHistoryUseCase
import com.raumanian.thirtysix.browser.core.domain.util.OneTimeGrantStore
import com.raumanian.thirtysix.browser.core.testing.fake.FakeCookieJarSnapshotManager
import com.raumanian.thirtysix.browser.core.testing.fake.FakeFaviconCache
import com.raumanian.thirtysix.browser.core.testing.fake.FakeFireproofSiteRepository
import com.raumanian.thirtysix.browser.core.testing.fake.FakeHistoryRepository
import com.raumanian.thirtysix.browser.core.testing.fake.FakeProtectionExceptionRepository
import com.raumanian.thirtysix.browser.core.testing.fake.FakeScreenshotCache
import com.raumanian.thirtysix.browser.core.testing.fake.FakeSitePermissionRepository
import com.raumanian.thirtysix.browser.core.testing.fake.FakeTabSessionRepository
import com.raumanian.thirtysix.browser.core.testing.fake.FakeWebDataCleaner
import com.raumanian.thirtysix.browser.feature.privacy.data.datasource.local.HttpsUpgradeFailureStore
import com.raumanian.thirtysix.browser.feature.privacy.data.datasource.local.IncognitoProtectionExceptionStore
import com.raumanian.thirtysix.browser.feature.settings.domain.model.ClearBrowsingDataCategory
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every fake writes into one shared call log, so ordering across seams is observable. The first case
 * is a privacy guarantee: the incognito set-aside is discarded before the cookie jar is wiped.
 */
class ClearBrowsingDataUseCaseTest {

    private val log = mutableListOf<String>()
    private val history = FakeHistoryRepository(log)
    private val web = FakeWebDataCleaner(log)
    private val cookies = FakeCookieJarSnapshotManager(log)
    private val favicons = FakeFaviconCache(log)
    private val screenshots = FakeScreenshotCache(log)
    private val sitePermissions = FakeSitePermissionRepository()
    private val grants = OneTimeGrantStore()
    private val protectionExceptions = FakeProtectionExceptionRepository()
    private val incognitoProtection = IncognitoProtectionExceptionStore()
    private val fireproof = FakeFireproofSiteRepository()
    private val tabSessions = FakeTabSessionRepository()
    private val httpsFailures = HttpsUpgradeFailureStore()
    private val useCase = ClearBrowsingDataUseCase(
        ClearBrowsingHistoryUseCase(history, tabSessions),
        web,
        cookies,
        favicons,
        screenshots,
        sitePermissions,
        grants,
        protectionExceptions,
        incognitoProtection,
        fireproof,
        httpsFailures,
    )

    @Test
    fun `clearing history also forgets the hosts that failed the https upgrade`() = runTest {
        httpsFailures.markFailed("plain.example")

        useCase(setOf(ClearBrowsingDataCategory.CookiesAndSiteData))
        assertEquals(setOf("plain.example"), httpsFailures.snapshot())

        useCase(setOf(ClearBrowsingDataCategory.History))
        assertTrue(httpsFailures.snapshot().isEmpty())
    }

    @Test
    fun `sites without protection are cleared only when their category is selected`() = runTest {
        protectionExceptions.add("shop.example")
        incognitoProtection.add("private.example")

        useCase(ClearBrowsingDataCategory.DEFAULT_SELECTION)
        assertEquals(setOf("shop.example"), protectionExceptions.currentSites())
        assertTrue(incognitoProtection.contains("private.example"))

        log.clear()
        val result = useCase(setOf(ClearBrowsingDataCategory.SitesWithoutProtection))
        assertTrue(result.failed.isEmpty())
        assertTrue(protectionExceptions.currentSites().isEmpty())
        assertTrue(!incognitoProtection.contains("private.example"))
        assertEquals("no other category ran", emptyList<String>(), log)
    }

    @Test
    fun `fireproof sites are cleared only when their category is selected and never shield cookies`() = runTest {
        fireproof.add("example.com")

        val defaults = useCase(ClearBrowsingDataCategory.DEFAULT_SELECTION)
        assertTrue(defaults.failed.isEmpty())
        assertEquals(
            "INV-10 — cookies cleared without consulting the list",
            setOf("example.com"),
            fireproof.currentSites(),
        )
        assertTrue(FakeWebDataCleaner.CALL_CLEAR_SITE_DATA in log)
        assertFalse(FakeWebDataCleaner.CALL_CLEAR_EXCEPT in log)
        assertFalse(ClearBrowsingDataCategory.FireproofSites in ClearBrowsingDataCategory.DEFAULT_SELECTION)

        log.clear()
        val result = useCase(setOf(ClearBrowsingDataCategory.FireproofSites))
        assertTrue(result.failed.isEmpty())
        assertTrue(fireproof.currentSites().isEmpty())
        assertEquals("no other category ran", emptyList<String>(), log)
    }

    @Test
    fun `site permissions are cleared only when their category is selected`() = runTest {
        sitePermissions.save(ORIGIN, SiteCapability.Camera, SiteDecision.Allow)
        grants.grant(1L, 1L, ORIGIN, setOf(SiteCapability.Microphone))

        useCase(ClearBrowsingDataCategory.DEFAULT_SELECTION)
        assertEquals(1, sitePermissions.rows.value.size)
        assertTrue(grants.has(1L, 1L, ORIGIN, SiteCapability.Microphone))

        val result = useCase(setOf(ClearBrowsingDataCategory.SitePermissions))
        assertTrue(result.failed.isEmpty())
        assertTrue(sitePermissions.rows.value.isEmpty())
        assertFalse(grants.has(1L, 1L, ORIGIN, SiteCapability.Microphone))
    }

    @Test
    fun `the default selection leaves site permissions out`() {
        assertFalse(ClearBrowsingDataCategory.SitePermissions in ClearBrowsingDataCategory.DEFAULT_SELECTION)
    }

    @Test
    fun `the incognito set-aside is discarded before the cookie jar is wiped`() = runTest {
        useCase(setOf(ClearBrowsingDataCategory.CookiesAndSiteData))

        assertEquals(
            "discard MUST precede the wipe",
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
    fun `clearing history also clears every tab's saved back and forward`() = runTest {
        tabSessions.save(TabSession(1L, byteArrayOf(1), 1L))
        tabSessions.save(TabSession(-1L, byteArrayOf(2), 1L))

        useCase(setOf(ClearBrowsingDataCategory.History))

        assertTrue(tabSessions.sessions.isEmpty())
        assertEquals(1L, tabSessions.clears.value)
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

    private companion object {
        const val ORIGIN = "https://meet.example"
    }
}
