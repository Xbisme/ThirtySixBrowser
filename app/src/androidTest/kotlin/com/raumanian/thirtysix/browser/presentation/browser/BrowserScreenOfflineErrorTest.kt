package com.raumanian.thirtysix.browser.presentation.browser

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.raumanian.thirtysix.browser.HiltTestActivity
import com.raumanian.thirtysix.browser.core.constants.UrlConstants
import com.raumanian.thirtysix.browser.core.result.Result
import com.raumanian.thirtysix.browser.data.local.cache.FaviconCache
import com.raumanian.thirtysix.browser.data.local.cache.ScreenshotCache
import com.raumanian.thirtysix.browser.domain.model.Tab
import com.raumanian.thirtysix.browser.domain.repository.SearchEngineRepository
import com.raumanian.thirtysix.browser.domain.repository.TabRepository
import com.raumanian.thirtysix.browser.domain.usecase.BuildSearchUrlUseCase
import com.raumanian.thirtysix.browser.domain.usecase.CreateTabUseCase
import com.raumanian.thirtysix.browser.domain.usecase.ObserveActiveTabUseCase
import com.raumanian.thirtysix.browser.domain.usecase.ObserveTabsUseCase
import com.raumanian.thirtysix.browser.domain.usecase.UpdateActiveTabUrlAndTitleUseCase
import com.raumanian.thirtysix.browser.presentation.browser.components.TEST_TAG_BROWSER_ERROR_STATE
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Spec 007 US3 — error-state rendering smoke test.
 *
 * Mục đích: assert rằng khi `BrowserUiState.loadingState` chuyển sang
 * [LoadingState.Failed], [BrowserScreen] swap từ [BrowserWebView] sang
 * [components.BrowserErrorState] (ngừng render WebView).
 *
 * Lý do KHÔNG dùng real WebView load + invalid URL trigger:
 * - Earlier `@UninstallModules` + `FakeUrlConfigModule` swap (CI run 2026-05-01)
 *   timed out ngay cả với 15 s budget. Hai khả năng: (a) Hilt nested-module
 *   replace không thực sự swap binding ở androidTest, (b) Chromium WebView trên
 *   emulator API 29 không fire `onReceivedError(ERROR_HOST_LOOKUP)` đáng tin cậy
 *   cho `.invalid` TLD. Cả hai đều mất nhiều thời gian debug và tạo flake mù.
 * - Phần "WebViewClient → ErrorReason" mapping đã được cover deterministic ở
 *   `BrowserViewModelTest` (Failed transitions) + `ErrorReasonTest`
 *   (toUserMessageRes table) — JVM-only, không cần emulator network.
 * - SC-003 production scenario (airplane mode → localized error UI ≤ 5 s) vẫn
 *   được verify bằng Gate 7 step 4 (user manual on real device).
 *
 * Test này chỉ trả lời 1 câu hỏi đơn giản: rendering branch của BrowserScreen
 * khi `Failed` đúng không. Trigger qua `viewModel::onLoadFailed` trực tiếp →
 * không phụ thuộc network, deterministic, < 1 s ở real CI.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class BrowserScreenOfflineErrorTest {

    @get:Rule(order = 0)
    val hiltRule: HiltAndroidRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<HiltTestActivity>()

    private lateinit var viewModel: BrowserViewModel

    @Before
    fun setUp() {
        hiltRule.inject()
        composeRule.activity.runOnUiThread {
            // Construct VM and seed it to Failed BEFORE setContent. Reasoning:
            // BrowserScreen branches `when (loadingState)` and only constructs
            // BrowserWebView when state is NOT Failed. Seeding upfront means
            // the WebView is never created → no real network load → no race
            // between WebView's onPageFinished and the test's assertion (which
            // would re-transition Failed → Loaded and hide the error UI).
            // Spec 010 — error-rendering test never invokes the address-bar
            // Query branch, so a no-op `BuildSearchUrlUseCase` is sufficient.
            // Spec 011 — error-rendering test does not exercise tab logic;
            // a no-op TabRepository fake suffices for the 4 new use-case
            // dependencies.
            val noopTabRepo = OfflineErrorNoopTabRepository
            val observeTabs = ObserveTabsUseCase(noopTabRepo)
            viewModel = BrowserViewModel(
                defaultHomeUrl = UrlConstants.DEFAULT_HOME_URL,
                buildSearchUrl = BuildSearchUrlUseCase(OfflineErrorNoopSearchEngineRepository),
                observeActiveTab = ObserveActiveTabUseCase(observeTabs),
                observeTabs = observeTabs,
                updateActiveTabUrlAndTitle = UpdateActiveTabUrlAndTitleUseCase(noopTabRepo),
                createTab = CreateTabUseCase(noopTabRepo, UrlConstants.DEFAULT_HOME_URL),
                faviconCache = OfflineErrorNoopFaviconCache,
                screenshotCache = OfflineErrorNoopScreenshotCache,
            ).apply {
                onLoadStarted(UrlConstants.DEFAULT_HOME_URL)
                onLoadFailed(ErrorReason.NetworkUnavailable)
            }
            composeRule.activity.setContent { BrowserScreen(viewModel = viewModel) }
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun errorState_rendersWhenInitialStateIsFailed() {
        composeRule.waitUntilExactlyOneExists(
            matcher = hasTestTag(TEST_TAG_BROWSER_ERROR_STATE),
            timeoutMillis = ERROR_STATE_TIMEOUT_MS,
        )
    }

    private companion object {
        // Pure recomposition with state preseeded — well within 5 s. The
        // earlier 15 s budget targeted real DNS NXDOMAIN, no longer applicable.
        const val ERROR_STATE_TIMEOUT_MS: Long = 5_000L
    }
}

/**
 * Spec 010 — file-private no-op [SearchEngineRepository] for the error-state
 * rendering test. Query branch is never reached.
 */
private object OfflineErrorNoopSearchEngineRepository : SearchEngineRepository {
    override suspend fun buildSearchUrl(query: String): String =
        error("instrumented test should not reach the search-URL build path")
}

/**
 * Spec 011 — file-private no-op [TabRepository] for the error-rendering test.
 * Mirrors the OfflineErrorNoopSearchEngineRepository pattern.
 */
private object OfflineErrorNoopTabRepository : TabRepository {
    override fun observeTabs(): Flow<List<Tab>> = flowOf(emptyList())
    override suspend fun createTab(url: String): Result<Tab> =
        error("error-rendering test should not reach createTab")
    override suspend fun switchActiveTab(tabId: Long) = Unit
    override suspend fun updateTabUrlAndTitle(tabId: Long, url: String, title: String) = Unit
    override suspend fun closeTab(tabId: Long) = Unit
    override suspend fun closeAllTabs() = Unit
    override suspend fun getTabCount(): Int = 0
}

/**
 * Spec 011 favicon amendment — no-op [FaviconCache] for the error-rendering
 * instrumented test. The test never triggers `onReceivedIcon` (loading
 * state seeded to Failed before WebView is constructed).
 */
private object OfflineErrorNoopFaviconCache : FaviconCache {
    private val state = MutableStateFlow(0L)
    override val version: StateFlow<Long> = state.asStateFlow()
    override suspend fun save(url: String, bitmap: Bitmap) = Unit
    override fun fileFor(url: String): File? = null
}

private object OfflineErrorNoopScreenshotCache : ScreenshotCache {
    private val state = MutableStateFlow(0L)
    override val version: StateFlow<Long> = state.asStateFlow()
    override suspend fun save(tabId: Long, bitmap: Bitmap) = Unit
    override fun fileFor(tabId: Long): File? = null
    override suspend fun delete(tabId: Long) = Unit
    override suspend fun clearAll() = Unit
}
