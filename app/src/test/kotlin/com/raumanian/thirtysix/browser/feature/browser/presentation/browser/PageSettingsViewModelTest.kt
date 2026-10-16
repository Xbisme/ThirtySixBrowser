package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

import app.cash.turbine.test
import com.raumanian.thirtysix.browser.core.domain.model.TextSize
import com.raumanian.thirtysix.browser.core.testing.MainDispatcherRule
import com.raumanian.thirtysix.browser.core.testing.fake.FakeSettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PageSettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(testDispatcher)

    @Test
    fun `the page follows text size and the swipe switch`() = runTest(testDispatcher) {
        val settings = FakeSettingsRepository()
        val vm = PageSettingsViewModel(settings)

        vm.uiState.test {
            assertEquals(PageSettingsUiState(), awaitItem())
            settings.setSwipeNavigationEnabled(false)
            assertEquals(false, awaitItem().swipeNavigationEnabled)
            settings.setTextSize(TextSize.entries.last())
            assertEquals(TextSize.entries.last().percent, awaitItem().textZoomPercent)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
