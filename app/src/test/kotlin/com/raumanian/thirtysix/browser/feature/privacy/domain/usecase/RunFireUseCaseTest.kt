package com.raumanian.thirtysix.browser.feature.privacy.domain.usecase

import com.raumanian.thirtysix.browser.core.domain.model.Bookmark
import com.raumanian.thirtysix.browser.core.domain.model.FireStep
import com.raumanian.thirtysix.browser.core.domain.model.SiteCapability
import com.raumanian.thirtysix.browser.core.domain.model.SiteDataClearOutcome
import com.raumanian.thirtysix.browser.core.domain.model.SiteDataPath
import com.raumanian.thirtysix.browser.core.domain.model.SiteDecision
import com.raumanian.thirtysix.browser.core.domain.model.Tab
import com.raumanian.thirtysix.browser.core.domain.repository.HttpsUpgradeFailures
import com.raumanian.thirtysix.browser.core.domain.repository.IncognitoTabRepository
import com.raumanian.thirtysix.browser.core.domain.repository.PrivacyScriptSession
import com.raumanian.thirtysix.browser.core.domain.repository.TabRepository
import com.raumanian.thirtysix.browser.core.domain.util.DispatcherProvider
import com.raumanian.thirtysix.browser.core.domain.util.OneTimeGrantStore
import com.raumanian.thirtysix.browser.core.testing.fake.FakeBookmarkRepository
import com.raumanian.thirtysix.browser.core.testing.fake.FakeCookieJarSnapshotManager
import com.raumanian.thirtysix.browser.core.testing.fake.FakeFaviconCache
import com.raumanian.thirtysix.browser.core.testing.fake.FakeFireproofSiteRepository
import com.raumanian.thirtysix.browser.core.testing.fake.FakeHistoryRepository
import com.raumanian.thirtysix.browser.core.testing.fake.FakeIncognitoTabRepository
import com.raumanian.thirtysix.browser.core.testing.fake.FakeScreenshotCache
import com.raumanian.thirtysix.browser.core.testing.fake.FakeSitePermissionRepository
import com.raumanian.thirtysix.browser.core.testing.fake.FakeTabRepository
import com.raumanian.thirtysix.browser.core.testing.fake.FakeWebDataCleaner
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Spec 024 T022 / T037 — [RunFireUseCase]: step order (INV-2, INV-3), failure boundaries, the two
 * site-data paths, what Fire keeps (INV-4, FR-004a, FR-008) and the session traces (FR-003a).
 *
 * Every fake shares one call log, so ordering across seams is observable.
 */
class RunFireUseCaseTest {

    private val log = mutableListOf<String>()
    private val fireproof = FakeFireproofSiteRepository()
    private val history = FakeHistoryRepository(log)
    private val tabsFake = FakeTabRepository(
        homeUrl = HOME,
        initialTabs = listOf(tab(1L, "https://news.test/a"), tab(2L, "https://mail.example.com/inbox")),
    )
    private val tabs: TabRepository = object : TabRepository by tabsFake {
        override suspend fun closeAllTabs() {
            log += CALL_CLOSE_TABS
            tabsFake.closeAllTabs()
        }
    }
    private val incognitoFake = FakeIncognitoTabRepository().apply {
        emit(listOf(tab(-1L, "https://secret.test/", incognito = true)))
    }
    private val incognito: IncognitoTabRepository = object : IncognitoTabRepository by incognitoFake {
        override suspend fun closeAll() {
            log += CALL_CLOSE_INCOGNITO
            incognitoFake.closeAll()
        }
    }
    private val permissions = FakeSitePermissionRepository()
    private val grants = OneTimeGrantStore()
    private val web = FakeWebDataCleaner(log).apply { origins = setOf("storage.test") }
    private val cookies = FakeCookieJarSnapshotManager(log)
    private val favicons = FakeFaviconCache(log)
    private val screenshots = FakeScreenshotCache(log)
    private val bookmarks = FakeBookmarkRepository()
    private val failures = FakeFailures()
    private val session = FakeSession()
    private val observer = object : FireStepObserver {
        var failAt: FireStep? = null
        var cancelAt: FireStep? = null
        override suspend fun beforeStep(step: FireStep) {
            log += "step.$step"
            if (step == cancelAt) throw CancellationException("stop")
            if (step == failAt) throw IOException("boom")
        }
    }

