package com.raumanian.thirtysix.browser.domain.usecase

import com.raumanian.thirtysix.browser.core.result.Result
import com.raumanian.thirtysix.browser.domain.model.Bookmark
import com.raumanian.thirtysix.browser.testdoubles.FakeBookmarkRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ToggleBookmarkUseCaseTest {

    @Test
    fun `first tap on a non-bookmarked URL adds it at root`() = runTest {
        val repo = FakeBookmarkRepository()
        val toggle = ToggleBookmarkUseCase(repo, AddBookmarkUseCase(repo))

        val result = toggle(url = "https://example.com", fallbackTitle = "Example")
        assertTrue(result is Result.Success)
        assertEquals(ToggleBookmarkResult.Added, (result as Result.Success).data)
        assertEquals(1, repo.countBookmarksByUrl("https://example.com"))
    }

    @Test
    fun `second tap removes the most recently created bookmark for that URL`() = runTest {
        val repo = FakeBookmarkRepository()
        val toggle = ToggleBookmarkUseCase(repo, AddBookmarkUseCase(repo))

        // Seed three duplicates with distinct timestamps.
        repo.seedBookmark(
            Bookmark(0L, "old", "https://example.com", null, 100L, 100L),
        )
        repo.seedBookmark(
            Bookmark(0L, "mid", "https://example.com", null, 200L, 200L),
        )
        repo.seedBookmark(
            Bookmark(0L, "new", "https://example.com", null, 300L, 300L),
        )
        assertEquals(3, repo.countBookmarksByUrl("https://example.com"))

        val result = toggle(url = "https://example.com", fallbackTitle = "doesn't matter")
        assertEquals(Result.Success(ToggleBookmarkResult.Removed), result)
        assertEquals(2, repo.countBookmarksByUrl("https://example.com"))

        // Confirm "newest" (created at 300L) is the one deleted — verify Q4 semantic.
        // Repeatedly toggling will continue to remove most-recent.
        toggle(url = "https://example.com", fallbackTitle = "x")
        toggle(url = "https://example.com", fallbackTitle = "x")
        assertEquals(0, repo.countBookmarksByUrl("https://example.com"))
    }

    @Test
    fun `toggle preserves manual duplicates when one is added afterwards`() = runTest {
        val repo = FakeBookmarkRepository()
        val toggle = ToggleBookmarkUseCase(repo, AddBookmarkUseCase(repo))

        toggle(url = "https://example.com", fallbackTitle = "Star-add")
        // Manual add for the same URL — separate row.
        AddBookmarkUseCase(repo)("https://example.com", "Manual", null)
        assertEquals(2, repo.countBookmarksByUrl("https://example.com"))

        // Star toggle off should remove only the manual one (newest), star-created stays.
        toggle(url = "https://example.com", fallbackTitle = "x")
        assertEquals(1, repo.countBookmarksByUrl("https://example.com"))
    }
}
