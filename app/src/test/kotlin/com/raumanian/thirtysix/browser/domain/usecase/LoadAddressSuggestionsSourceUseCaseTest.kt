package com.raumanian.thirtysix.browser.domain.usecase

import com.raumanian.thirtysix.browser.core.domain.model.Bookmark
import com.raumanian.thirtysix.browser.core.testing.fake.FakeBookmarkRepository
import com.raumanian.thirtysix.browser.core.testing.fake.FakeHistoryRepository
import com.raumanian.thirtysix.browser.domain.model.SuggestionSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** Spec 022 FR-016 — bookmarks first, then history; one entry per address (I-10, I-11). */
class LoadAddressSuggestionsSourceUseCaseTest {

    private companion object {
        val BOOKMARK = SuggestionSource.Bookmark
        val HISTORY = SuggestionSource.History
    }

    private val bookmarks = FakeBookmarkRepository()
    private val history = FakeHistoryRepository()
    private val load = LoadAddressSuggestionsSourceUseCase(bookmarks, history)

    private fun bookmark(url: String, title: String, createdAt: Long) {
        bookmarks.seedBookmark(Bookmark(0L, title, url, null, createdAt, createdAt))
    }

    @Test
    fun `bookmarks come first newest saved first, then history newest visit first`() = runTest {
        bookmark("https://b-old.example", "B old", createdAt = 1L)
        bookmark("https://b-new.example", "B new", createdAt = 2L)
        history.recordVisit("https://h-old.example", "H old", visitedAt = 10L)
        history.recordVisit("https://h-new.example", "H new", visitedAt = 20L)

        val candidates = load().candidates

        assertEquals(
            listOf("https://b-new.example", "https://b-old.example", "https://h-new.example", "https://h-old.example"),
            candidates.map { it.url },
        )
        assertEquals(
            listOf(BOOKMARK, BOOKMARK, HISTORY, HISTORY),
            candidates.map { it.source },
        )
    }

    @Test
    fun `a bookmarked address is not repeated from history`() = runTest {
        bookmark("https://both.example", "Saved", createdAt = 1L)
        history.recordVisit("https://both.example", "Visited", visitedAt = 99L)

        val candidates = load().candidates

        assertEquals(1, candidates.size)
        assertEquals(SuggestionSource.Bookmark, candidates.single().source)
        assertEquals("Saved", candidates.single().title)
    }

    @Test
    fun `repeat visits and duplicate bookmarks give one entry per address`() = runTest {
        bookmark("https://dup.example", "Older copy", createdAt = 1L)
        bookmark("https://dup.example", "Newer copy", createdAt = 2L)
        history.recordVisit("https://again.example", "First", visitedAt = 10L)
        history.recordVisit("https://again.example", "Second", visitedAt = 20L)

        val candidates = load().candidates

        assertEquals(listOf("https://dup.example", "https://again.example"), candidates.map { it.url })
        assertEquals("Newer copy", candidates.first().title)
        assertEquals("Second", candidates.last().title)
    }
}