    private val useCase = RunFireUseCase(
        fireproof, history, tabs, incognito, permissions, grants, web, cookies,
        favicons, screenshots, bookmarks, failures, session, observer, TestDispatchers,
    )

    @Test
    fun `without fireproof sites everything is cleared through the complete path`() = runTest {
        history.recordVisit("https://news.test/a", "", 1L)

        val result = useCase()

        assertTrue(result.failedSteps.isEmpty())
        assertEquals(SiteDataPath.Complete, result.siteDataPath)
        assertTrue(history.recorded.isEmpty())
        assertTrue(incognitoFake.snapshot().isEmpty())
        val remaining = tabsFake.observeTabs().first()
        assertEquals("exactly one home tab", listOf(HOME), remaining.map { it.url })
        assertTrue(FakeWebDataCleaner.CALL_CLEAR_SITE_DATA in log)
        assertFalse("cache already emptied by the complete path", FakeWebDataCleaner.CALL_CLEAR_WEB_CACHE in log)
        assertTrue(FakeScreenshotCache.CALL_CLEAR_ALL in log)
    }

    @Test
    fun `hosts are collected first and incognito ends before any cookie step`() = runTest {
        useCase()

        val collect = log.indexOf("step.${FireStep.CollectHosts}")
        val origins = log.indexOf(FakeWebDataCleaner.CALL_STORAGE_ORIGINS)
        val closeIncognito = log.indexOf(CALL_CLOSE_INCOGNITO)
        val closeTabs = log.indexOf(CALL_CLOSE_TABS)
        val historyCleared = log.indexOf(FakeHistoryRepository.CALL_CLEAR_ALL)
        val discard = log.indexOf(FakeCookieJarSnapshotManager.CALL_DISCARD)
        val siteData = log.indexOf(FakeWebDataCleaner.CALL_CLEAR_SITE_DATA)
        assertTrue("INV-3", collect < origins && origins < closeIncognito)
        assertTrue("INV-2", closeIncognito < discard && discard < siteData)
        assertTrue("tabs before history", closeTabs in (closeIncognito + 1) until historyCleared)
        assertTrue("history before cookies", historyCleared < siteData)
    }

    @Test
    fun `with fireproof sites the fireproof-aware path receives the kept sites and every candidate host`() = runTest {
        fireproof.add("example.com")
        history.recordVisit("https://history.test/", "", 1L)
        permissions.save("https://camera.test", SiteCapability.Camera, SiteDecision.Allow)

        val result = useCase()

        assertEquals(SiteDataPath.PerSite, result.siteDataPath)
        val (keep, candidates) = web.lastClearExcept!!
        assertEquals(setOf("example.com"), keep)
        assertEquals(
            setOf("history.test", "news.test", "mail.example.com", "secret.test", "camera.test", "storage.test"),
            candidates,
        )
        assertFalse(FakeWebDataCleaner.CALL_CLEAR_SITE_DATA in log)
        assertTrue("the web cache is cleared on the per-site path", FakeWebDataCleaner.CALL_CLEAR_WEB_CACHE in log)
    }

    @Test
    fun `a partial clear on an older engine is the fallback path and the cache is still cleared`() = runTest {
        web.siteDataOutcome = SiteDataClearOutcome.Partial

        val result = useCase()

        assertEquals(SiteDataPath.Fallback, result.siteDataPath)
        assertTrue(FakeWebDataCleaner.CALL_CLEAR_WEB_CACHE in log)
    }

    @Test
    fun `a failing step is reported and every later step still runs`() = runTest {
        observer.failAt = FireStep.History
        history.recordVisit("https://news.test/a", "", 1L)

        val result = useCase()

        assertEquals(setOf(FireStep.History), result.failedSteps)
        assertTrue(FakeWebDataCleaner.CALL_CLEAR_SITE_DATA in log)
        assertTrue(FakeFaviconCache.CALL_RETAIN_ONLY in log)
        assertTrue(failures.cleared)
    }

