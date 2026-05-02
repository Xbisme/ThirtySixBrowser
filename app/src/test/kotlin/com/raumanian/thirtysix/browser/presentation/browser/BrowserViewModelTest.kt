package com.raumanian.thirtysix.browser.presentation.browser

import app.cash.turbine.test
import com.raumanian.thirtysix.browser.core.constants.UrlConstants
import com.raumanian.thirtysix.browser.domain.repository.SearchEngineRepository
import com.raumanian.thirtysix.browser.domain.usecase.BuildSearchUrlUseCase
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Spec 007 — pure JVM unit tests for [BrowserViewModel] (US1 happy-path scenarios).
 *
 * Failed-state tests live in T034 (US3 phase) once `ErrorReason` and
 * `LoadingState.Failed` exist. This test class restricts itself to
 * Idle / Loading / Loaded transitions per [contracts/browser-screen-contract.md].
 *
 * Spec 010 — query-branch tests now exercise a real [BuildSearchUrlUseCase]
 * backed by a [GoogleByteIdenticalRepository] fake. The fake reproduces the
 * exact `URLEncoder.encode(query, "UTF-8")` + `GOOGLE_SEARCH_URL_TEMPLATE`
 * formula Spec 009 used inline so SC-001 byte-identity holds at the ViewModel
 * boundary as well. The query branch now runs inside `viewModelScope.launch`
 * so query tests advance the [StandardTestDispatcher] before asserting.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BrowserViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newViewModel(
        url: String = UrlConstants.DEFAULT_HOME_URL,
        buildSearchUrl: BuildSearchUrlUseCase = BuildSearchUrlUseCase(GoogleByteIdenticalRepository),
    ): BrowserViewModel = BrowserViewModel(defaultHomeUrl = url, buildSearchUrl = buildSearchUrl)

    /**
     * Test fake reproducing the exact Spec 009 inline-encoding formula so the
     * Google query-path assertions stay byte-identical to the prior production
     * behavior (SC-001 non-regression).
     */
    private object GoogleByteIdenticalRepository : SearchEngineRepository {
        override suspend fun buildSearchUrl(query: String): String {
            val encoded = URLEncoder.encode(query, Charsets.UTF_8.name())
            return String.format(UrlConstants.GOOGLE_SEARCH_URL_TEMPLATE, encoded)
        }
    }

    @Test
    fun `initial state matches injected URL and Idle loading`() = runTest {
        val vm = newViewModel("https://example.com")
        vm.uiState.test {
            val first = awaitItem()
            assertEquals("https://example.com", first.currentUrl)
            assertEquals(LoadingState.Idle, first.loadingState)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `happy path Idle to Loading to Loaded via progress`() = runTest {
        val vm = newViewModel()
        // Spec 009 — production wiring fires onUrlChange BEFORE onLoadStarted
        // (see `BrowserWebViewClient.onPageStarted`). Mirror that order here
        // since `onLoadStarted` no longer mutates `currentUrl` after the Spec
        // 009 refactor.
        vm.onUrlChanged("https://example.com")
        vm.onLoadStarted("https://example.com")
        vm.onProgressChanged(EARLY_PROGRESS)
        vm.onProgressChanged(MID_PROGRESS)
        vm.onProgressChanged(MAX_PROGRESS)

        val state = vm.uiState.value
        assertEquals("https://example.com", state.currentUrl)
        assertEquals(LoadingState.Loaded, state.loadingState)
    }

    @Test
    fun `loaded via onPageFinished without final progress event`() = runTest {
        val vm = newViewModel()
        vm.onLoadStarted("https://example.com")
        vm.onProgressChanged(MID_PROGRESS) // never reaches 100
        vm.onLoadFinished("https://example.com")

        val state = vm.uiState.value
        assertEquals(LoadingState.Loaded, state.loadingState)
    }

    @Test
    fun `onLoadFinished is idempotent`() = runTest {
        val vm = newViewModel()
        vm.onLoadStarted("https://example.com")
        vm.onLoadFinished("https://example.com")
        val firstLoaded = vm.uiState.value
        vm.onLoadFinished("https://example.com") // second call
        val secondLoaded = vm.uiState.value

        assertEquals(firstLoaded, secondLoaded)
        assertEquals(LoadingState.Loaded, secondLoaded.loadingState)
    }

    @Test
    fun `progress over 100 is clamped to Loaded`() = runTest {
        val vm = newViewModel()
        vm.onLoadStarted("https://example.com")
        vm.onProgressChanged(OVER_PROGRESS) // defensive: 150
        assertEquals(LoadingState.Loaded, vm.uiState.value.loadingState)
    }

    @Test
    fun `progress under 0 is clamped to Loading 0f`() = runTest {
        val vm = newViewModel()
        vm.onLoadStarted("https://example.com")
        vm.onProgressChanged(NEGATIVE_PROGRESS) // defensive: -5
        val state = vm.uiState.value
        assertTrue(state.loadingState is LoadingState.Loading)
        assertEquals(0f, (state.loadingState as LoadingState.Loading).progress, FLOAT_TOLERANCE)
    }

    @Test
    fun `transitions to Failed on onLoadFailed during loading`() = runTest {
        val vm = newViewModel()
        vm.onLoadStarted("https://example.com")
        vm.onLoadFailed(ErrorReason.NetworkUnavailable)
        val state = vm.uiState.value
        assertTrue(state.loadingState is LoadingState.Failed)
        assertEquals(ErrorReason.NetworkUnavailable, (state.loadingState as LoadingState.Failed).reason)
    }

    @Test
    fun `Loaded then onLoadStarted again then onLoadFailed lands on Failed`() = runTest {
        val vm = newViewModel()
        vm.onLoadStarted("https://example.com")
        vm.onLoadFinished("https://example.com")
        vm.onLoadStarted("https://other.example.com") // simulating reload (Spec 008 will trigger this)
        vm.onLoadFailed(ErrorReason.SslError)
        val state = vm.uiState.value
        assertTrue(state.loadingState is LoadingState.Failed)
        assertEquals(ErrorReason.SslError, (state.loadingState as LoadingState.Failed).reason)
    }

    @Test
    fun `recovery from Failed via onLoadStarted re-enters Loading 0f`() = runTest {
        val vm = newViewModel()
        vm.onLoadStarted("https://example.com")
        vm.onLoadFailed(ErrorReason.DnsFailure)
        // Now retry
        vm.onLoadStarted("https://example.com")
        val state = vm.uiState.value
        assertTrue(state.loadingState is LoadingState.Loading)
        assertEquals(0f, (state.loadingState as LoadingState.Loading).progress, FLOAT_TOLERANCE)
    }

    @Test
    fun `progress integer maps to expected Float in 0_to_1f range`() = runTest {
        val vm = newViewModel()
        vm.onLoadStarted("https://example.com")
        vm.onProgressChanged(0)
        assertEquals(0f, (vm.uiState.value.loadingState as LoadingState.Loading).progress, FLOAT_TOLERANCE)
        vm.onProgressChanged(EARLY_PROGRESS) // 25
        assertEquals(0.25f, (vm.uiState.value.loadingState as LoadingState.Loading).progress, FLOAT_TOLERANCE)
        vm.onProgressChanged(MID_PROGRESS) // 50
        assertEquals(0.5f, (vm.uiState.value.loadingState as LoadingState.Loading).progress, FLOAT_TOLERANCE)
        vm.onProgressChanged(NEAR_PROGRESS) // 99
        assertEquals(0.99f, (vm.uiState.value.loadingState as LoadingState.Loading).progress, FLOAT_TOLERANCE)
    }

    // ---------- Spec 008 (T026) — canGoBack / canGoForward state mutators ----------

    @Test
    fun `initial canGoBack and canGoForward are false`() = runTest {
        val vm = newViewModel()
        val state = vm.uiState.value
        assertEquals(false, state.canGoBack)
        assertEquals(false, state.canGoForward)
    }

    @Test
    fun `onCanGoBackChanged true propagates to state`() = runTest {
        val vm = newViewModel()
        vm.onCanGoBackChanged(true)
        assertEquals(true, vm.uiState.value.canGoBack)
        assertEquals(false, vm.uiState.value.canGoForward) // independent
    }

    @Test
    fun `onCanGoBackChanged false reverts to false`() = runTest {
        val vm = newViewModel()
        vm.onCanGoBackChanged(true)
        vm.onCanGoBackChanged(false)
        assertEquals(false, vm.uiState.value.canGoBack)
    }

    @Test
    fun `onCanGoForwardChanged true propagates to state`() = runTest {
        val vm = newViewModel()
        vm.onCanGoForwardChanged(true)
        assertEquals(true, vm.uiState.value.canGoForward)
        assertEquals(false, vm.uiState.value.canGoBack) // independent
    }

    @Test
    fun `onCanGoBackChanged and onCanGoForwardChanged are independent`() = runTest {
        val vm = newViewModel()
        vm.onCanGoBackChanged(true)
        vm.onCanGoForwardChanged(true)
        val state = vm.uiState.value
        assertEquals(true, state.canGoBack)
        assertEquals(true, state.canGoForward)

        vm.onCanGoBackChanged(false)
        val after = vm.uiState.value
        assertEquals(false, after.canGoBack)
        assertEquals(true, after.canGoForward) // forward retains its value
    }

    // ---------- Spec 008 (T026) — onLoadStopped + homeUrl getter ----------

    @Test
    fun `onLoadStopped during Loading transitions to Loaded`() = runTest {
        val vm = newViewModel()
        vm.onLoadStarted("https://example.com")
        vm.onProgressChanged(MID_PROGRESS) // partial load
        assertTrue(vm.uiState.value.loadingState is LoadingState.Loading)

        vm.onLoadStopped()
        assertEquals(LoadingState.Loaded, vm.uiState.value.loadingState)
    }

    @Test
    fun `onLoadStopped from non-Loading state is no-op`() = runTest {
        val vm = newViewModel()
        // From Idle
        vm.onLoadStopped()
        assertEquals(LoadingState.Idle, vm.uiState.value.loadingState)

        // From Loaded
        vm.onLoadStarted("https://example.com")
        vm.onLoadFinished("https://example.com")
        val loaded = vm.uiState.value
        vm.onLoadStopped()
        assertEquals(loaded, vm.uiState.value)

        // From Failed
        vm.onLoadFailed(ErrorReason.NetworkUnavailable)
        val failed = vm.uiState.value
        vm.onLoadStopped()
        assertEquals(failed, vm.uiState.value)
    }

    @Test
    fun `homeUrl getter returns the injected default URL`() = runTest {
        val vm = newViewModel("https://www.google.com/")
        assertEquals("https://www.google.com/", vm.homeUrl)
    }

    // ---------- Spec 009 (T009) — Foundational address-bar default state ----------

    @Test
    fun `initial address-bar text is empty and not focused`() = runTest {
        val vm = newViewModel()
        val state = vm.uiState.value
        assertEquals("", state.addressBarText)
        assertEquals(false, state.isAddressBarFocused)
    }

    // ---------- Spec 009 (T011) — onAddressBarTextChange / FocusChange / Submit (US1) ----------

    @Test
    fun `onAddressBarTextChange updates state`() = runTest {
        val vm = newViewModel()
        vm.onAddressBarTextChange("foo")
        assertEquals("foo", vm.uiState.value.addressBarText)
    }

    @Test
    fun `onAddressBarFocusChange toggles flag`() = runTest {
        val vm = newViewModel()
        vm.onAddressBarFocusChange(true)
        assertEquals(true, vm.uiState.value.isAddressBarFocused)
        vm.onAddressBarFocusChange(false)
        assertEquals(false, vm.uiState.value.isAddressBarFocused)
    }

    @Test
    fun `onAddressBarSubmit returns false on empty input and does not call loadUrl`() = runTest {
        val vm = newViewModel()
        val calls = mutableListOf<String>()
        val submitted = vm.onAddressBarSubmit { calls.add(it) }
        assertEquals(false, submitted)
        assertTrue(calls.isEmpty())
    }

    @Test
    fun `onAddressBarSubmit returns false on whitespace-only input`() = runTest {
        val vm = newViewModel()
        vm.onAddressBarTextChange("   ")
        val calls = mutableListOf<String>()
        val submitted = vm.onAddressBarSubmit { calls.add(it) }
        assertEquals(false, submitted)
        assertTrue(calls.isEmpty())
    }

    @Test
    fun `onAddressBarSubmit URL input prepends https and calls loadUrl`() = runTest {
        val vm = newViewModel()
        vm.onAddressBarTextChange("example.com")
        val calls = mutableListOf<String>()
        val submitted = vm.onAddressBarSubmit { calls.add(it) }
        assertEquals(true, submitted)
        assertEquals(listOf("https://example.com"), calls)
    }

    @Test
    fun `onAddressBarSubmit explicit http scheme is preserved`() = runTest {
        val vm = newViewModel()
        vm.onAddressBarTextChange("http://example.com")
        val calls = mutableListOf<String>()
        vm.onAddressBarSubmit { calls.add(it) }
        assertEquals(listOf("http://example.com"), calls)
    }

    /**
     * C1 remediation — FR-013 cancel-previous-load assertion. Two consecutive
     * non-empty submits MUST forward both URLs to `loadUrl` in order. The
     * underlying WebView platform contract guarantees the second `loadUrl`
     * cancels the first; this test asserts the ViewModel does not coalesce
     * or skip submissions.
     */
    @Test
    fun `two consecutive submits forward both URLs to loadUrl in order (FR-013)`() = runTest {
        val vm = newViewModel()
        val calls = mutableListOf<String>()
        val loadUrl: (String) -> Unit = { calls.add(it) }

        vm.onAddressBarTextChange("first.com")
        val first = vm.onAddressBarSubmit(loadUrl)
        vm.onAddressBarTextChange("second.com")
        val second = vm.onAddressBarSubmit(loadUrl)

        assertEquals(true, first)
        assertEquals(true, second)
        assertEquals(listOf("https://first.com", "https://second.com"), calls)
    }

    // ---------- Spec 009 (T020 — US2) — onAddressBarSubmit query path ----------

    @Test
    fun `onAddressBarSubmit query input builds Google search URL`() = runTest(testDispatcher) {
        val vm = newViewModel()
        vm.onAddressBarTextChange("kotlin coroutines")
        val calls = mutableListOf<String>()
        val submitted = vm.onAddressBarSubmit { calls.add(it) }
        assertEquals(true, submitted)
        advanceUntilIdle()
        // URLEncoder.encode renders space as `+` in form-urlencoded mode.
        assertEquals(listOf("https://www.google.com/search?q=kotlin+coroutines"), calls)
    }

    @Test
    fun `onAddressBarSubmit query encodes special characters`() = runTest(testDispatcher) {
        val vm = newViewModel()
        vm.onAddressBarTextChange("c++ vs rust")
        val calls = mutableListOf<String>()
        vm.onAddressBarSubmit { calls.add(it) }
        advanceUntilIdle()
        // `+` becomes `%2B`; space becomes `+`.
        assertEquals(listOf("https://www.google.com/search?q=c%2B%2B+vs+rust"), calls)
    }

    @Test
    fun `onAddressBarSubmit query encodes Unicode`() = runTest(testDispatcher) {
        val vm = newViewModel()
        vm.onAddressBarTextChange("안녕")
        val calls = mutableListOf<String>()
        vm.onAddressBarSubmit { calls.add(it) }
        advanceUntilIdle()
        // Korean "annyeong" UTF-8 percent-encoded.
        assertEquals(listOf("https://www.google.com/search?q=%EC%95%88%EB%85%95"), calls)
    }

    // ---------- Spec 010 — onAddressBarSubmit query path routes through BuildSearchUrlUseCase ----------

    @Test
    fun `query branch dispatches to BuildSearchUrlUseCase`() = runTest(testDispatcher) {
        var receivedQuery: String? = null
        val recordingRepo = object : SearchEngineRepository {
            override suspend fun buildSearchUrl(query: String): String {
                receivedQuery = query
                return "https://example.test/search?q=$query"
            }
        }
        val vm = newViewModel(buildSearchUrl = BuildSearchUrlUseCase(recordingRepo))
        vm.onAddressBarTextChange("kotlin")
        val calls = mutableListOf<String>()
        val submitted = vm.onAddressBarSubmit { calls.add(it) }
        advanceUntilIdle()

        assertEquals(true, submitted)
        assertEquals("kotlin", receivedQuery)
        assertEquals(listOf("https://example.test/search?q=kotlin"), calls)
    }

    @Test
    fun `URL branch does not invoke BuildSearchUrlUseCase`() = runTest(testDispatcher) {
        var useCaseCalled = false
        val watcher = object : SearchEngineRepository {
            override suspend fun buildSearchUrl(query: String): String {
                useCaseCalled = true
                return "should-not-be-used"
            }
        }
        val vm = newViewModel(buildSearchUrl = BuildSearchUrlUseCase(watcher))
        vm.onAddressBarTextChange("example.com")
        val calls = mutableListOf<String>()
        vm.onAddressBarSubmit { calls.add(it) }
        advanceUntilIdle()

        assertEquals(false, useCaseCalled)
        assertEquals(listOf("https://example.com"), calls)
    }

    @Test
    fun `empty submit returns false and never invokes BuildSearchUrlUseCase`() = runTest(testDispatcher) {
        var useCaseCalled = false
        val watcher = object : SearchEngineRepository {
            override suspend fun buildSearchUrl(query: String): String {
                useCaseCalled = true
                return "irrelevant"
            }
        }
        val vm = newViewModel(buildSearchUrl = BuildSearchUrlUseCase(watcher))
        // No onAddressBarTextChange call → addressBarText is empty.
        val calls = mutableListOf<String>()
        val submitted = vm.onAddressBarSubmit { calls.add(it) }
        advanceUntilIdle()

        assertEquals(false, submitted)
        assertEquals(false, useCaseCalled)
        assertTrue(calls.isEmpty())
    }

    // ---------- Spec 009 (T024 — US3) — onUrlChanged + onLoadStarted refactor ----------

    @Test
    fun `onUrlChanged updates currentUrl and does not affect addressBarText or focus`() = runTest {
        val vm = newViewModel("https://example.com")
        vm.onAddressBarTextChange("typing-in-progress")
        vm.onAddressBarFocusChange(true)
        vm.onUrlChanged("https://example.com/about")
        val state = vm.uiState.value
        assertEquals("https://example.com/about", state.currentUrl)
        assertEquals("typing-in-progress", state.addressBarText)
        assertEquals(true, state.isAddressBarFocused)
    }

    /**
     * Spec 009 refactor — `onLoadStarted` previously mutated `currentUrl`
     * (Spec 007 behavior); the responsibility moved to [onUrlChanged]. This
     * test asserts the refactor: `onLoadStarted` only flips loading state.
     */
    @Test
    fun `onLoadStarted no longer mutates currentUrl after Spec 009 refactor`() = runTest {
        val vm = newViewModel("https://initial.example.com")
        vm.onLoadStarted("https://other.example.com")
        val state = vm.uiState.value
        assertEquals("https://initial.example.com", state.currentUrl)
        assertTrue(state.loadingState is LoadingState.Loading)
    }

    /**
     * C2 remediation — FR-019a immediacy. After submit, the WebView's
     * `onPageStarted` callback fires synchronously; the test wires a stub
     * `loadUrl` that calls `onUrlChanged(target)` directly to mirror that
     * platform contract. Asserts `currentUrl` reflects the submitted target
     * before any `onLoadFinished` callback fires.
     */
    @Test
    fun `submit triggers immediate currentUrl update via onUrlChanged (FR-019a)`() = runTest {
        val vm = newViewModel("https://before.example.com")
        // Stub mirrors the real WebView's onPageStarted → onUrlChanged chain.
        val loadUrl: (String) -> Unit = { url -> vm.onUrlChanged(url) }

        vm.onAddressBarTextChange("example.com")
        val submitted = vm.onAddressBarSubmit(loadUrl)

        assertEquals(true, submitted)
        assertEquals("https://example.com", vm.uiState.value.currentUrl)
    }

    // ---------- Spec 009 (T034 — US4) — onAddressBarClear ----------

    @Test
    fun `onAddressBarClear empties text and preserves focus`() = runTest {
        val vm = newViewModel()
        vm.onAddressBarTextChange("partial input")
        vm.onAddressBarFocusChange(true)
        vm.onAddressBarClear()
        val state = vm.uiState.value
        assertEquals("", state.addressBarText)
        assertEquals(true, state.isAddressBarFocused)
    }

    private companion object {
        const val EARLY_PROGRESS: Int = 25
        const val MID_PROGRESS: Int = 50
        const val NEAR_PROGRESS: Int = 99
        const val MAX_PROGRESS: Int = 100
        const val OVER_PROGRESS: Int = 150
        const val NEGATIVE_PROGRESS: Int = -5
        const val FLOAT_TOLERANCE: Float = 0.0001f
    }
}
