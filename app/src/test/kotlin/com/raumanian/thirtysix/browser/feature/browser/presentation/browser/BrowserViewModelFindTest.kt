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
class BrowserViewModelFindTest {

    private val dispatcher = StandardTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(dispatcher)

    @Test
    fun `opening starts empty with no count`() = runTest {
        val vm = BrowserViewModelFixture().build()
        vm.onAction(BrowserAction.Find.Opened)
        assertEquals(FindInPageState(isOpen = true), vm.findInPage.value)
    }

    @Test
    fun `the query is cut at the find text limit`() = runTest {
        val vm = BrowserViewModelFixture().build()
        vm.onAction(BrowserAction.Find.Opened)
        vm.onAction(BrowserAction.Find.QueryChanged("x".repeat(BrowserLimits.MAX_FIND_QUERY_LENGTH + 50)))
        assertEquals(BrowserLimits.MAX_FIND_QUERY_LENGTH, vm.findInPage.value.query.length)
    }

    @Test
    fun `a result shows the one-based current match and the total`() = runTest {
        val vm = BrowserViewModelFixture().build()
        vm.onAction(BrowserAction.Find.Opened)
        vm.onAction(BrowserAction.Find.QueryChanged("lorem"))
        vm.onAction(BrowserAction.Page.FindResult(activeOrdinal = 0, total = 12))
        assertEquals(1, vm.findInPage.value.current)
        assertEquals(12, vm.findInPage.value.total)
        vm.onAction(BrowserAction.Page.FindResult(activeOrdinal = 11, total = 12))
        assertEquals(12, vm.findInPage.value.current)
    }

    @Test
    fun `no matches reads zero of zero`() = runTest {
        val vm = BrowserViewModelFixture().build()
        vm.onAction(BrowserAction.Find.Opened)
        vm.onAction(BrowserAction.Find.QueryChanged("zzz"))
        vm.onAction(BrowserAction.Page.FindResult(activeOrdinal = 0, total = 0))
        assertEquals(0, vm.findInPage.value.current)
        assertEquals(0, vm.findInPage.value.total)
    }

    @Test
    fun `a changed query resets the count until its result arrives`() = runTest {
        val vm = BrowserViewModelFixture().build()
        vm.onAction(BrowserAction.Find.Opened)
        vm.onAction(BrowserAction.Find.QueryChanged("lorem"))
        vm.onAction(BrowserAction.Page.FindResult(activeOrdinal = 3, total = 12))
        vm.onAction(BrowserAction.Find.QueryChanged("lore"))
        assertEquals(0, vm.findInPage.value.current)
        assertEquals(0, vm.findInPage.value.total)
    }

    @Test
    fun `results arriving after close or for an empty query are ignored`() = runTest {
        val vm = BrowserViewModelFixture().build()
        vm.onAction(BrowserAction.Find.Opened)
        vm.onAction(BrowserAction.Page.FindResult(activeOrdinal = 0, total = 5))
        assertEquals(0, vm.findInPage.value.total)
        vm.onAction(BrowserAction.Find.QueryChanged("lorem"))
        vm.onAction(BrowserAction.Find.Closed)
        vm.onAction(BrowserAction.Page.FindResult(activeOrdinal = 0, total = 5))
        assertEquals(FindInPageState(), vm.findInPage.value)
    }

    @Test
    fun `the current match never exceeds the total`() = runTest {
        val vm = BrowserViewModelFixture().build()
        vm.onAction(BrowserAction.Find.Opened)
        vm.onAction(BrowserAction.Find.QueryChanged("lorem"))
        vm.onAction(BrowserAction.Page.FindResult(activeOrdinal = 40, total = 3))
        val state = vm.findInPage.value
        assertTrue(state.current <= state.total)
    }

    @Test
    fun `a navigation closes the find bar`() = runTest {
        val vm = BrowserViewModelFixture().build()
        vm.onAction(BrowserAction.Find.Opened)
        vm.onAction(BrowserAction.Find.QueryChanged("lorem"))
        vm.onAction(BrowserAction.Page.LoadStarted("https://example.com/next"))
        assertFalse(vm.findInPage.value.isOpen)
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
        val vm = fixture.build()
        backgroundScope.launch { vm.activeTab.collect { } }
        advanceUntilIdle()
        assertEquals(2L, vm.activeTab.value?.id)
        vm.onAction(BrowserAction.Find.Opened)
        vm.onAction(BrowserAction.Find.QueryChanged("lorem"))
        fixture.tabs.switchActiveTab(1L)
        advanceUntilIdle()
        assertEquals(1L, vm.activeTab.value?.id)
        assertFalse(vm.findInPage.value.isOpen)
    }

    private fun tab(id: Long, url: String) =
        Tab(id = id, url = url, title = "", position = id.toInt(), createdAt = id, lastActiveAt = id)
}
