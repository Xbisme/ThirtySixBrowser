package com.raumanian.thirtysix.browser.presentation.browser

import app.cash.turbine.test
import com.raumanian.thirtysix.browser.core.constants.UrlConstants
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Spec 007 — pure JVM unit tests for [BrowserViewModel] (US1 happy-path scenarios).
 *
 * Failed-state tests live in T034 (US3 phase) once `ErrorReason` and
 * `LoadingState.Failed` exist. This test class restricts itself to
 * Idle / Loading / Loaded transitions per [contracts/browser-screen-contract.md].
 */
class BrowserViewModelTest {

    private fun newViewModel(url: String = UrlConstants.DEFAULT_HOME_URL): BrowserViewModel =
        BrowserViewModel(defaultHomeUrl = url)

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
