package com.raumanian.thirtysix.browser.domain.usecase

import app.cash.turbine.test
import com.raumanian.thirtysix.browser.testdoubles.FakeBookmarkRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class IsUrlBookmarkedUseCaseTest {

    @Test
    fun `emits false when no bookmark exists for url, then true after add`() = runTest {
        val repo = FakeBookmarkRepository()
        val isBookmarked = IsUrlBookmarkedUseCase(repo)
        val add = AddBookmarkUseCase(repo)

        isBookmarked("https://example.com").test {
            assertEquals(false, awaitItem())
            add(url = "https://example.com", title = "X", parentFolderId = null)
            assertEquals(true, awaitItem())
            cancelAndConsumeRemainingEvents()
        }
    }
}
