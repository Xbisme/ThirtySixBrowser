package com.raumanian.thirtysix.browser.presentation.browser.suggestions

import com.raumanian.thirtysix.browser.core.domain.model.Bookmark
import com.raumanian.thirtysix.browser.core.domain.util.DispatcherProvider
import com.raumanian.thirtysix.browser.domain.model.SuggestionSource
import com.raumanian.thirtysix.browser.domain.usecase.FilterAddressSuggestionsUseCase
import com.raumanian.thirtysix.browser.domain.usecase.LoadAddressSuggestionsSourceUseCase
import com.raumanian.thirtysix.browser.testdoubles.FakeBookmarkRepository
import com.raumanian.thirtysix.browser.testdoubles.FakeHistoryRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Spec 022 US2 — the suggestion ViewModel: one load per session, FR-021, writes nothing. */
@OptIn(ExperimentalCoroutinesApi::class)
class AddressSuggestionsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val bookmarks = CountingBookmarks()
    private val history = FakeHistoryRepository()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.newViewModel(): AddressSuggestionsViewModel {
        val vm = AddressSuggestionsViewModel(
            loadSource = LoadAddressSuggestionsSourceUseCase(bookmarks, history),
            filter = FilterAddressSuggestionsUseCase(),
            dispatchers = TestDispatchers(dispatcher),
        )
        backgroundScope.launch { vm.uiState.collect { } }
        return vm
    }

    private fun seed() = runTest {
        bookmarks.seedBookmark(Bookmark(0L, "Kotlin docs", "https://kotlinlang.org/docs", null, 1L, 1L))
        history.recordVisit("https://news.example/kotlin", "News", visitedAt = 5L)
        history.recordVisit("https://example.com/", "Example", visitedAt = 6L)
    }

    @Test
    fun `typing shows matches, bookmarks first`() = runTest {
        seed()
        val vm = newViewModel()
        vm.onEditingStarted()
        vm.onTextChanged("kot", currentPageUrl = "https://example.com/")
        advanceUntilIdle()
        val shown = vm.uiState.value.suggestions
        assertEquals(listOf("https://kotlinlang.org/docs", "https://news.example/kotlin"), shown.map { it.url })
        assertEquals(SuggestionSource.Bookmark, shown.first().source)
    }

    @Test
    fun `the snapshot is loaded once per editing session`() = runTest {
        seed()
        val vm = newViewModel()
        vm.onEditingStarted()
        vm.onTextChanged("k", null)
        vm.onTextChanged("ko", null)
        vm.onEditingStarted()
        advanceUntilIdle()
        assertEquals(1, bookmarks.reads)

        vm.onEditingEnded()
        vm.onEditingStarted()
        advanceUntilIdle()
        assertEquals(2, bookmarks.reads)
    }

    @Test
    fun `the page's own address unchanged, or empty text, suggests nothing`() = runTest {
        seed()
        val vm = newViewModel()
        vm.onEditingStarted()
        vm.onTextChanged("https://example.com/", currentPageUrl = "https://example.com/")
        advanceUntilIdle()
        assertTrue(vm.uiState.value.suggestions.isEmpty())
        vm.onTextChanged("", currentPageUrl = "https://example.com/")
        advanceUntilIdle()
        assertTrue(vm.uiState.value.suggestions.isEmpty())
    }

    @Test
    fun `ending the session clears the suggestions`() = runTest {
        seed()
        val vm = newViewModel()
        vm.onEditingStarted()
        vm.onTextChanged("kot", null)
        advanceUntilIdle()
        assertEquals(2, vm.uiState.value.suggestions.size)
        vm.onEditingEnded()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.suggestions.isEmpty())
    }

    @Test
    fun `only the latest text counts`() = runTest {
        seed()
        val vm = newViewModel()
        vm.onEditingStarted()
        vm.onTextChanged("kot", null)
        vm.onTextChanged("example.com", null)
        advanceUntilIdle()
        assertEquals(listOf("https://example.com/"), vm.uiState.value.suggestions.map { it.url })
    }

    @Test
    fun `typing writes nothing to bookmarks or history`() = runTest {
        seed()
        val historyBefore = history.recorded
        val vm = newViewModel()
        vm.onEditingStarted()
        "kotlin docs".indices.forEach { vm.onTextChanged("kotlin docs".take(it + 1), null) }
        vm.onEditingEnded()
        advanceUntilIdle()
        assertEquals(historyBefore, history.recorded)
        assertEquals(1, bookmarks.getAllBookmarks().size)
    }

    /** Counts snapshot reads so "loaded once per session" can be checked. */
    private class CountingBookmarks : FakeBookmarkRepository() {
        var reads = 0
        override suspend fun getAllBookmarks(): List<Bookmark> {
            reads++
            return super.getAllBookmarks()
        }
    }

    private class TestDispatchers(dispatcher: CoroutineDispatcher) : DispatcherProvider {
        override val main = dispatcher
        override val io = dispatcher
        override val default = dispatcher
        override val unconfined = dispatcher
    }
}