    @Test
    fun `a failed cookie wipe on the fireproof path is reported`() = runTest {
        fireproof.add("example.com")
        web.clearExceptPath = null

        val result = useCase()

        assertTrue(FireStep.CookiesAndSiteData in result.failedSteps)
        assertEquals(null, result.siteDataPath)
    }

    @Test(expected = CancellationException::class)
    fun `cancellation is rethrown, never counted as a failed step`() = runTest {
        observer.cancelAt = FireStep.Favicons
        useCase()
    }

    @Test
    fun `permissions and one-time grants of non-fireproof sites are removed and fireproof ones kept`() = runTest {
        fireproof.add("example.com")
        permissions.save("https://mail.example.com", SiteCapability.Camera, SiteDecision.Allow)
        permissions.save("https://other.test", SiteCapability.Location, SiteDecision.Allow)
        grants.grant(1L, 1L, "https://mail.example.com", setOf(SiteCapability.Microphone))
        grants.grant(2L, 1L, "https://other.test", setOf(SiteCapability.Microphone))

        useCase()

        assertEquals(listOf("https://mail.example.com"), permissions.rows.value.map { it.origin })
        assertTrue(grants.has(1L, 1L, "https://mail.example.com", SiteCapability.Microphone))
        assertFalse(grants.has(2L, 1L, "https://other.test", SiteCapability.Microphone))
    }

    @Test
    fun `icons are kept only for bookmarked hosts and fireproof sites`() = runTest {
        fireproof.add("example.com")
        bookmarks.seedBookmark(Bookmark(0L, "B", "https://bookmarked.test/x", null, 1L, 0L))

        useCase()

        assertEquals(
            setOf("bookmarked.test", "mail.example.com", "example.com", "www.example.com"),
            favicons.retainedHosts,
        )
    }

    @Test
    fun `session traces are reset`() = runTest {
        val before = session.secret.value

        useCase()

        assertTrue(failures.cleared)
        assertNotEquals(before, session.secret.value)
    }

    @Test
    fun `bookmarks, the fireproof list and other kept data are never written`() = runTest {
        fireproof.add("example.com")
        val writesBefore = fireproof.writes
        bookmarks.seedBookmark(Bookmark(0L, "B", "https://bookmarked.test/x", null, 1L, 0L))

        useCase()

        assertEquals(writesBefore, fireproof.writes)
        assertEquals(1, bookmarks.getAllBookmarks().size)
    }

    @Test
    fun `running twice in a row is harmless`() = runTest {
        useCase()
        val second = useCase()

        assertTrue(second.failedSteps.isEmpty())
        assertEquals(listOf(HOME), tabsFake.observeTabs().first().map { it.url })
    }

    private class FakeFailures : HttpsUpgradeFailures {
        var cleared = false
        override fun markFailed(host: String) = Unit
        override fun snapshot(): Set<String> = emptySet()
        override fun clear() {
            cleared = true
        }
    }

    private class FakeSession : PrivacyScriptSession {
        private var counter = 0
        private val state = MutableStateFlow("00")
        override val secret: StateFlow<String> = state
        override fun regenerate() {
            state.value = (++counter).toString(16).padStart(2, '0')
        }
    }

    private object TestDispatchers : DispatcherProvider {
        override val main = Dispatchers.Unconfined
        override val io = Dispatchers.Unconfined
        override val default = Dispatchers.Unconfined
        override val unconfined = Dispatchers.Unconfined
    }

    private companion object {
        const val HOME = "thirtysix:home"
        const val CALL_CLOSE_TABS = "tabs.closeAllTabs"
        const val CALL_CLOSE_INCOGNITO = "incognito.closeAll"

        fun tab(id: Long, url: String, incognito: Boolean = false) =
            Tab(
                id = id,
                url = url,
                title = "",
                position = 0,
                createdAt = 1L,
                lastActiveAt = id,
                isIncognito = incognito,
            )
    }
}
