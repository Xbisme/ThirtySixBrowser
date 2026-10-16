package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

import com.raumanian.thirtysix.browser.core.domain.model.Tab
import com.raumanian.thirtysix.browser.core.domain.model.TabSession
import com.raumanian.thirtysix.browser.core.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TabSessionViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(testDispatcher)

    private fun tab(id: Long) =
        Tab(id = id, url = "https://example.com/$id", title = "", position = 0, createdAt = 0L, lastActiveAt = 0L)

    @Test
    fun `the active tab's saved state is read before its page may be built`() = runTest(testDispatcher) {
        val fixture = BrowserHoldersFixture()
        fixture.tabSessions.save(TabSession(5L, byteArrayOf(1, 2), 1L))
        val vm = fixture.buildTabSession()

        assertNull(vm.uiState.value.loadedTabId)
        fixture.pageStore.setActiveTab(tab(5L))
        advanceUntilIdle()

        assertEquals(5L, vm.uiState.value.loadedTabId)
        assertArrayEquals(byteArrayOf(1, 2), vm.savedState.savedState(5L))
        assertNull(vm.savedState.savedState(6L))
    }

    @Test
    fun `a tab with nothing saved is ready with no state`() = runTest(testDispatcher) {
        val fixture = BrowserHoldersFixture()
        val vm = fixture.buildTabSession()

        fixture.pageStore.setActiveTab(tab(7L))
        advanceUntilIdle()

        assertEquals(7L, vm.uiState.value.loadedTabId)
        assertNull(vm.savedState.savedState(7L))
    }

    @Test
    fun `switching tabs reads the new tab's state`() = runTest(testDispatcher) {
        val fixture = BrowserHoldersFixture()
        fixture.tabSessions.save(TabSession(1L, byteArrayOf(1), 1L))
        fixture.tabSessions.save(TabSession(2L, byteArrayOf(2), 1L))
        val vm = fixture.buildTabSession()

        fixture.pageStore.setActiveTab(tab(1L))
        advanceUntilIdle()
        fixture.pageStore.setActiveTab(tab(2L))
        advanceUntilIdle()

        assertEquals(2L, vm.uiState.value.loadedTabId)
        assertArrayEquals(byteArrayOf(2), vm.savedState.savedState(2L))
        assertNull(vm.savedState.savedState(1L))
    }

    @Test
    fun `a save is written and becomes the shown tab's state`() = runTest(testDispatcher) {
        val fixture = BrowserHoldersFixture()
        val vm = fixture.buildTabSession()
        fixture.pageStore.setActiveTab(tab(3L))
        advanceUntilIdle()

        vm.onAction(TabSessionAction.Save(3L, byteArrayOf(9)))
        advanceUntilIdle()

        assertArrayEquals(byteArrayOf(9), fixture.tabSessions.sessions[3L]?.state)
        assertArrayEquals(byteArrayOf(9), vm.savedState.savedState(3L))
    }

    @Test
    fun `a save for a tab that is no longer shown is written without changing the shown state`() =
        runTest(testDispatcher) {
            val fixture = BrowserHoldersFixture()
            fixture.tabSessions.save(TabSession(2L, byteArrayOf(2), 1L))
            val vm = fixture.buildTabSession()
            fixture.pageStore.setActiveTab(tab(2L))
            advanceUntilIdle()

            vm.onAction(TabSessionAction.Save(1L, byteArrayOf(1)))
            advanceUntilIdle()

            assertArrayEquals(byteArrayOf(1), fixture.tabSessions.sessions[1L]?.state)
            assertArrayEquals(byteArrayOf(2), vm.savedState.savedState(2L))
        }
}
