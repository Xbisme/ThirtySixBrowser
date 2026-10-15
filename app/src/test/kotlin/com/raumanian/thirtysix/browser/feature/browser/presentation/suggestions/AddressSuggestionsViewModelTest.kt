package com.raumanian.thirtysix.browser.feature.browser.presentation.suggestions

import com.raumanian.thirtysix.browser.core.domain.model.Bookmark
import com.raumanian.thirtysix.browser.core.domain.util.DispatcherProvider
import com.raumanian.thirtysix.browser.core.testing.MainDispatcherRule
import com.raumanian.thirtysix.browser.core.testing.fake.FakeBookmarkRepository
import com.raumanian.thirtysix.browser.core.testing.fake.FakeHistoryRepository
import com.raumanian.thirtysix.browser.feature.browser.domain.model.SuggestionSource
import com.raumanian.thirtysix.browser.feature.browser.domain.usecase.FilterAddressSuggestionsUseCase
import com.raumanian.thirtysix.browser.feature.browser.domain.usecase.LoadAddressSuggestionsSourceUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** One load per session, nothing suggested for the unchanged page address, nothing written. */
@OptIn(ExperimentalCoroutinesApi::class)
class AddressSuggestionsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val bookmarks = CountingBookmarks()
    private val history = FakeHistoryRepository()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(dispatcher)

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
        vm.onAction(AddressSuggestionsAction.FocusChanged(true))
        vm.onAction(AddressSuggestionsAction.TextChanged("kot", currentPageUrl = "https://example.com/"))
        advanceUntilIdle()
        val shown = vm.uiState.value.suggestions
        assertEquals(listOf("https://kotlinlang.org/docs", "https://news.example/kotlin"), shown.map { it.url })
        assertEquals(SuggestionSource.Bookmark, shown.first().source)
    }

    @Test
    fun `the snapshot is loaded once per editing session`() = runTest {
        seed()
        val vm = newViewModel()
        vm.onAction(AddressSuggestionsAction.FocusChanged(true))
        vm.onAction(AddressSuggestionsAction.TextChanged("k", null))
        vm.onAction(AddressSuggestionsAction.TextChanged("ko", null))
        vm.onAction(AddressSuggestionsAction.FocusChanged(true))
        advanceUntilIdle()
        assertEquals(1, bookmarks.reads)

        vm.onAction(AddressSuggestionsAction.FocusChanged(false))
        vm.onAction(AddressSuggestionsAction.FocusChanged(true))
        advanceUntilIdle()
        assertEquals(2, bookmarks.reads)
    }

    @Test
    fun `the page's own address unchanged, or empty text, suggests nothing`() = runTest {
        seed()
        val vm = newViewModel()
        vm.onAction(AddressSuggestionsAction.FocusChanged(true))
        vm.onAction(
            AddressSuggestionsAction.TextChanged("https://example.com/", currentPageUrl = "https://example.com/"),
        )
        advanceUntilIdle()
        assertTrue(vm.uiState.value.suggestions.isEmpty())
        vm.onAction(AddressSuggestionsAction.TextChanged("", currentPageUrl = "https://example.com/"))
        advanceUntilIdle()
        assertTrue(vm.uiState.value.suggestions.isEmpty())
    }

    @Test
    fun `ending the session clears the suggestions`() = runTest {
        seed()
        val vm = newViewModel()
        vm.onAction(AddressSuggestionsAction.FocusChanged(true))
        vm.onAction(AddressSuggestionsAction.TextChanged("kot", null))
        advanceUntilIdle()
        assertEquals(2, vm.uiState.value.suggestions.size)
        vm.onAction(AddressSuggestionsAction.FocusChanged(false))
        advanceUntilIdle()
        assertTrue(vm.uiState.value.suggestions.isEmpty())
    }

    @Test
    fun `only the latest text counts`() = runTest {
        seed()
        val vm = newViewModel()
        vm.onAction(AddressSuggestionsAction.FocusChanged(true))
        vm.onAction(AddressSuggestionsAction.TextChanged("kot", null))
        vm.onAction(AddressSuggestionsAction.TextChanged("example.com", null))
        advanceUntilIdle()
        assertEquals(listOf("https://example.com/"), vm.uiState.value.suggestions.map { it.url })
    }

    @Test
    fun `typing writes nothing to bookmarks or history`() = runTest {
        seed()
        val historyBefore = history.recorded
        val vm = newViewModel()
        vm.onAction(AddressSuggestionsAction.FocusChanged(true))
        "kotlin docs".indices.forEach { end ->
            vm.onAction(AddressSuggestionsAction.TextChanged("kotlin docs".take(end + 1), null))
        }
        vm.onAction(AddressSuggestionsAction.FocusChanged(false))
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
