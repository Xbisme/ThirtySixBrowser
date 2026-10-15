package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

import com.raumanian.thirtysix.browser.core.domain.model.Tab
import com.raumanian.thirtysix.browser.core.domain.util.BrowserLimits
import com.raumanian.thirtysix.browser.core.testing.MainDispatcherRule
import com.raumanian.thirtysix.browser.core.testing.fake.FakeTabRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The find-in-page state machine. */
@OptIn(ExperimentalCoroutinesApi::class)
class FindInPageViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(dispatcher)

    @Test
    fun `opening starts empty with no count`() = runTest {
        val vm = BrowserViewModelFixture().buildFind()
        vm.onAction(FindInPageAction.Opened)
        assertEquals(FindInPageState(isOpen = true), vm.state.value)
    }

    @Test
    fun `the query is cut at the find text limit`() = runTest {
        val vm = BrowserViewModelFixture().buildFind()
        vm.onAction(FindInPageAction.Opened)
        vm.onAction(FindInPageAction.QueryChanged("x".repeat(BrowserLimits.MAX_FIND_QUERY_LENGTH + 50)))
        assertEquals(BrowserLimits.MAX_FIND_QUERY_LENGTH, vm.state.value.query.length)
    }

    @Test
    fun `a result shows the one-based current match and the total`() = runTest {
        val vm = BrowserViewModelFixture().buildFind()
        vm.onAction(FindInPageAction.Opened)
        vm.onAction(FindInPageAction.QueryChanged("lorem"))
        vm.onAction(FindInPageAction.Result(activeOrdinal = 0, total = 12))
        assertEquals(1, vm.state.value.current)
        assertEquals(12, vm.state.value.total)
        vm.onAction(FindInPageAction.Result(activeOrdinal = 11, total = 12))
        assertEquals(12, vm.state.value.current)
    }

    @Test
    fun `no matches reads zero of zero`() = runTest {
        val vm = BrowserViewModelFixture().buildFind()
        vm.onAction(FindInPageAction.Opened)
        vm.onAction(FindInPageAction.QueryChanged("zzz"))
        vm.onAction(FindInPageAction.Result(activeOrdinal = 0, total = 0))
        assertEquals(0, vm.state.value.current)
        assertEquals(0, vm.state.value.total)
    }

    @Test
    fun `a changed query resets the count until its result arrives`() = runTest {
        val vm = BrowserViewModelFixture().buildFind()
        vm.onAction(FindInPageAction.Opened)
        vm.onAction(FindInPageAction.QueryChanged("lorem"))
        vm.onAction(FindInPageAction.Result(activeOrdinal = 3, total = 12))
        vm.onAction(FindInPageAction.QueryChanged("lore"))
        assertEquals(0, vm.state.value.current)
        assertEquals(0, vm.state.value.total)
    }

    @Test
    fun `results arriving after close or for an empty query are ignored`() = runTest {
        val vm = BrowserViewModelFixture().buildFind()
        vm.onAction(FindInPageAction.Opened)
        vm.onAction(FindInPageAction.Result(activeOrdinal = 0, total = 5))
        assertEquals(0, vm.state.value.total)
        vm.onAction(FindInPageAction.QueryChanged("lorem"))
        vm.onAction(FindInPageAction.Closed)
        vm.onAction(FindInPageAction.Result(activeOrdinal = 0, total = 5))
        assertEquals(FindInPageState(), vm.state.value)
    }

    @Test
    fun `the current match never exceeds the total`() = runTest {
        val vm = BrowserViewModelFixture().buildFind()
        vm.onAction(FindInPageAction.Opened)
        vm.onAction(FindInPageAction.QueryChanged("lorem"))
        vm.onAction(FindInPageAction.Result(activeOrdinal = 40, total = 3))
        val state = vm.state.value
        assertTrue(state.current <= state.total)
    }

    @Test
    fun `a navigation closes the find bar`() = runTest {
        val fixture = BrowserViewModelFixture()
        val page = fixture.build()
        val vm = fixture.buildFind()
        advanceUntilIdle()
        vm.onAction(FindInPageAction.Opened)
        vm.onAction(FindInPageAction.QueryChanged("lorem"))
        page.onAction(BrowserAction.Page.LoadStarted("https://example.com/next"))
        advanceUntilIdle()
        assertFalse(vm.state.value.isOpen)
    }

    @Test
    fun `switching tab closes the find bar`() = runTest {
        val fixture = BrowserViewModelFixture(
            tabs = FakeTabRepository(
                initialTabs = listOf(
                    tab(id = 1L, url = "https://example.com/"),
                    tab(id = 2L, url = "https://example.org/"),
                ),
            ),
        )
        val page = fixture.build()
        val vm = fixture.buildFind()
        backgroundScope.launch { page.activeTab.collect { } }
        advanceUntilIdle()
        assertEquals(2L, page.activeTab.value?.id)
        vm.onAction(FindInPageAction.Opened)
        vm.onAction(FindInPageAction.QueryChanged("lorem"))
        fixture.tabs.switchActiveTab(1L)
        advanceUntilIdle()
        assertEquals(1L, page.activeTab.value?.id)
        assertFalse(vm.state.value.isOpen)
    }

    private fun tab(id: Long, url: String) =
        Tab(id = id, url = url, title = "", position = id.toInt(), createdAt = id, lastActiveAt = id)
}
