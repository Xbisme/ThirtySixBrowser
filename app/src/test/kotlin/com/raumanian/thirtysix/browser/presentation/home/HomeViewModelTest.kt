package com.raumanian.thirtysix.browser.presentation.home

import app.cash.turbine.test
import com.raumanian.thirtysix.browser.core.domain.model.SearchEngine
import com.raumanian.thirtysix.browser.core.domain.model.Tab
import com.raumanian.thirtysix.browser.core.domain.usecase.ObserveActiveTabUseCase
import com.raumanian.thirtysix.browser.core.domain.usecase.ObserveAllTabsUseCase
import com.raumanian.thirtysix.browser.core.domain.usecase.ObserveUserSettingsUseCase
import com.raumanian.thirtysix.browser.core.domain.usecase.SwitchActiveTabUseCase
import com.raumanian.thirtysix.browser.core.domain.util.DispatcherProvider
import com.raumanian.thirtysix.browser.domain.model.GreetingPeriod
import com.raumanian.thirtysix.browser.domain.usecase.ObserveHomeContentUseCase
import com.raumanian.thirtysix.browser.testdoubles.FakeBookmarkRepository
import com.raumanian.thirtysix.browser.testdoubles.FakeFaviconCache
import com.raumanian.thirtysix.browser.testdoubles.FakeHistoryRepository
import com.raumanian.thirtysix.browser.testdoubles.FakeIncognitoTabRepository
import com.raumanian.thirtysix.browser.testdoubles.FakeScreenshotCache
import com.raumanian.thirtysix.browser.testdoubles.FakeSettingsRepository
import com.raumanian.thirtysix.browser.testdoubles.FakeTabRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** Spec 019 T056a — Constitution §VI: HomeViewModel state, greeting and dispatcher. */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(testDispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private class MutableClock(var instant: Instant) : Clock() {
        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: java.time.ZoneId?) = this
        override fun instant(): Instant = instant
    }

    private class TestDispatchers(private val d: CoroutineDispatcher) : DispatcherProvider {
        override val main = d
        override val io = d
        override val default = d
        override val unconfined = d
    }

    private fun at(hour: Int): Instant = LocalDateTime.of(2026, 10, 10, hour, 0).toInstant(ZoneOffset.UTC)

    private fun viewModel(clock: Clock, tabs: FakeTabRepository, history: FakeHistoryRepository) = HomeViewModel(
        observeActiveTab = ObserveActiveTabUseCase(ObserveAllTabsUseCase(tabs, FakeIncognitoTabRepository())),
        observeHomeContent = ObserveHomeContentUseCase(tabs, FakeBookmarkRepository(), history),
        observeUserSettings = ObserveUserSettingsUseCase(FakeSettingsRepository()),
        screenshotCache = FakeScreenshotCache(),
        faviconCache = FakeFaviconCache(),
        clock = clock,
        switchActiveTab = SwitchActiveTabUseCase(tabs),
        dispatchers = TestDispatchers(testDispatcher),
    )

    private fun tab(id: Long, url: String, lastActive: Long) =
        Tab(id = id, url = url, title = "T$id", position = id.toInt(), createdAt = 0L, lastActiveAt = lastActive)

    @Test
    fun state_combinesShortcutsRecentAndEngine() = runTest(testDispatcher) {
        val tabs = FakeTabRepository(
            initialTabs = listOf(tab(1, "https://active.test/", 9), tab(2, "https://other.test/", 1)),
        )
        val history = FakeHistoryRepository().apply { recordVisit("https://h.test/", "H", 1) }
        val vm = viewModel(MutableClock(at(HOUR_EVENING)), tabs, history)

        vm.uiState.test {
            advanceUntilIdle()
            val state = expectMostRecentItem()
            assertEquals(listOf("https://other.test/", "https://h.test/"), state.shortcuts.map { it.shortcut.url })
            assertEquals(listOf("https://h.test/"), state.recent.map { it.url })
            assertEquals(SearchEngine.Google, state.searchEngine)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun greeting_followsTheClock_andRefreshesOnResume() = runTest(testDispatcher) {
        val clock = MutableClock(at(HOUR_MORNING))
        val tabs = FakeTabRepository(initialTabs = listOf(tab(1, "https://a.test/", 1)))
        val vm = viewModel(clock, tabs, FakeHistoryRepository())

        vm.uiState.test {
            advanceUntilIdle()
            assertEquals(GreetingPeriod.Morning, expectMostRecentItem().greeting)

            clock.instant = at(HOUR_EVENING)
            vm.onResume()
            advanceUntilIdle()
            assertEquals(GreetingPeriod.Evening, expectMostRecentItem().greeting)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private companion object {
        const val HOUR_MORNING = 8
        const val HOUR_EVENING = 20
    }
}
