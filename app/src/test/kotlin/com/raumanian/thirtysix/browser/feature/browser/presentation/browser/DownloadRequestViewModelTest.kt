package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

import app.cash.turbine.test
import com.raumanian.thirtysix.browser.core.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadRequestViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(dispatcher)

    private val vm = DownloadRequestViewModel(noOpStartDownloadUseCase())

    @Test
    fun `a download the platform cannot take reports the service unavailable`() = runTest(dispatcher) {
        vm.onAction(DownloadRequestAction.Requested("https://example.com/a.pdf", "UA", null, "application/pdf"))
        advanceUntilIdle()

        vm.events.test {
            assertEquals(DownloadSnackbarEvent.ServiceUnavailable, awaitItem())
            expectNoEvents()
        }
    }

    @Test
    fun `a refused storage permission says whether settings are the only way back`() = runTest(dispatcher) {
        vm.onAction(DownloadRequestAction.StoragePermissionDenied(permanently = false))
        vm.onAction(DownloadRequestAction.StoragePermissionDenied(permanently = true))

        vm.events.test {
            assertEquals(DownloadSnackbarEvent.StoragePermissionDenied, awaitItem())
            assertEquals(DownloadSnackbarEvent.StoragePermissionPermanentlyDenied, awaitItem())
            expectNoEvents()
        }
    }
}
