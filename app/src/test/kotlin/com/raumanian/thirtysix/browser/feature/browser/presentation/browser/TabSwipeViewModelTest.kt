package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

import app.cash.turbine.test
import com.raumanian.thirtysix.browser.core.domain.model.Tab
import com.raumanian.thirtysix.browser.core.domain.model.UserSettings
import com.raumanian.thirtysix.browser.core.domain.repository.ScreenshotCache
import com.raumanian.thirtysix.browser.core.domain.usecase.ObserveAllTabsUseCase
import com.raumanian.thirtysix.browser.core.domain.util.UrlConstants
import com.raumanian.thirtysix.browser.core.testing.MainDispatcherRule
import com.raumanian.thirtysix.browser.core.testing.fake.FakeIncognitoTabRepository
import com.raumanian.thirtysix.browser.core.testing.fake.FakeSettingsRepository
import com.raumanian.thirtysix.browser.core.testing.fake.FakeTabRepository
import com.raumanian.thirtysix.browser.feature.browser.domain.usecase.ObserveTabNeighboursUseCase
import com.raumanian.thirtysix.browser.feature.browser.domain.usecase.SwitchToTabUseCase
import java.io.File
import java.io.OutputStream
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TabSwipeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val normal = FakeTabRepository()
    private val incognito = FakeIncognitoTabRepository()
    private fun viewModel(settings: FakeSettingsRepository = FakeSettingsRepository()) = TabSwipeViewModel(
        observeTabNeighbours = ObserveTabNeighboursUseCase(ObserveAllTabsUseCase(normal, incognito)),
        settings = settings,
        switchToTab = SwitchToTabUseCase(normal, incognito),
        screenshotCache = EveryTabHasAPicture,
    )

    private fun tab(id: Long, position: Int, lastActiveAt: Long, url: String = "https://t$id.example/a") = Tab(
        id = id,
        url = url,
        title = "Title $id",
        position = position,
        createdAt = 0L,
        lastActiveAt = lastActiveAt,
        isIncognito = id < 0L,
    )

    @Test
    fun `neighbours become previews with host and title`() = runTest {
        normal.emit(listOf(tab(1, 0, 10), tab(2, 1, 30), tab(3, 2, 20, url = UrlConstants.HOME_PAGE_URL)))

        viewModel().uiState.test {
            val state = awaitUntil { it.previous != null }
            assertEquals(TabPreview(1L, false, false, "t1.example", "Title 1"), state.previous)
            val home = state.next!!
            assertTrue(home.isHome)
            assertEquals("", home.host)
            assertTrue(state.enabled)
        }
    }

    @Test
    fun `incognito previews carry no title or host and never a picture`() = runTest {
        normal.emit(listOf(tab(1, 0, 10)))
        incognito.emit(listOf(tab(-1, 0, 30), tab(-2, 1, 20)))
        val vm = viewModel()

        vm.uiState.test {
            val next = awaitUntil { it.next != null }.next!!
            assertTrue(next.isIncognito)
            assertEquals("", next.title)
            assertEquals("", next.host)
            assertNull(vm.screenshots.fileFor(next))
        }
    }

    @Test
    fun `a normal preview reads its picture`() = runTest {
        normal.emit(listOf(tab(1, 0, 30), tab(2, 1, 20)))
        val vm = viewModel()

        vm.uiState.test {
            assertNotNull(vm.screenshots.fileFor(awaitUntil { it.next != null }.next!!))
        }
    }

    @Test
    fun `the setting turns the gesture off`() = runTest {
        val off = FakeSettingsRepository(initial = UserSettings.DEFAULT.copy(isTabSwipeEnabled = false))

        viewModel(off).uiState.test {
            assertFalse(awaitUntil { !it.enabled }.enabled)
        }
    }

    @Test
    fun `switching makes the tab active, and only an accessibility switch announces it`() = runTest {
        normal.emit(listOf(tab(1, 0, 30), tab(2, 1, 20), tab(3, 2, 10)))
        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        vm.uiState.first { it.next?.tabId == 2L }

        vm.events.test {
            vm.onAction(TabSwipeAction.SwitchTo(2L, announce = false))
            assertEquals(2L, normal.observeTabs().first().first().id)
            val third = vm.uiState.first { it.next?.tabId == 3L }.next!!

            vm.onAction(TabSwipeAction.SwitchTo(3L, announce = true))

            assertEquals(TabSwipeEvent.Announce(third), awaitItem())
            expectNoEvents()
        }
    }

    private suspend fun app.cash.turbine.ReceiveTurbine<TabSwipeUiState>.awaitUntil(
        predicate: (TabSwipeUiState) -> Boolean,
    ): TabSwipeUiState {
        var item = awaitItem()
        while (!predicate(item)) item = awaitItem()
        return item
    }

    private object EveryTabHasAPicture : ScreenshotCache {
        override val version: StateFlow<Long> = MutableStateFlow(0L)
        override suspend fun save(tabId: Long, writeImage: (OutputStream) -> Unit) = Unit
        override fun fileFor(tabId: Long): File = File("tab-$tabId.webp")
        override suspend fun delete(tabId: Long) = Unit
        override suspend fun clearAll() = Unit
    }
}
