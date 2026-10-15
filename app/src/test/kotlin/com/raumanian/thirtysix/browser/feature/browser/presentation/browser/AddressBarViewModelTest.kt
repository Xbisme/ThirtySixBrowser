package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

import com.raumanian.thirtysix.browser.core.domain.repository.SearchEngineRepository
import com.raumanian.thirtysix.browser.core.domain.util.UrlConstants
import com.raumanian.thirtysix.browser.core.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Query submits build their URL in `viewModelScope`, so those tests advance the dispatcher. Tests
 * that need the page drive the page holder over the same store, as the browser screen does.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AddressBarViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(testDispatcher)

    private fun newAddressBar(searchEngine: SearchEngineRepository? = null): AddressBarViewModel {
        val fixture = BrowserViewModelFixture(url = SAMPLE_PAGE_URL)
        return if (searchEngine == null) fixture.buildAddressBar() else fixture.buildAddressBar(searchEngine)
    }

    // ---------- Text, focus and clear ----------

    @Test
    fun `initial address-bar text is empty and not focused`() = runTest {
        val state = newAddressBar().uiState.value
        assertEquals("", state.text)
        assertEquals(false, state.isFocused)
    }

    @Test
    fun `onAddressBarTextChange updates state`() = runTest {
        val vm = newAddressBar()
        vm.onAction(AddressBarAction.TextChange("foo"))
        assertEquals("foo", vm.uiState.value.text)
    }

    @Test
    fun `onAddressBarFocusChange toggles flag`() = runTest {
        val vm = newAddressBar()
        vm.onAction(AddressBarAction.FocusChange(true))
        assertEquals(true, vm.uiState.value.isFocused)
        vm.onAction(AddressBarAction.FocusChange(false))
        assertEquals(false, vm.uiState.value.isFocused)
    }

    @Test
    fun `onAddressBarClear empties text and preserves focus`() = runTest {
        val vm = newAddressBar()
        vm.onAction(AddressBarAction.TextChange("partial input"))
        vm.onAction(AddressBarAction.FocusChange(true))
        vm.onAction(AddressBarAction.Clear)
        val state = vm.uiState.value
        assertEquals("", state.text)
        assertEquals(true, state.isFocused)
    }

    // ---------- Submit ----------

    @Test
    fun `onAddressBarSubmit returns false on empty input and does not call loadUrl`() = runTest {
        val vm = newAddressBar()
        val calls = eventsOf(vm.events)
        vm.onAction(AddressBarAction.Submit)
        assertTrue(calls.loads().isEmpty())
    }

    @Test
    fun `onAddressBarSubmit returns false on whitespace-only input`() = runTest {
        val vm = newAddressBar()
        vm.onAction(AddressBarAction.TextChange("   "))
        val calls = eventsOf(vm.events)
        vm.onAction(AddressBarAction.Submit)
        assertTrue(calls.loads().isEmpty())
    }

    @Test
    fun `onAddressBarSubmit URL input prepends https and calls loadUrl`() = runTest {
        val vm = newAddressBar()
        vm.onAction(AddressBarAction.TextChange("example.com"))
        val calls = eventsOf(vm.events)
        vm.onAction(AddressBarAction.Submit)
        assertEquals(listOf("https://example.com"), calls.loads())
    }

    @Test
    fun `onAddressBarSubmit explicit http scheme is preserved`() = runTest {
        val vm = newAddressBar()
        vm.onAction(AddressBarAction.TextChange("http://example.com"))
        val calls = eventsOf(vm.events)
        vm.onAction(AddressBarAction.Submit)
        assertEquals(listOf("http://example.com"), calls.loads())
    }

    /** The WebView cancels the first load, so the holder must not coalesce or skip submissions. */
    @Test
    fun `two consecutive submits forward both URLs to loadUrl in order`() = runTest {
        val vm = newAddressBar()
        val calls = eventsOf(vm.events)

        vm.onAction(AddressBarAction.TextChange("first.com"))
        vm.onAction(AddressBarAction.Submit)
        vm.onAction(AddressBarAction.TextChange("second.com"))
        vm.onAction(AddressBarAction.Submit)

        assertEquals(listOf("https://first.com", "https://second.com"), calls.loads())
    }

    // ---------- Query path ----------

    @Test
    fun `onAddressBarSubmit query input builds Google search URL`() = runTest(testDispatcher) {
        val vm = newAddressBar()
        vm.onAction(AddressBarAction.TextChange("kotlin coroutines"))
        val calls = eventsOf(vm.events)
        vm.onAction(AddressBarAction.Submit)
        advanceUntilIdle()
        // URLEncoder.encode renders space as `+` in form-urlencoded mode.
        assertEquals(listOf("https://www.google.com/search?q=kotlin+coroutines"), calls.loads())
    }

    @Test
    fun `onAddressBarSubmit query encodes special characters`() = runTest(testDispatcher) {
        val vm = newAddressBar()
        vm.onAction(AddressBarAction.TextChange("c++ vs rust"))
        val calls = eventsOf(vm.events)
        vm.onAction(AddressBarAction.Submit)
        advanceUntilIdle()
        assertEquals(listOf("https://www.google.com/search?q=c%2B%2B+vs+rust"), calls.loads())
    }

    @Test
    fun `onAddressBarSubmit query encodes Unicode`() = runTest(testDispatcher) {
        val vm = newAddressBar()
        vm.onAction(AddressBarAction.TextChange("안녕"))
        val calls = eventsOf(vm.events)
        vm.onAction(AddressBarAction.Submit)
        advanceUntilIdle()
        assertEquals(listOf("https://www.google.com/search?q=%EC%95%88%EB%85%95"), calls.loads())
    }

    @Test
    fun `query branch dispatches to the search engine`() = runTest(testDispatcher) {
        var receivedQuery: String? = null
        val recordingRepo = object : SearchEngineRepository {
            override suspend fun buildSearchUrl(query: String): String {
                receivedQuery = query
                return "https://example.test/search?q=$query"
            }
        }
        val vm = newAddressBar(recordingRepo)
        vm.onAction(AddressBarAction.TextChange("kotlin"))
        val calls = eventsOf(vm.events)
        vm.onAction(AddressBarAction.Submit)
        advanceUntilIdle()

        assertEquals("kotlin", receivedQuery)
        assertEquals(listOf("https://example.test/search?q=kotlin"), calls.loads())
    }

    @Test
    fun `URL branch does not invoke the search engine`() = runTest(testDispatcher) {
        val watcher = WatchingSearchEngine()
        val vm = newAddressBar(watcher)
        vm.onAction(AddressBarAction.TextChange("example.com"))
        val calls = eventsOf(vm.events)
        vm.onAction(AddressBarAction.Submit)
        advanceUntilIdle()

        assertEquals(false, watcher.called)
        assertEquals(listOf("https://example.com"), calls.loads())
    }

    @Test
    fun `empty submit never invokes the search engine`() = runTest(testDispatcher) {
        val watcher = WatchingSearchEngine()
        val vm = newAddressBar(watcher)
        val calls = eventsOf(vm.events)
        vm.onAction(AddressBarAction.Submit)
        advanceUntilIdle()

        assertEquals(false, watcher.called)
        assertTrue(calls.loads().isEmpty())
    }

    // ---------- With the page holder over the same store ----------

    @Test
    fun `onUrlChanged updates currentUrl and does not affect address-bar text or focus`() = runTest {
        val fixture = BrowserViewModelFixture(url = "https://example.com")
        val page = fixture.build()
        val addressBar = fixture.buildAddressBar()
        addressBar.onAction(AddressBarAction.TextChange("typing-in-progress"))
        addressBar.onAction(AddressBarAction.FocusChange(true))
        page.onAction(BrowserAction.Page.UrlChanged("https://example.com/about"))
        val state = page.screenState(addressBar)
        assertEquals("https://example.com/about", state.currentUrl)
        assertEquals("typing-in-progress", state.addressBarText)
        assertEquals(true, state.isAddressBarFocused)
    }

    /** The screen's load mirrors the real WebView, whose `onPageStarted` reports the URL at once. */
    @Test
    fun `submit triggers immediate currentUrl update via onUrlChanged`() = runTest {
        val fixture = BrowserViewModelFixture(url = "https://before.example.com")
        val page = fixture.build()
        val addressBar = fixture.buildAddressBar()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            addressBar.events.collect { event ->
                if (event is AddressBarEvent.LoadUrl) page.onAction(BrowserAction.Page.UrlChanged(event.url))
            }
        }

        addressBar.onAction(AddressBarAction.TextChange("example.com"))
        addressBar.onAction(AddressBarAction.Submit)

        assertEquals("https://example.com", page.screenState(addressBar).currentUrl)
    }

    // ---------- Home inside a tab ----------

    @Test
    fun `Home pressed on a page overlays Home and Back returns to the page`() = runTest {
        val fixture = BrowserViewModelFixture(url = "https://page.test/")
        val page = fixture.build()
        val addressBar = fixture.buildAddressBar()
        advanceUntilIdle()
        addressBar.onAction(AddressBarAction.HomePressed)
        assertTrue(page.screenState(addressBar).isHomeContent)
        assertEquals("https://page.test/", page.screenState(addressBar).currentUrl)
        addressBar.onAction(AddressBarAction.HomeOverlayDismissed)
        assertFalse(page.screenState(addressBar).isHomeContent)
    }

    @Test
    fun `Home submit loads the same URL as the address bar and marks cameFromHome`() = runTest {
        val vm = BrowserViewModelFixture(url = UrlConstants.HOME_PAGE_URL).buildAddressBar()
        val events = eventsOf(vm.events)
        vm.onAction(AddressBarAction.HomeSubmit("kotlinlang.org"))
        val fromHome = events.loads().single()
        vm.onAction(AddressBarAction.TextChange("kotlinlang.org"))
        vm.onAction(AddressBarAction.Submit)
        assertEquals(listOf(fromHome, fromHome), events.loads())
        assertTrue(vm.uiState.value.cameFromHome)
    }

    @Test
    fun `blank Home submit does nothing`() = runTest {
        val vm = BrowserViewModelFixture(url = UrlConstants.HOME_PAGE_URL).buildAddressBar()
        val events = eventsOf(vm.events)
        vm.onAction(AddressBarAction.HomeSubmit("   "))
        assertTrue(events.loads().isEmpty())
        assertFalse(vm.uiState.value.cameFromHome)
    }

    @Test
    fun `Back without history returns to Home only when the page came from Home`() = runTest {
        val fixture = BrowserViewModelFixture(url = UrlConstants.HOME_PAGE_URL)
        val page = fixture.build()
        val addressBar = fixture.buildAddressBar()
        advanceUntilIdle()
        val before = page.screenState(addressBar)
        addressBar.onAction(AddressBarAction.BackWithoutHistory)
        assertEquals(before, page.screenState(addressBar))

        addressBar.onAction(AddressBarAction.NavigateFromHome)
        page.onAction(BrowserAction.Page.UrlChanged("https://page.test/"))
        assertFalse(page.screenState(addressBar).isHomeContent)
        addressBar.onAction(AddressBarAction.BackWithoutHistory)
        assertTrue(page.screenState(addressBar).isHomeContent)
        assertFalse(page.screenState(addressBar).cameFromHome)
    }

    private class WatchingSearchEngine : SearchEngineRepository {
        var called = false
            private set

        override suspend fun buildSearchUrl(query: String): String {
            called = true
            return "should-not-be-used"
        }
    }
}

/** A real page address for tests; the app's default is the Home page. */
private const val SAMPLE_PAGE_URL: String = "https://www.google.com/"
