package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

import com.raumanian.thirtysix.browser.core.domain.model.Tab
import com.raumanian.thirtysix.browser.core.domain.model.TabSession
import com.raumanian.thirtysix.browser.core.domain.repository.TabRepository
import com.raumanian.thirtysix.browser.core.domain.usecase.ObserveAllTabsUseCase
import com.raumanian.thirtysix.browser.core.domain.util.Result
import com.raumanian.thirtysix.browser.core.testing.MainDispatcherRule
import com.raumanian.thirtysix.browser.core.testing.fake.FakeIncognitoTabRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
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
        val first = fixture.tabs.observeTabs().first().single()
        val second = (fixture.tabs.createTab("https://two.example/") as Result.Success).data
        fixture.tabSessions.save(TabSession(first.id, byteArrayOf(1), 1L))
        fixture.tabSessions.save(TabSession(second.id, byteArrayOf(2), 1L))
        val vm = fixture.buildTabSession()

        fixture.pageStore.setActiveTab(first)
        advanceUntilIdle()
        fixture.pageStore.setActiveTab(second)
        advanceUntilIdle()

        assertEquals(second.id, vm.uiState.value.loadedTabId)
        assertArrayEquals(byteArrayOf(2), vm.savedState.savedState(second.id))
        assertNull(vm.savedState.savedState(first.id))
    }

    @Test
    fun `clearing history forgets the shown state and tells the page to drop its back and forward`() =
        runTest(testDispatcher) {
            val fixture = BrowserHoldersFixture()
            fixture.tabSessions.save(TabSession(4L, byteArrayOf(4), 1L))
            val vm = fixture.buildTabSession()
            val events = eventsOf(vm.events)
            fixture.pageStore.setActiveTab(tab(4L))
            advanceUntilIdle()

            fixture.tabSessions.clearAll()
            advanceUntilIdle()

            assertNull(vm.savedState.savedState(4L))
            assertEquals(listOf(TabSessionEvent.ClearLiveHistory), events)
        }

    @Test
    fun `sessions of closed tabs are swept`() = runTest(testDispatcher) {
        val fixture = BrowserHoldersFixture()
        fixture.buildTabSession()
        advanceUntilIdle()
        val openIds = fixture.tabs.observeTabs().first().map { it.id }
        fixture.tabSessions.save(TabSession(openIds.first(), byteArrayOf(1), 1L))
        fixture.tabSessions.save(TabSession(999L, byteArrayOf(2), 1L))
        fixture.tabSessions.save(TabSession(-5L, byteArrayOf(3), 1L))

        fixture.tabs.createTab("https://new.example/")
        advanceUntilIdle()

        assertEquals(setOf(openIds.first()), fixture.tabSessions.sessions.keys)
    }

    @Test
    fun `no sweep runs before any normal tab exists`() = runTest(testDispatcher) {
        val fixture = BrowserHoldersFixture()
        fixture.tabSessions.save(TabSession(1L, byteArrayOf(1), 1L))

        val noNormalTabs = ObserveAllTabsUseCase(NoTabs, FakeIncognitoTabRepository())
        TabSessionViewModel(fixture.pageStore, fixture.tabSessions, noNormalTabs)
        advanceUntilIdle()

        assertEquals(setOf(1L), fixture.tabSessions.sessions.keys)
    }

    /** The moment before Home is seeded: no normal tab yet. */
    private object NoTabs : TabRepository {
        override fun observeTabs(): Flow<List<Tab>> = MutableStateFlow(emptyList())
        override suspend fun createTab(url: String): Result<Tab> = error("not used")
        override suspend fun switchActiveTab(tabId: Long) = Unit
        override suspend fun updateTabUrlAndTitle(tabId: Long, url: String, title: String) = Unit
        override suspend fun closeTab(tabId: Long) = Unit
        override suspend fun closeAllTabs() = Unit
        override suspend fun getTabCount(): Int = 0
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
