package com.raumanian.thirtysix.browser.presentation.browser

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.raumanian.thirtysix.browser.core.constants.UrlConstants
import com.raumanian.thirtysix.browser.data.local.cache.FaviconCache
import com.raumanian.thirtysix.browser.data.local.cache.ScreenshotCache
import com.raumanian.thirtysix.browser.domain.model.Tab
import com.raumanian.thirtysix.browser.domain.repository.SearchEngineRepository
import com.raumanian.thirtysix.browser.domain.usecase.BuildSearchUrlUseCase
import com.raumanian.thirtysix.browser.domain.usecase.CreateTabUseCase
import com.raumanian.thirtysix.browser.domain.usecase.ObserveActiveTabIsIncognitoUseCase
import com.raumanian.thirtysix.browser.domain.usecase.ObserveActiveTabUseCase
import com.raumanian.thirtysix.browser.domain.usecase.ObserveAllTabsUseCase
import com.raumanian.thirtysix.browser.domain.usecase.ObserveTabsUseCase
import com.raumanian.thirtysix.browser.domain.usecase.UpdateActiveTabUrlAndTitleUseCase
import com.raumanian.thirtysix.browser.domain.usecase.UpdateHistoryEntryTitleUseCase
import com.raumanian.thirtysix.browser.testdoubles.FakeIncognitoTabRepository
import com.raumanian.thirtysix.browser.testdoubles.FakeTabRepository
import java.io.File
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Spec 012 — T010 cache-write-gate tests for [BrowserViewModel].
 *
 * Hosted in a separate file (not folded into [BrowserViewModelTest]) because
 * the gate assertions need real `android.graphics.Bitmap` instances to pass
 * through `onIconReceived` / `onScreenshotReady`, which in turn requires
 * Robolectric (`AndroidJUnit4` runner + SDK 33 pin from Spec 005). Keeping
 * [BrowserViewModelTest] pure-JVM preserves its fast execution profile —
 * Robolectric overhead is isolated to the 3 tests below.
 *
 * Verifies FR-009 (no screenshot cache writes for incognito) and FR-010 (no
 * favicon cache writes for incognito) plus a control test asserting the gate
 * does NOT regress the normal-tab cache write path.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class BrowserViewModelIncognitoCacheGateTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * Build a VM where the active tab is forced to be incognito by seeding the
     * [FakeIncognitoTabRepository] with a tab whose `lastActiveAt` exceeds the
     * timestamps of the auto-seeded normal home tab in [FakeTabRepository].
     * The merged-flow ordering rule (lastActiveAt DESC) then puts the incognito
     * tab at the head, so [BrowserViewModel.uiState.isIncognito] resolves to
     * `true` after `advanceUntilIdle()`.
     */
    private fun newIncognitoActiveViewModel(
        faviconCache: FaviconCache,
        screenshotCache: ScreenshotCache,
    ): BrowserViewModel {
        val tabRepository = FakeTabRepository(homeUrl = "https://normal.example.com")
        val incognitoRepo = FakeIncognitoTabRepository()
        incognitoRepo.emit(
            listOf(
                Tab(
                    id = -1L,
                    url = "https://incognito.example.com",
                    title = "",
                    position = 0,
                    createdAt = Long.MAX_VALUE,
                    lastActiveAt = Long.MAX_VALUE,
                    isIncognito = true,
                ),
            ),
        )
        return buildVm(tabRepository, incognitoRepo, faviconCache, screenshotCache)
    }

    private fun newNormalActiveViewModel(
        faviconCache: FaviconCache,
        screenshotCache: ScreenshotCache,
    ): BrowserViewModel {
        val tabRepository = FakeTabRepository(homeUrl = "https://normal.example.com")
        val incognitoRepo = FakeIncognitoTabRepository()
        return buildVm(tabRepository, incognitoRepo, faviconCache, screenshotCache)
    }

    private fun buildVm(
        tabRepository: FakeTabRepository,
        incognitoRepo: FakeIncognitoTabRepository,
        faviconCache: FaviconCache,
        screenshotCache: ScreenshotCache,
    ): BrowserViewModel {
        val observeAllTabs = ObserveAllTabsUseCase(tabRepository, incognitoRepo)
        // Spec 013 — star use cases. Self-contained fake repo; no impact on incognito tests.
        val bookmarkRepo = com.raumanian.thirtysix.browser.testdoubles.FakeBookmarkRepository()
        val addBookmark = com.raumanian.thirtysix.browser.domain.usecase.AddBookmarkUseCase(bookmarkRepo)
        return BrowserViewModel(
            defaultHomeUrl = "https://normal.example.com",
            buildSearchUrl = BuildSearchUrlUseCase(GoogleByteIdenticalRepository),
            observeActiveTab = ObserveActiveTabUseCase(observeAllTabs),
            observeTabs = ObserveTabsUseCase(tabRepository),
            observeActiveTabIsIncognito = ObserveActiveTabIsIncognitoUseCase(observeAllTabs),
            updateActiveTabUrlAndTitle = UpdateActiveTabUrlAndTitleUseCase(tabRepository, incognitoRepo),
            createTab = CreateTabUseCase(tabRepository, "https://normal.example.com"),
            faviconCache = faviconCache,
            screenshotCache = screenshotCache,
            isUrlBookmarked = com.raumanian.thirtysix.browser.domain.usecase
                .IsUrlBookmarkedUseCase(bookmarkRepo),
            toggleBookmark = com.raumanian.thirtysix.browser.domain.usecase
                .ToggleBookmarkUseCase(bookmarkRepo, addBookmark),
            updateHistoryEntryTitle = com.raumanian.thirtysix.browser.domain.usecase
                .UpdateHistoryEntryTitleUseCase(
                    com.raumanian.thirtysix.browser.testdoubles.FakeHistoryRepository(),
                ),
            recordHistoryEntry = com.raumanian.thirtysix.browser.domain.usecase
                .RecordHistoryEntryUseCase(
                    com.raumanian.thirtysix.browser.testdoubles.FakeHistoryRepository(),
                ),
            startDownload = noOpStartDownloadUseCase(),
        )
    }

    @Test
    fun `onIconReceived when incognito does not call FaviconCache save`() = runTest(testDispatcher) {
        val recordingFavicon = RecordingFaviconCache()
        val vm = newIncognitoActiveViewModel(
            faviconCache = recordingFavicon,
            screenshotCache = NoopScreenshotCache,
        )
        advanceUntilIdle()
        assertEquals("incognito flag should propagate to UiState", true, vm.uiState.value.isIncognito)

        val bmp = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        vm.onIconReceived("https://incognito.example.com", bmp)
        advanceUntilIdle()

        assertEquals(
            "FaviconCache.save MUST NOT be called for incognito tabs (FR-010)",
            0,
            recordingFavicon.saveCallCount,
        )
    }

    @Test
    fun `onScreenshotReady when incognito does not call ScreenshotCache save`() = runTest(testDispatcher) {
        val recordingScreenshot = RecordingScreenshotCache()
        val vm = newIncognitoActiveViewModel(
            faviconCache = NoopFaviconCache,
            screenshotCache = recordingScreenshot,
        )
        advanceUntilIdle()
        assertEquals(true, vm.uiState.value.isIncognito)

        val bmp = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        vm.onScreenshotReady(bmp)
        advanceUntilIdle()

        assertEquals(
            "ScreenshotCache.save MUST NOT be called for incognito tabs (FR-009)",
            0,
            recordingScreenshot.saveCallCount,
        )
    }

    @Test
    fun `onIconReceived when normal tab still writes to FaviconCache (control)`() = runTest(testDispatcher) {
        val recordingFavicon = RecordingFaviconCache()
        val vm = newNormalActiveViewModel(
            faviconCache = recordingFavicon,
            screenshotCache = NoopScreenshotCache,
        )
        advanceUntilIdle()
        assertEquals(false, vm.uiState.value.isIncognito)

        val bmp = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        vm.onIconReceived("https://example.com", bmp)
        advanceUntilIdle()

        assertEquals(
            "non-regression: normal-tab favicon write MUST proceed",
            1,
            recordingFavicon.saveCallCount,
        )
    }

    private object NoopFaviconCache : FaviconCache {
        private val state = MutableStateFlow(0L)
        override val version: StateFlow<Long> = state.asStateFlow()
        override suspend fun save(url: String, bitmap: Bitmap) = Unit
        override suspend fun clearAll() = Unit
        override fun fileFor(url: String): File? = null
    }

    private object NoopScreenshotCache : ScreenshotCache {
        private val state = MutableStateFlow(0L)
        override val version: StateFlow<Long> = state.asStateFlow()
        override suspend fun save(tabId: Long, bitmap: Bitmap) = Unit
        override fun fileFor(tabId: Long): File? = null
        override suspend fun delete(tabId: Long) = Unit
        override suspend fun clearAll() = Unit
    }

    private class RecordingFaviconCache : FaviconCache {
        private val state = MutableStateFlow(0L)
        override val version: StateFlow<Long> = state.asStateFlow()
        var saveCallCount: Int = 0
            private set
        override suspend fun save(url: String, bitmap: Bitmap) {
            saveCallCount += 1
        }
        override suspend fun clearAll() = Unit
        override fun fileFor(url: String): File? = null
    }

    private class RecordingScreenshotCache : ScreenshotCache {
        private val state = MutableStateFlow(0L)
        override val version: StateFlow<Long> = state.asStateFlow()
        var saveCallCount: Int = 0
            private set
        override suspend fun save(tabId: Long, bitmap: Bitmap) {
            saveCallCount += 1
        }
        override fun fileFor(tabId: Long): File? = null
        override suspend fun delete(tabId: Long) = Unit
        override suspend fun clearAll() = Unit
    }

    private object GoogleByteIdenticalRepository : SearchEngineRepository {
        override suspend fun buildSearchUrl(query: String): String {
            val encoded = URLEncoder.encode(query, Charsets.UTF_8.name())
            return String.format(UrlConstants.GOOGLE_SEARCH_URL_TEMPLATE, encoded)
        }
    }
}
