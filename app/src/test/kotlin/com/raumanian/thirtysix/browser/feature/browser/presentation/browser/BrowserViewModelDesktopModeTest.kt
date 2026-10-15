package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

import com.raumanian.thirtysix.browser.core.domain.model.Tab
import com.raumanian.thirtysix.browser.core.testing.MainDispatcherRule
import com.raumanian.thirtysix.browser.core.testing.fake.FakeTabRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Desktop mode belongs to one tab and lives in memory only. */
@OptIn(ExperimentalCoroutinesApi::class)
class BrowserViewModelDesktopModeTest {

    private val dispatcher = StandardTestDispatcher()
    private val tabs = FakeTabRepository(initialTabs = listOf(tab(1L), tab(2L)))

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(dispatcher)

    private fun TestScope.newViewModel(): BrowserViewModel {
        val vm = BrowserViewModelFixture(tabs = tabs).build()
        backgroundScope.launch { vm.activeTab.collect { } }
        backgroundScope.launch { vm.uiState.collect { } }
        advanceUntilIdle()
        return vm
    }

    @Test
    fun `toggling affects only the active tab`() = runTest {
        val vm = newViewModel()
        assertEquals(2L, vm.activeTab.value?.id)
        val events = eventsOf(vm)

        vm.onAction(BrowserAction.ToggleDesktopMode)
        assertEquals(listOf(BrowserEvent.SetDesktopMode(true)), events)
        advanceUntilIdle()

        assertTrue(vm.desktopMode.isDesktop(2L))
        assertFalse(vm.desktopMode.isDesktop(1L))
        assertTrue(vm.uiState.value.isDesktopMode)
    }

    @Test
    fun `toggling twice returns to mobile`() = runTest {
        val vm = newViewModel()
        val events = eventsOf(vm)
        vm.onAction(BrowserAction.ToggleDesktopMode)
        vm.onAction(BrowserAction.ToggleDesktopMode)
        assertEquals(BrowserEvent.SetDesktopMode(false), events.last())
        advanceUntilIdle()
        assertFalse(vm.desktopMode.isDesktop(2L))
        assertFalse(vm.uiState.value.isDesktopMode)
    }

    @Test
    fun `switching tab shows that tab's own mode, and a new tab is mobile`() = runTest {
        val vm = newViewModel()
        vm.onAction(BrowserAction.ToggleDesktopMode)
        // Explicit activity times: the fake's own clock can tie within one millisecond.
        tabs.emit(listOf(tab(1L, lastActiveAt = 10L), tab(2L, lastActiveAt = 3L)))
        advanceUntilIdle()
        assertEquals(1L, vm.activeTab.value?.id)
        assertFalse(vm.uiState.value.isDesktopMode)

        tabs.emit(listOf(tab(1L, lastActiveAt = 10L), tab(2L, lastActiveAt = 20L)))
        advanceUntilIdle()
        assertTrue(vm.uiState.value.isDesktopMode)

        tabs.emit(listOf(tab(1L, lastActiveAt = 10L), tab(2L, lastActiveAt = 20L), tab(3L, lastActiveAt = 30L)))
        advanceUntilIdle()
        assertEquals(3L, vm.activeTab.value?.id)
        assertFalse(vm.desktopMode.isDesktop(3L))
        assertFalse(vm.uiState.value.isDesktopMode)
    }

    @Test
    fun `a fresh view model, as after a process restart, has every tab in mobile mode`() = runTest {
        newViewModel().onAction(BrowserAction.ToggleDesktopMode)
        val restarted = newViewModel()
        assertFalse(restarted.desktopMode.isDesktop(1L))
        assertFalse(restarted.desktopMode.isDesktop(2L))
        assertFalse(restarted.uiState.value.isDesktopMode)
    }

    @Test
    fun `with no active tab, toggling does nothing`() = runTest {
        val vm = BrowserViewModelFixture(tabs = tabs).build()
        val events = eventsOf(vm)
        vm.onAction(BrowserAction.ToggleDesktopMode)
        assertTrue(events.isEmpty())
    }

    private companion object {
        fun tab(id: Long, lastActiveAt: Long = id) = Tab(
            id = id,
            url = "https://e$id.example/",
            title = "",
            position = id.toInt(),
            createdAt = id,
            lastActiveAt = lastActiveAt,
        )
    }
}
