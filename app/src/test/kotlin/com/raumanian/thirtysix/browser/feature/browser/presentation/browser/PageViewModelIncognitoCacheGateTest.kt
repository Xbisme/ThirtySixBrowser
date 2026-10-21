package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.raumanian.thirtysix.browser.core.domain.model.Tab
import com.raumanian.thirtysix.browser.core.domain.repository.FaviconCache
import com.raumanian.thirtysix.browser.core.domain.repository.ScreenshotCache
import com.raumanian.thirtysix.browser.core.domain.usecase.ObserveActiveTabUseCase
import com.raumanian.thirtysix.browser.core.domain.usecase.ObserveAllTabsUseCase
import com.raumanian.thirtysix.browser.core.domain.usecase.UpdateActiveTabUrlAndTitleUseCase
import com.raumanian.thirtysix.browser.core.testing.MainDispatcherRule
import com.raumanian.thirtysix.browser.core.testing.fake.FakeHistoryRepository
import com.raumanian.thirtysix.browser.core.testing.fake.FakeIncognitoTabRepository
import com.raumanian.thirtysix.browser.core.testing.fake.FakeTabRepository
import com.raumanian.thirtysix.browser.feature.browser.data.repository.CurrentPageRepositoryImpl
import com.raumanian.thirtysix.browser.feature.browser.domain.usecase.RecordHistoryEntryUseCase
import com.raumanian.thirtysix.browser.feature.browser.domain.usecase.UpdateHistoryEntryTitleUseCase
import java.io.File
import java.io.OutputStream
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Incognito tabs write neither favicons nor screenshots to disk; normal tabs still do. Robolectric
 * is needed for real `Bitmap`s, so these live apart from the pure-JVM [PageViewModelTest].
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class PageViewModelIncognitoCacheGateTest {

    private val testDispatcher = StandardTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(testDispatcher)

    /** The incognito tab is used most recently, so it heads the merged tab list and is active. */
    private fun newIncognitoActiveViewModel(
        faviconCache: FaviconCache,
        screenshotCache: ScreenshotCache,
    ): PageViewModel {
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
    ): PageViewModel {
        val tabRepository = FakeTabRepository(homeUrl = "https://normal.example.com")
        val incognitoRepo = FakeIncognitoTabRepository()
        return buildVm(tabRepository, incognitoRepo, faviconCache, screenshotCache)
    }

    private fun buildVm(
        tabRepository: FakeTabRepository,
        incognitoRepo: FakeIncognitoTabRepository,
        faviconCache: FaviconCache,
        screenshotCache: ScreenshotCache,
    ): PageViewModel {
        val observeAllTabs = ObserveAllTabsUseCase(tabRepository, incognitoRepo)
        return PageViewModel(
            observeActiveTab = ObserveActiveTabUseCase(observeAllTabs),
            tabs = tabRepository,
            updateActiveTabUrlAndTitle = UpdateActiveTabUrlAndTitleUseCase(tabRepository, incognitoRepo),
            faviconCache = faviconCache,
            screenshotCache = screenshotCache,
            updateHistoryEntryTitle = UpdateHistoryEntryTitleUseCase(
                FakeHistoryRepository(),
            ),
            recordHistoryEntry = RecordHistoryEntryUseCase(
                FakeHistoryRepository(),
            ),
            pageStore = CurrentPageRepositoryImpl("https://normal.example.com"),
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
        assertEquals("incognito flag should propagate to UiState", true, vm.screenState().isIncognito)

        val bmp = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        vm.onAction(PageAction.IconReceived("https://incognito.example.com", bmp, isIncognito = true))
        advanceUntilIdle()

        assertEquals(
            "FaviconCache.save MUST NOT be called for incognito tabs",
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
        assertEquals(true, vm.screenState().isIncognito)

        val bmp = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        vm.onAction(PageAction.ScreenshotReady(bmp, tabId = -1L, isIncognito = true))
        advanceUntilIdle()

        assertEquals(
            "ScreenshotCache.save MUST NOT be called for incognito tabs",
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
        assertEquals(false, vm.screenState().isIncognito)

        val bmp = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        vm.onAction(PageAction.IconReceived("https://example.com", bmp, isIncognito = false))
        advanceUntilIdle()

        assertEquals(
            "non-regression: normal-tab favicon write MUST proceed",
            1,
            recordingFavicon.saveCallCount,
        )
    }

    @Test
    fun `a preview arriving after a tab switch is saved under the tab it came from`() = runTest(testDispatcher) {
        val recordingScreenshot = RecordingScreenshotCache()
        val vm = newNormalActiveViewModel(faviconCache = NoopFaviconCache, screenshotCache = recordingScreenshot)
        advanceUntilIdle()
        val active = vm.currentPage.value.activeTab?.id

        val bmp = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        vm.onAction(PageAction.ScreenshotReady(bmp, tabId = PREVIOUS_TAB_ID, isIncognito = false))
        advanceUntilIdle()

        assertEquals(listOf(PREVIOUS_TAB_ID), recordingScreenshot.savedIds)
        assertNotEquals(PREVIOUS_TAB_ID, active)
    }

    @Test
    fun `a preview or icon taken from an incognito tab is never saved, even after a switch to a normal tab`() =
        runTest(testDispatcher) {
            val recordingFavicon = RecordingFaviconCache()
            val recordingScreenshot = RecordingScreenshotCache()
            val vm = newNormalActiveViewModel(faviconCache = recordingFavicon, screenshotCache = recordingScreenshot)
            advanceUntilIdle()
            assertEquals(false, vm.screenState().isIncognito)

            val bmp = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
            vm.onAction(PageAction.ScreenshotReady(bmp, tabId = -1L, isIncognito = true))
            vm.onAction(PageAction.IconReceived("https://incognito.example.com", bmp, isIncognito = true))
            advanceUntilIdle()

            assertEquals(0, recordingScreenshot.saveCallCount)
            assertEquals(0, recordingFavicon.saveCallCount)
        }

    private object NoopFaviconCache : FaviconCache {
        private val state = MutableStateFlow(0L)
        override val version: StateFlow<Long> = state.asStateFlow()
        override suspend fun save(url: String, writeImage: (OutputStream) -> Unit) = Unit
        override suspend fun clearAll() = Unit
        override suspend fun retainOnly(hosts: Set<String>) = Unit
        override fun fileFor(url: String): File? = null
    }

    private object NoopScreenshotCache : ScreenshotCache {
        private val state = MutableStateFlow(0L)
        override val version: StateFlow<Long> = state.asStateFlow()
        override suspend fun save(tabId: Long, writeImage: (OutputStream) -> Unit) = Unit
        override fun fileFor(tabId: Long): File? = null
        override suspend fun delete(tabId: Long) = Unit
        override suspend fun clearAll() = Unit
    }

    private class RecordingFaviconCache : FaviconCache {
        private val state = MutableStateFlow(0L)
        override val version: StateFlow<Long> = state.asStateFlow()
        var saveCallCount: Int = 0
            private set
        override suspend fun save(url: String, writeImage: (OutputStream) -> Unit) {
            saveCallCount += 1
        }
        override suspend fun clearAll() = Unit
        override suspend fun retainOnly(hosts: Set<String>) = Unit
        override fun fileFor(url: String): File? = null
    }

    private class RecordingScreenshotCache : ScreenshotCache {
        private val state = MutableStateFlow(0L)
        override val version: StateFlow<Long> = state.asStateFlow()
        val savedIds = mutableListOf<Long>()
        val saveCallCount: Int get() = savedIds.size
        override suspend fun save(tabId: Long, writeImage: (OutputStream) -> Unit) {
            savedIds += tabId
        }
        override fun fileFor(tabId: Long): File? = null
        override suspend fun delete(tabId: Long) = Unit
        override suspend fun clearAll() = Unit
    }

    private companion object {
        const val PREVIOUS_TAB_ID = 42L
    }
}
